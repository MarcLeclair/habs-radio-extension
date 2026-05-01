const api = (typeof browser !== "undefined") ? browser : chrome;
const t = (key, ...subs) => api.i18n.getMessage(key, subs.length ? subs : undefined);

let extensionInvalidated = false;
const invalidatedListeners = new Set();
function isContextInvalidatedError(err) {
  const msg = err && (err.message || String(err));
  return typeof msg === "string" && /context invalidated|invalidated context/i.test(msg);
}
function send(msg) {
  return api.runtime.sendMessage(msg).catch((err) => {
    if (isContextInvalidatedError(err) && !extensionInvalidated) {
      extensionInvalidated = true;
      invalidatedListeners.forEach((fn) => { try { fn(); } catch {} });
    }
    return undefined;
  });
}

const mutedByUs = new WeakSet();

function muteVideo(v) {
  try {
    if (!v.muted) {
      v.muted = true;
      mutedByUs.add(v);
    }
  } catch {}
}

function muteSiteVideos() {
  document.querySelectorAll("video").forEach(muteVideo);
}

function unmuteOurVideos() {
  document.querySelectorAll("video").forEach((v) => {
    if (mutedByUs.has(v)) {
      try { v.muted = false; } catch {}
      mutedByUs.delete(v);
    }
  });
}

let videoMuteObserver = null;
function startMutingNewVideos() {
  if (videoMuteObserver) return;
  videoMuteObserver = new MutationObserver((mutations) => {
    for (const m of mutations) {
      for (const node of m.addedNodes) {
        if (node.nodeType !== Node.ELEMENT_NODE) continue;
        if (node.tagName === "VIDEO") {
          muteVideo(node);
        } else if (node.querySelectorAll) {
          node.querySelectorAll("video").forEach(muteVideo);
        }
      }
    }
  });
  videoMuteObserver.observe(document.documentElement, { childList: true, subtree: true });
}
function stopMutingNewVideos() {
  if (!videoMuteObserver) return;
  videoMuteObserver.disconnect();
  videoMuteObserver = null;
}

function $(root, selector) {
  return root.querySelector(selector);
}

function buildPanel() {
  const panel = document.createElement("div");
  panel.id = "hrs-panel";
  panel.innerHTML = `
    <div class="hrs-header">
      <span class="hrs-title"></span>
      <button class="hrs-collapse">–</button>
    </div>
    <div class="hrs-body">
      <div class="hrs-stations"></div>
      <div class="hrs-controls">
        <button class="hrs-play"></button>
        <button class="hrs-reload">↻</button>
      </div>
      <div class="hrs-sync">
        <div class="hrs-sync-label">
          <span class="hrs-sync-text"></span>
          <span class="hrs-sync-value">0s</span>
        </div>
        <input type="range" min="0" max="60" step="1" value="0" />
        <div class="hrs-buffer">
          <div class="hrs-buffer-bar">
            <div class="hrs-buffer-fill"></div>
            <div class="hrs-buffer-played"></div>
          </div>
          <div class="hrs-buffer-label"></div>
        </div>
        <div class="hrs-nudge">
          <button data-delta="-10">−10s</button>
          <button data-delta="-5">−5s</button>
          <button data-delta="-1">−1s</button>
          <button data-delta="1">+1s</button>
          <button data-delta="5">+5s</button>
          <button data-delta="10">+10s</button>
        </div>
      </div>
      <div class="hrs-status"></div>
      <div class="hrs-mute-tip"></div>
    </div>
  `;
  panel.querySelector(".hrs-title").textContent = t("panelTitle");
  panel.querySelector(".hrs-collapse").title = t("panelCollapseTitle");
  panel.querySelector(".hrs-play").textContent = t("panelPlay");
  panel.querySelector(".hrs-reload").title = t("panelReloadTitle");
  panel.querySelector(".hrs-sync-text").textContent = t("panelSyncDelay");
  panel.querySelector(".hrs-buffer-label").textContent = t("panelBufferLabel", "0");
  const isFirefox = navigator.userAgent.includes("Firefox");
  const muteTipEl = panel.querySelector(".hrs-mute-tip");
  if (isFirefox) {
    muteTipEl.textContent = t("panelMuteTip");
  } else {
    muteTipEl.textContent = t("panelMuteTipChrome");
  }
  return panel;
}

