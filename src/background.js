if (typeof importScripts === "function" && typeof self.hrsIsValidHost === "undefined") {
  try { importScripts("host-utils.js"); } catch (e) {}
}

const api = (typeof browser !== "undefined") ? browser : chrome;
const USE_PROMISE_APIS = typeof browser !== "undefined" && api === browser;
const STORAGE_KEY = "hrs-user-domains";
const SCRIPT_FILES = ["src/stations.js", "src/content.js"];
const CSS_FILES = ["src/panel.css"];

const isValidHost = self.hrsIsValidHost;
const originPatterns = self.hrsOriginPatterns;

let iframeHostTabId = null;
let pendingIframeTabId = null;

const storageGet = (keys) => USE_PROMISE_APIS
  ? api.storage.local.get(keys)
  : new Promise((resolve) => api.storage.local.get(keys, resolve));
const storageSet = (items) => USE_PROMISE_APIS
  ? api.storage.local.set(items)
  : new Promise((resolve) => api.storage.local.set(items, resolve));
const containsPermission = (permissions) => USE_PROMISE_APIS
  ? api.permissions.contains(permissions)
  : new Promise((resolve) => api.permissions.contains(permissions, resolve));
const removePermission = (permissions) => USE_PROMISE_APIS
  ? api.permissions.remove(permissions)
  : new Promise((resolve) => api.permissions.remove(permissions, resolve));
const queryTabs = (query) => USE_PROMISE_APIS
  ? api.tabs.query(query)
  : new Promise((resolve) => api.tabs.query(query, resolve));
const sendTabMessage = (tabId, message) => USE_PROMISE_APIS
  ? api.tabs.sendMessage(tabId, message)
  : new Promise((resolve) => api.tabs.sendMessage(tabId, message, resolve));

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
  const res = await storageGet([STORAGE_KEY]);
  return ((res && res[STORAGE_KEY]) || []).filter(isValidHost);
}

async function setDomains(list) {
  return storageSet({ [STORAGE_KEY]: list.filter(isValidHost) });
}

function hasHostPermission(host) {
  return containsPermission({ origins: originPatterns(host) });
}

function removeHostPermission(host) {
  return removePermission({ origins: originPatterns(host) });
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
        await api.offscreen.createDocument({
          url: api.runtime.getURL("src/audio-host.html"),
          reasons: ["AUDIO_PLAYBACK"],
          justification: "Plays radio audio in an extension-owned context so the radio CDN does not see the user's browsing site as the request Origin."
        });
      }
      return { kind: "offscreen" };
    } catch (e) {}
  }

  if (iframeHostTabId !== null) {
    return { kind: "iframe", tabId: iframeHostTabId };
  }
  if (typeof senderTabId === "number") {
    try {
      pendingIframeTabId = senderTabId;
      await sendTabMessage(senderTabId, { type: "hrs:inject-audio-host" });
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
    for (const { reject } of pendingAudioReplies.values()) reject(new Error("audio host disconnected"));
    pendingAudioReplies.clear();
  });
});

async function waitForAudioPort() {
  for (let i = 0; i < 50; i++) {
    if (audioPort) return;
    await new Promise((r) => setTimeout(r, 100));
  }
  throw new Error("audio host did not connect");
}

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
  queryTabs({}).then((tabs) => {
    for (const t of tabs || []) {
      sendTabMessage(t.id, message).catch(() => {});
    }
  }).catch(() => {});
}

api.runtime.onInstalled.addListener(reRegisterAll);
api.runtime.onStartup.addListener(reRegisterAll);

api.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (!msg || !msg.type) return false;

  if (msg.type === "hrs:listDomains") {
    if (!isPopupSender(sender)) return forbidden(sendResponse);
    return asyncResponse(sendResponse, async () => ({ domains: await getDomains() }));
  }

  if (msg.type === "hrs:registerDomain") {
    if (!isPopupSender(sender)) return forbidden(sendResponse);
    const host = msg.host;
    if (!isValidHost(host)) {
      sendResponse({ ok: false, reason: "invalid-host" });
      return false;
    }
    return asyncResponse(sendResponse, async () => {
      if (!(await hasHostPermission(host))) return { ok: false, reason: "no-permission" };
      const ok = await registerForDomain(host);
      if (ok) {
        const list = await getDomains();
        if (!list.includes(host)) list.push(host);
        await setDomains(list);
      }
      return { ok };
    });
  }

  if (msg.type === "hrs:removeDomain") {
    if (!isPopupSender(sender)) return forbidden(sendResponse);
    const host = msg.host;
    if (!isValidHost(host)) {
      sendResponse({ ok: false, reason: "invalid-host" });
      return false;
    }
    return asyncResponse(sendResponse, async () => {
      await unregisterForDomain(host);
      try { await removeHostPermission(host); } catch {}
      const list = (await getDomains()).filter((h) => h !== host);
      await setDomains(list);
      return { ok: true };
    });
  }

  if (msg.type === "hrs:queryState") {
    return asyncResponse(sendResponse, async () => {
      if (!(await isAllowedContentSender(sender))) {
        return { ok: false, reason: "forbidden" };
      }
      if (!audioPort) return { playing: false };
      try {
        return await callAudioHost({ type: "hrs:audio-queryState" }, sender && sender.tab && sender.tab.id);
      } catch (e) {
        return { playing: false };
      }
    });
  }

  if (msg.type === "hrs:play" || msg.type === "hrs:pause" ||
      msg.type === "hrs:reload" || msg.type === "hrs:setDelay") {
    return asyncResponse(sendResponse, async () => {
      if (!(await isAllowedContentSender(sender))) {
        return { ok: false, reason: "forbidden" };
      }
      const audioMsg = { ...msg, type: "hrs:audio-" + msg.type.slice(4) };
      return callAudioHost(audioMsg, sender && sender.tab && sender.tab.id);
    });
  }

  return false;
});
