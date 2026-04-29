const api = (typeof browser !== "undefined") ? browser : chrome;
const USE_PROMISE_APIS = typeof browser !== "undefined" && api === browser;

function send(msg) {
  if (USE_PROMISE_APIS) {
    return api.runtime.sendMessage(msg).catch(() => undefined);
  }
  return new Promise((resolve) => {
    try {
      api.runtime.sendMessage(msg, (res) => resolve(res));
    } catch (e) {
      resolve(undefined);
    }
  });
}

function muteSiteVideos() {
  document.querySelectorAll("video").forEach((v) => {
    try { v.muted = true; } catch {}
  });
}

function $(root, selector) {
  return root.querySelector(selector);
}

function buildPanel() {
  const panel = document.createElement("div");
  panel.id = "hrs-panel";
  panel.innerHTML = `
    <div class="hrs-header">
      <span class="hrs-title">🏒 Habs Radio Sync</span>
      <button class="hrs-collapse" title="Collapse">–</button>
    </div>
    <div class="hrs-body">
      <div class="hrs-stations"></div>
      <div class="hrs-controls">
        <button class="hrs-play">▶ Play</button>
        <button class="hrs-reload" title="Reload stream and flush buffer">↻</button>
      </div>
      <div class="hrs-sync">
        <div class="hrs-sync-label">
          <span>Sync delay</span>
          <span class="hrs-sync-value">0s</span>
        </div>
        <input type="range" min="0" max="60" step="1" value="0" />
        <div class="hrs-buffer">
          <div class="hrs-buffer-bar">
            <div class="hrs-buffer-fill"></div>
            <div class="hrs-buffer-played"></div>
          </div>
          <div class="hrs-buffer-label">Buffer: 0s / 60s</div>
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
      <div class="hrs-mute-tip">
        We auto-mute the video player when you press play. If you still
        hear it, mute the video manually using its own controls. Don't
        mute the whole tab — that mutes the radio too.
      </div>
    </div>
  `;
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

  let activeStation = stations[0];
  let playing = false;

  function setPlaying(nextPlaying, status) {
    playing = nextPlaying;
    playBtn.textContent = playing ? "⏸ Pause" : "▶ Play";
    playBtn.disabled = false;
    statusEl.textContent = status || (playing ? `Playing ${activeStation.name}` : "Paused");
  }

  const applyHeartbeat = (data) => {
    const buffered = data.bufferedSeconds || 0;
    const applied = data.delaySeconds || 0;
    const requested = data.requestedDelaySeconds || 0;

    bufferFill.style.width = Math.min(100, (buffered / 60) * 100) + "%";
    bufferPlayed.style.width = Math.min(100, (applied / 60) * 100) + "%";

    let label = `Buffer: ${Math.round(buffered)}s / 60s`;
    if (requested > buffered + 0.5) {
      label += ` — filling to ${Math.round(requested)}s…`;
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
      setPlaying(false);
      return;
    }
    statusEl.textContent = "Connecting…";
    playBtn.disabled = true;
    try {
      muteSiteVideos();
      const res = await send({ type: "hrs:play", stationId: activeStation.id });
      if (!res || !res.ok) throw new Error(res && res.reason || "play failed");
      setPlaying(true);
    } catch (e) {
      statusEl.textContent = "Failed to play stream";
      playBtn.disabled = false;
    }
  });

  reloadBtn.addEventListener("click", async () => {
    reloadBtn.disabled = true;
    statusEl.textContent = "Reloading stream…";
    try {
      const res = await send({ type: "hrs:reload" });
      if (!res || !res.ok) throw new Error("reload failed");
      statusEl.textContent = playing ? `Playing ${activeStation.name}` : "Paused";
    } catch (e) {
      statusEl.textContent = "Reload failed";
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
    if (res && res.station) {
      send({ type: "hrs:reload" }).catch(() => {});
      if (res.playing) {
        setPlaying(true);
      }
    }
  }).catch(() => {});
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", init);
} else {
  init();
}
