const api = (typeof browser !== "undefined") ? browser : chrome;
const t = (key, ...subs) => api.i18n.getMessage(key, subs.length ? subs : undefined);

const BUILTIN_HOSTS = ["sportsnet.ca", "rds.ca", "tvasports.ca"];

const isValidHost = self.hrsIsValidHost;
const originPatterns = self.hrsOriginPatterns;

const send = (msg) => api.runtime.sendMessage(msg);
const queryActiveTab = () => api.tabs.query({ active: true, currentWindow: true });
const requestOrigins = (origins) => api.permissions.request({ origins });

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
    hostEl.textContent = t("popupNotAWebpage");
    configureAction(actionEl, t("popupUnavailable"), true, false);
    hintEl.textContent = t("popupHintNotAWebpage");
  } else {
    hostEl.textContent = current.host;

    if (isBuiltin(current.host)) {
      configureAction(actionEl, t("popupActionAlwaysEnabled"), true, true);
      hintEl.textContent = t("popupHintBuiltin");
    } else if (domains.includes(current.host)) {
      configureAction(actionEl, t("popupActionRemove"), false, true, async () => {
        actionEl.disabled = true;
        await send({ type: "hrs:removeDomain", host: current.host });
        await render();
      });
      hintEl.textContent = t("popupHintEnabled");
    } else {
      const addCurrentSite = async () => {
        let granted = false;
        try {
          granted = await requestOrigins(originPatterns(current.host));
        } catch (e) {
          hintEl.textContent = t("popupPermissionFailed", String(e && e.message || e));
          return;
        }
        if (!granted) {
          hintEl.textContent = t("popupPermissionDenied");
          return;
        }
        actionEl.disabled = true;
        actionEl.textContent = t("popupRegistering");
        const res = await send({ type: "hrs:registerDomain", host: current.host });
        if (res && res.ok) {
          api.tabs.reload(current.tabId);
          window.close();
        } else {
          configureAction(actionEl, t("popupActionAdd", current.host), false, false, addCurrentSite);
          hintEl.textContent = t("popupRegisterFailed");
        }
      };
      configureAction(actionEl, t("popupActionAdd", current.host), false, false, addCurrentSite);
      hintEl.textContent = t("popupHintAdd");
    }
  }

  listEl.innerHTML = "";

  for (const host of BUILTIN_HOSTS) {
    const tag = document.createElement("span");
    tag.className = "empty";
    tag.textContent = t("popupBuiltinTag");
    appendListItem(listEl, host, tag);
  }

  for (const host of domains) {
    if (!isValidHost(host)) continue;
    const btn = document.createElement("button");
    btn.className = "remove";
    btn.textContent = t("popupRemoveButton");
    btn.onclick = async () => {
      btn.disabled = true;
      await send({ type: "hrs:removeDomain", host });
      await render();
    };
    appendListItem(listEl, host, btn);
  }

  emptyEl.hidden = domains.length > 0;
}

function applyStaticI18n() {
  document.querySelectorAll("[data-i18n]").forEach((el) => {
    el.textContent = t(el.getAttribute("data-i18n"));
  });
}

document.addEventListener("DOMContentLoaded", () => {
  applyStaticI18n();
  render();
});