function renderStations(container, stations, activeId, onSelect) {
  container.innerHTML = "";
  for (const s of stations) {
    const el = document.createElement("div");
    el.className = "hrs-station" + (s.id === activeId ? " active" : "");
    el.dataset.id = s.id;
    const wrap = document.createElement("div");
    const nameEl = document.createElement("div");
    nameEl.className = "hrs-station-name";
    nameEl.textContent = s.name;
    const tagEl = document.createElement("div");
    tagEl.className = "hrs-station-tag";
    tagEl.textContent = s.tagline;
    wrap.appendChild(nameEl);
    wrap.appendChild(tagEl);
    el.appendChild(wrap);
    el.addEventListener("click", () => onSelect(s));
    container.appendChild(el);
  }
}

function makeDraggable(panel, handle) {
  let dragging = false;
  let offX = 0, offY = 0;
  handle.addEventListener("mousedown", (e) => {
    if (e.target.classList.contains("hrs-collapse")) return;
    dragging = true;
    const r = panel.getBoundingClientRect();
    offX = e.clientX - r.left;
    offY = e.clientY - r.top;
    e.preventDefault();
  });
  document.addEventListener("mousemove", (e) => {
    if (!dragging) return;
    panel.style.left = (e.clientX - offX) + "px";
    panel.style.top = (e.clientY - offY) + "px";
    panel.style.right = "auto";
    panel.style.bottom = "auto";
  });
  document.addEventListener("mouseup", () => { dragging = false; });
}

