if (typeof importScripts === "function" && typeof self.hrsIsValidHost === "undefined") {
  try { importScripts("host-utils.js"); } catch (e) {}
}

const api = (typeof browser !== "undefined") ? browser : chrome;
const STORAGE_KEY = "hrs-user-domains";
const SCRIPT_FILES = ["src/stations.js", "src/content.js"];
const CSS_FILES = ["src/panel.css"];

const isValidHost = self.hrsIsValidHost;
const originPatterns = self.hrsOriginPatterns;

let iframeHostTabId = null;
let pendingIframeTabId = null;

function forbidden(sendResponse) {
  sendResponse({ ok: false, reason: "forbidden" });
  return false;
}

function asyncResponse(sendResponse, fn) {
  fn()
    .then(sendResponse)
    .catch((e) => sendResponse({ ok: false, reason: String(e && e.message || e) }));
  return true;
}

async function getDomains() {
  const res = await api.storage.local.get([STORAGE_KEY]);
  return ((res && res[STORAGE_KEY]) || []).filter(isValidHost);
}

async function setDomains(list) {
  return api.storage.local.set({ [STORAGE_KEY]: list.filter(isValidHost) });
}

function hasHostPermission(host) {
  return api.permissions.contains({ origins: originPatterns(host) });
}

function removeHostPermission(host) {
  return api.permissions.remove({ origins: originPatterns(host) });
}

const BUILTIN_HOSTS = ["sportsnet.ca", "rds.ca", "tvasports.ca"];
function isBuiltinHost(host) {
  return BUILTIN_HOSTS.some((b) => host === b || host.endsWith("." + b));
}

function isPopupSender(sender) {
  return sender && sender.url === api.runtime.getURL("src/popup.html");
}

async function isAllowedContentSender(sender) {
  if (!sender || !sender.tab || typeof sender.tab.id !== "number" || !sender.url) {
    return false;
  }
  try {
    const url = new URL(sender.url);
    if (url.protocol !== "https:" || !isValidHost(url.hostname)) return false;
    if (isBuiltinHost(url.hostname)) return true;
    const domains = await getDomains();
    return domains.includes(url.hostname);
  } catch (e) {
    return false;
  }
}

async function registerForDomain(host) {
  if (!isValidHost(host)) return false;
  if (isBuiltinHost(host)) return false;
  const id = `hrs-user-${host}`;
  try {
    try {
      await api.scripting.unregisterContentScripts({ ids: [id] });
    } catch {}
    await api.scripting.registerContentScripts([
      {
        id,
        matches: originPatterns(host),
        js: SCRIPT_FILES,
        css: CSS_FILES,
        runAt: "document_idle",
        persistAcrossSessions: true
      }
    ]);
    return true;
  } catch (e) {
    return false;
  }
}

async function unregisterForDomain(host) {
  if (!isValidHost(host)) return;
  const id = `hrs-user-${host}`;
  try {
    await api.scripting.unregisterContentScripts({ ids: [id] });
  } catch (e) {}
}

async function reRegisterAll() {
  const domains = await getDomains();
  for (const host of domains) {
    if (await hasHostPermission(host)) await registerForDomain(host);
  }
}

async function ensureAudioHost(senderTabId) {
  if (api.offscreen && api.offscreen.createDocument) {
    try {
      const has = await api.offscreen.hasDocument().catch(() => false);
      if (!has) {
        try {
          await api.offscreen.createDocument({
            url: api.runtime.getURL("src/audio-host.html"),
            reasons: ["AUDIO_PLAYBACK"],
            justification: "Plays radio audio in an extension-owned context so the radio CDN does not see the user's browsing site as the request Origin."
          });
        } catch (e) {
          const msg = String(e && e.message || e);
          if (!/already.*offscreen|single offscreen/i.test(msg)) {
            throw e;
          }
        }
      }
      return { kind: "offscreen" };
    } catch (e) {
      throw new Error("offscreen unavailable: " + (e && e.message || e));
    }
  }

  if (iframeHostTabId !== null) {
    return { kind: "iframe", tabId: iframeHostTabId };
  }
  if (typeof senderTabId === "number") {
    try {
      pendingIframeTabId = senderTabId;
      await api.tabs.sendMessage(senderTabId, { type: "hrs:inject-audio-host" });
      iframeHostTabId = senderTabId;
      return { kind: "iframe", tabId: senderTabId };
    } catch (e) {
      if (pendingIframeTabId === senderTabId) pendingIframeTabId = null;
    }
  }
  throw new Error("no audio host available");
}

