const api = (typeof browser !== "undefined") ? browser : chrome;
const USE_PROMISE_APIS = typeof browser !== "undefined" && api === browser;

const BUILTIN_HOSTS = ["sportsnet.ca", "rds.ca", "tvasports.ca"];

const isValidHost = self.hrsIsValidHost;
const originPatterns = self.hrsOriginPatterns;

const send = (msg) => USE_PROMISE_APIS
  ? api.runtime.sendMessage(msg)
  : new Promise((resolve) => api.runtime.sendMessage(msg, resolve));
const queryActiveTab = () => USE_PROMISE_APIS
  ? api.tabs.query({ active: true, currentWindow: true })
  : new Promise((resolve) => api.tabs.query({ active: true, currentWindow: true }, resolve));

function requestOrigins(origins) {
  if (USE_PROMISE_APIS) return api.permissions.request({ origins });
  return new Promise((resolve, reject) => {
    try {
      const p = api.permissions.request({ origins }, (granted) => {
        if (api.runtime.lastError) reject(api.runtime.lastError);
        else resolve(granted);
      });
      if (p && typeof p.then === "function") p.then(resolve, reject);
    } catch (e) {
      reject(e);
    }
  });
}

function getCurrentTabHost() {
  return queryActiveTab().then((tabs) => {
    const tab = tabs && tabs[0];
    if (!tab || !tab.url) return null;
    try {
      const u = new URL(tab.url);
      if (u.protocol !== "https:") return null;
      const host = u.hostname.toLowerCase();
      return isValidHost(host) ? { host, tabId: tab.id } : null;
    } catch {
      return null;
    }
  });
}

function isBuiltin(host) {
  return BUILTIN_HOSTS.some((b) => host === b || host.endsWith("." + b));
}

function appendListItem(listEl, host, rightContent) {
  const li = document.createElement("li");
  const nameSpan = document.createElement("span");
  nameSpan.className = "host-name";
  nameSpan.textContent = host;
  li.appendChild(nameSpan);
  li.appendChild(rightContent);
  listEl.appendChild(li);
}

function configureAction(button, text, disabled, secondary, onClick) {
  button.textContent = text;
  button.disabled = disabled;
  button.classList.toggle("secondary", secondary);
  button.onclick = onClick || null;
}

async function render() {
  const hostEl = document.getElementById("current-host");
  const actionEl = document.getElementById("current-action");
  const hintEl = document.getElementById("current-hint");
  const listEl = document.getElementById("domain-list");
  const emptyEl = document.getElementById("empty-msg");

  const current = await getCurrentTabHost();
  const { domains } = (await send({ type: "hrs:listDomains" })) || { domains: [] };

  if (!current) {
    hostEl.textContent = "(not a webpage)";
    configureAction(actionEl, "Unavailable", true, false);
    hintEl.textContent = "Open a sports streaming page, then click the extension icon to enable it there.";
  } else {
    hostEl.textContent = current.host;

    if (isBuiltin(current.host)) {
      configureAction(actionEl, "✓ Always enabled", true, true);
      hintEl.textContent = "This site is built in. The radio panel appears automatically.";
    } else if (domains.includes(current.host)) {
      configureAction(actionEl, "Remove this site", false, true, async () => {
        actionEl.disabled = true;
        await send({ type: "hrs:removeDomain", host: current.host });
        await render();
      });
      hintEl.textContent = "The radio panel is enabled here. Reload the page after removing.";
    } else {
      const addCurrentSite = async () => {
        let granted = false;
        try {
          granted = await requestOrigins(originPatterns(current.host));
        } catch (e) {
          hintEl.textContent = "Permission request failed: " + (e && e.message || e);
          return;
        }
        if (!granted) {
          hintEl.textContent = "Permission was not granted.";
          return;
        }
        actionEl.disabled = true;
        actionEl.textContent = "Registering…";
        const res = await send({ type: "hrs:registerDomain", host: current.host });
        if (res && res.ok) {
          api.tabs.reload(current.tabId);
          window.close();
        } else {
          configureAction(actionEl, `Add ${current.host}`, false, false, addCurrentSite);
          hintEl.textContent = "Failed to register content script.";
        }
      };
      configureAction(actionEl, `Add ${current.host}`, false, false, addCurrentSite);
      hintEl.textContent = "Adds permission for this exact hostname only. Reload the page after enabling.";
    }
  }

  listEl.innerHTML = "";

  for (const host of BUILTIN_HOSTS) {
    const tag = document.createElement("span");
    tag.className = "empty";
    tag.textContent = "built-in";
    appendListItem(listEl, host, tag);
  }

  for (const host of domains) {
    if (!isValidHost(host)) continue;
    const btn = document.createElement("button");
    btn.className = "remove";
    btn.textContent = "Remove";
    btn.onclick = async () => {
      btn.disabled = true;
      await send({ type: "hrs:removeDomain", host });
      await render();
    };
    appendListItem(listEl, host, btn);
  }

  emptyEl.hidden = domains.length > 0;
}

document.addEventListener("DOMContentLoaded", render);