async function init() {
  if (document.getElementById("hrs-panel")) return;
  if (window.__HRS_INITIALIZED__) return;
  window.__HRS_INITIALIZED__ = true;

  const stations = window.HRS_STATIONS;
  const initialDelay = window.HRS_DEFAULT_DELAY || 0;

  const panel = buildPanel();
  document.body.appendChild(panel);

  const stationsEl = $(panel, ".hrs-stations");
  const playBtn = $(panel, ".hrs-play");
  const reloadBtn = $(panel, ".hrs-reload");
  const slider = $(panel, "input[type=range]");
  const valueEl = $(panel, ".hrs-sync-value");
  const statusEl = $(panel, ".hrs-status");
  const collapseBtn = $(panel, ".hrs-collapse");
  const header = $(panel, ".hrs-header");
  const bufferFill = $(panel, ".hrs-buffer-fill");
  const bufferPlayed = $(panel, ".hrs-buffer-played");
  const bufferLabel = $(panel, ".hrs-buffer-label");

  makeDraggable(panel, header);

  invalidatedListeners.add(() => {
    statusEl.textContent = t("statusInvalidated");
    playBtn.disabled = true;
    reloadBtn.disabled = true;
    slider.disabled = true;
    panel.querySelectorAll(".hrs-nudge button").forEach((b) => { b.disabled = true; });
  });

  let activeStation = stations[0];
  let playing = false;

  function setPlaying(nextPlaying, status) {
    playing = nextPlaying;
    playBtn.textContent = playing ? t("panelPause") : t("panelPlay");
    playBtn.disabled = false;
    statusEl.textContent = status || (playing ? t("statusPlaying", activeStation.name) : t("statusPaused"));
  }

  const applyHeartbeat = (data) => {
    const buffered = data.bufferedSeconds || 0;
    const applied = data.delaySeconds || 0;
    const requested = data.requestedDelaySeconds || 0;

    bufferFill.style.width = Math.min(100, (buffered / 60) * 100) + "%";
    bufferPlayed.style.width = Math.min(100, (applied / 60) * 100) + "%";

    let label = t("panelBufferLabel", String(Math.round(buffered)));
    if (requested > buffered + 0.5) {
      label += t("panelBufferFilling", String(Math.round(requested)));
    }
    bufferLabel.textContent = label;
  };

  let audioIframe = null;
  function ensureAudioIframe(forceRecreate) {
    if (audioIframe && audioIframe.isConnected && !forceRecreate) return;
    if (audioIframe) {
      try { audioIframe.remove(); } catch (e) {}
    }
    const iframe = document.createElement("iframe");
    iframe.src = api.runtime.getURL("src/audio-host.html");
    iframe.setAttribute("allow", "autoplay");
    iframe.style.cssText = "position:absolute;width:0;height:0;border:0;left:-9999px;top:-9999px;visibility:hidden;";
    iframe.setAttribute("aria-hidden", "true");
    document.documentElement.appendChild(iframe);
    audioIframe = iframe;
  }

  api.runtime.onMessage.addListener((msg) => {
    if (!msg) return;
    if (msg.type === "hrs:audio-heartbeat") applyHeartbeat(msg);
    else if (msg.type === "hrs:inject-audio-host") ensureAudioIframe(true);
  });

  slider.value = String(initialDelay);
  valueEl.textContent = `${Math.round(initialDelay)}s`;

  const onSelect = (s) => {
    activeStation = s;
    renderStations(stationsEl, stations, activeStation.id, onSelect);
    if (playing) send({ type: "hrs:play", stationId: s.id }).catch(() => {});
  };
  renderStations(stationsEl, stations, activeStation.id, onSelect);

  playBtn.addEventListener("click", async () => {
    if (playing) {
      await send({ type: "hrs:pause" });
      stopMutingNewVideos();
      unmuteOurVideos();
      setPlaying(false);
      return;
    }
    statusEl.textContent = t("statusConnecting");
    playBtn.disabled = true;
    try {
      muteSiteVideos();
      startMutingNewVideos();
      const res = await send({ type: "hrs:play", stationId: activeStation.id });
      if (!res || !res.ok) throw new Error(res && res.reason || "play failed");
      setPlaying(true);
    } catch (e) {
      stopMutingNewVideos();
      unmuteOurVideos();
      statusEl.textContent = t("statusFailedToPlay");
      playBtn.disabled = false;
    }
  });

  reloadBtn.addEventListener("click", async () => {
    reloadBtn.disabled = true;
    statusEl.textContent = t("statusReloading");
    try {
      const res = await send({ type: "hrs:reload" });
      if (!res || !res.ok) throw new Error("reload failed");
      statusEl.textContent = playing ? t("statusPlaying", activeStation.name) : t("statusPaused");
    } catch (e) {
      statusEl.textContent = t("statusReloadFailed");
    } finally {
      reloadBtn.disabled = false;
    }
  });

  slider.addEventListener("input", () => {
    const v = parseFloat(slider.value);
    valueEl.textContent = `${Math.round(v)}s`;
    send({ type: "hrs:setDelay", seconds: v }).catch(() => {});
  });

  panel.querySelectorAll(".hrs-nudge button").forEach((btn) => {
    btn.addEventListener("click", () => {
      const delta = parseFloat(btn.dataset.delta);
      const next = Math.max(0, Math.min(60, parseFloat(slider.value) + delta));
      slider.value = String(next);
      slider.dispatchEvent(new Event("input"));
    });
  });

  collapseBtn.addEventListener("click", () => {
    panel.classList.toggle("collapsed");
    collapseBtn.textContent = panel.classList.contains("collapsed") ? "+" : "–";
  });

  send({ type: "hrs:setDelay", seconds: initialDelay }).catch(() => {});

  send({ type: "hrs:queryState" }).then((res) => {
    if (res && res.playing) {
      muteSiteVideos();
      startMutingNewVideos();
      setPlaying(true);
    }
  }).catch(() => {});
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", init);
} else {
  init();
}