let audioPort = null;
let audioMessageId = 0;
const pendingAudioReplies = new Map();

let audioPortDeferred = null;
function newAudioPortDeferred() {
  let resolve;
  const promise = new Promise((r) => { resolve = r; });
  audioPortDeferred = { promise, resolve };
}
newAudioPortDeferred();
function waitForAudioPort(timeoutMs = 5000) {
  if (audioPort) return Promise.resolve();
  return Promise.race([
    audioPortDeferred.promise,
    new Promise((_, reject) => setTimeout(() => reject(new Error("audio host did not connect")), timeoutMs))
  ]);
}

api.runtime.onConnect.addListener((p) => {
  if (p.name !== "hrs-audio") return;

  const expectedUrl = api.runtime.getURL("src/audio-host.html");
  const senderUrl = p.sender && p.sender.url;
  if (senderUrl !== expectedUrl) {
    try { p.disconnect(); } catch (e) {}
    return;
  }
  if (p.sender && p.sender.tab && typeof p.sender.tab.id === "number") {
    const tabId = p.sender.tab.id;
    const matchesActiveTab = iframeHostTabId !== null && iframeHostTabId === tabId;
    const matchesPendingTab = iframeHostTabId === null && pendingIframeTabId === tabId;
    if (!matchesActiveTab && !matchesPendingTab) {
      try { p.disconnect(); } catch (e) {}
      return;
    }
  } else if (p.sender && p.sender.tab) {
    try { p.disconnect(); } catch (e) {}
    return;
  }

  if (audioPort && audioPort !== p) {
    try { audioPort.disconnect(); } catch (e) {}
  }
  audioPort = p;
  if (p.sender && p.sender.tab && typeof p.sender.tab.id === "number") {
    iframeHostTabId = p.sender.tab.id;
    pendingIframeTabId = null;
  }
  audioPortDeferred.resolve();
  p.onMessage.addListener((msg) => {
    if (audioPort !== p) return;
    if (!msg || !msg.type) return;
    if (msg.type === "hrs:audio-reply" && pendingAudioReplies.has(msg.id)) {
      const { resolve } = pendingAudioReplies.get(msg.id);
      pendingAudioReplies.delete(msg.id);
      const { type, id, ...payload } = msg;
      resolve(payload);
    } else if (msg.type === "hrs:audio-heartbeat") {
      const { type, ...rest } = msg;
      broadcastToContentScripts({ type: "hrs:audio-heartbeat", ...rest });
    }
  });
  p.onDisconnect.addListener(() => {
    if (audioPort !== p) return;
    audioPort = null;
    iframeHostTabId = null;
    newAudioPortDeferred();
    for (const { reject } of pendingAudioReplies.values()) reject(new Error("audio host disconnected"));
    pendingAudioReplies.clear();
  });
});

async function callAudioHost(message, senderTabId) {
  await ensureAudioHost(senderTabId);
  await waitForAudioPort();

  const id = ++audioMessageId;
  return new Promise((resolve, reject) => {
    pendingAudioReplies.set(id, { resolve, reject });
    try {
      audioPort.postMessage({ ...message, id });
    } catch (e) {
      pendingAudioReplies.delete(id);
      reject(e);
    }
    setTimeout(() => {
      if (pendingAudioReplies.has(id)) {
        pendingAudioReplies.delete(id);
        reject(new Error("audio host timeout"));
      }
    }, 10000);
  });
}

function broadcastToContentScripts(message) {
  api.tabs.query({}).then((tabs) => {
    for (const t of tabs || []) {
      api.tabs.sendMessage(t.id, message).catch(() => {});
    }
  }).catch(() => {});
}

async function teardownAudioHost() {
  if (audioPort) {
    try { audioPort.disconnect(); } catch (e) {}
    audioPort = null;
  }
  iframeHostTabId = null;
  pendingIframeTabId = null;
  for (const { reject } of pendingAudioReplies.values()) {
    reject(new Error("audio host torn down"));
  }
  pendingAudioReplies.clear();
  newAudioPortDeferred();
  if (api.offscreen && api.offscreen.closeDocument) {
    try {
      const has = await api.offscreen.hasDocument().catch(() => false);
      if (has) await api.offscreen.closeDocument();
    } catch (e) {}
  }
}

async function isPanelTabUrl(url) {
  if (!url) return false;
  try {
    const u = new URL(url);
    if (u.protocol !== "https:") return false;
    const host = u.hostname.toLowerCase();
    if (!isValidHost(host)) return false;
    if (isBuiltinHost(host)) return true;
    const domains = await getDomains();
    return domains.includes(host);
  } catch {
    return false;
  }
}

async function panelTabsRemain(excludeTabId) {
  return new Promise((resolve) => {
    api.tabs.query({}).then(async (tabs) => {
      for (const t of tabs || []) {
        if (t.id === excludeTabId) continue;
        if (await isPanelTabUrl(t.url)) {
          resolve(true);
          return;
        }
      }
      resolve(false);
    }).catch(() => resolve(false));
  });
}

api.tabs.onRemoved.addListener(async (tabId) => {
  if (!(await panelTabsRemain(tabId))) {
    await teardownAudioHost();
  }
});

api.tabs.onUpdated.addListener(async (tabId, changeInfo) => {
  if (!changeInfo.url) return;
  if (!(await panelTabsRemain(-1))) {
    await teardownAudioHost();
  }
});

api.runtime.onInstalled.addListener(reRegisterAll);
api.runtime.onStartup.addListener(reRegisterAll);

const AUDIO_COMMAND_TYPES = {
  "hrs:play": "hrs:audio-play",
  "hrs:pause": "hrs:audio-pause",
  "hrs:reload": "hrs:audio-reload",
  "hrs:setDelay": "hrs:audio-setDelay"
};

const handlers = {
  "hrs:listDomains": {
    auth: "popup",
    handler: async () => ({ domains: await getDomains() })
  },
  "hrs:registerDomain": {
    auth: "popup",
    handler: async (msg) => {
      if (!isValidHost(msg.host)) return { ok: false, reason: "invalid-host" };
      if (!(await hasHostPermission(msg.host))) return { ok: false, reason: "no-permission" };
      const ok = await registerForDomain(msg.host);
      if (ok) {
        const list = await getDomains();
        if (!list.includes(msg.host)) list.push(msg.host);
        await setDomains(list);
      }
      return { ok };
    }
  },
  "hrs:removeDomain": {
    auth: "popup",
    handler: async (msg) => {
      if (!isValidHost(msg.host)) return { ok: false, reason: "invalid-host" };
      await unregisterForDomain(msg.host);
      try { await removeHostPermission(msg.host); } catch {}
      const list = (await getDomains()).filter((h) => h !== msg.host);
      await setDomains(list);
      return { ok: true };
    }
  },
  "hrs:queryState": {
    auth: "content",
    handler: async (msg, sender) => {
      if (!audioPort) return { playing: false };
      try {
        return await callAudioHost({ type: "hrs:audio-queryState" }, sender.tab.id);
      } catch (e) {
        return { playing: false };
      }
    }
  }
};
for (const fromType of Object.keys(AUDIO_COMMAND_TYPES)) {
  handlers[fromType] = {
    auth: "content",
    handler: (msg, sender) =>
      callAudioHost({ ...msg, type: AUDIO_COMMAND_TYPES[fromType] }, sender.tab.id)
  };
}

api.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (!msg || !msg.type) return false;
  const entry = handlers[msg.type];
  if (!entry) return false;

  if (entry.auth === "popup") {
    if (!isPopupSender(sender)) return forbidden(sendResponse);
    return asyncResponse(sendResponse, () => entry.handler(msg, sender));
  }
  return asyncResponse(sendResponse, async () => {
    if (!(await isAllowedContentSender(sender))) return { ok: false, reason: "forbidden" };
    return entry.handler(msg, sender);
  });
});
