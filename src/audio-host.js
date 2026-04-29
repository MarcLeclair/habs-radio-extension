if (window.__HRS_AUDIO_HOST_LOADED__) {
  throw new Error("audio-host already loaded");
}
window.__HRS_AUDIO_HOST_LOADED__ = true;
const api = (typeof browser !== "undefined") ? browser : chrome;

let audio = null;
let ctx = null;
let workletNode = null;
let gainNode = null;
let outputGainNode = null;
let keepAliveOsc = null;
let currentStation = null;
let delaySeconds = 0;
let playing = false;

async function ensureGraph() {
  if (ctx) return;
  audio = new Audio();
  audio.crossOrigin = "anonymous";
  audio.preload = "none";

  ctx = new (window.AudioContext || window.webkitAudioContext)();
  await ctx.audioWorklet.addModule(api.runtime.getURL("src/delay-processor.js"));

  const source = ctx.createMediaElementSource(audio);
  gainNode = ctx.createGain();
  gainNode.gain.value = 1.0;
  outputGainNode = ctx.createGain();
  outputGainNode.gain.value = 0;

  workletNode = new AudioWorkletNode(ctx, "delay-processor", {
    numberOfInputs: 1,
    numberOfOutputs: 1,
    outputChannelCount: [2]
  });
  attachWorkletHeartbeats();
  workletNode.port.postMessage({ delaySeconds });
  source.connect(workletNode);
  workletNode.connect(gainNode);
  gainNode.connect(outputGainNode);
  outputGainNode.connect(ctx.destination);

  if (api.offscreen && api.offscreen.createDocument) {
    keepAliveOsc = ctx.createOscillator();
    keepAliveOsc.frequency.value = 440;
    const sink = ctx.createMediaStreamDestination();
    keepAliveOsc.connect(sink);
    keepAliveOsc.start();
  }
}

function resolveStation(stationId) {
  if (typeof stationId !== "string") return null;
  const list = self.HRS_STATIONS || [];
  return list.find((s) => s.id === stationId) || null;
}

async function play(stationId) {
  const station = resolveStation(stationId);
  if (!station) throw new Error("unknown station");

  await ensureGraph();
  if (ctx.state === "suspended") await ctx.resume();

  const audioStillHealthy = audio &&
    !audio.paused &&
    audio.src &&
    !audio.error &&
    audio.readyState >= 2 &&
    currentStation && currentStation.id === station.id;

  if (!audioStillHealthy) {
    currentStation = station;
    audio.pause();
    audio.removeAttribute("src");
    audio.load();
    audio.src = station.url;
    audio.addEventListener("playing", () => {
      try {
        const s = audio.seekable;
        if (s && s.length > 0) {
          const liveEdge = s.end(s.length - 1);
          if (liveEdge - audio.currentTime > 1) audio.currentTime = liveEdge;
        }
      } catch (e) {}
    }, { once: true });
    await audio.play();
  }

  if (outputGainNode && ctx) {
    outputGainNode.gain.setTargetAtTime(1, ctx.currentTime, 0.01);
  }
  playing = true;
}

function pause() {
  if (outputGainNode && ctx) {
    outputGainNode.gain.setTargetAtTime(0, ctx.currentTime, 0.01);
  }
  playing = false;
}

async function reload() {
  if (!currentStation || !audio) return;
  const wasPlaying = playing;
  audio.pause();
  const url = currentStation.url;
  audio.removeAttribute("src");
  audio.load();
  await new Promise((r) => setTimeout(r, 50));
  if (workletNode) workletNode.port.postMessage({ type: "reset" });
  audio.src = url;
  await audio.play();
  if (outputGainNode && ctx) {
    outputGainNode.gain.setTargetAtTime(wasPlaying ? 1 : 0, ctx.currentTime, 0.01);
  }
  playing = wasPlaying;
}

function setDelay(seconds) {
  delaySeconds = Math.max(0, Math.min(60, seconds));
  if (workletNode) workletNode.port.postMessage({ delaySeconds });
}

let port = null;

function sendPort(msg) {
  if (port) {
    try { port.postMessage(msg); } catch (e) {}
  }
}

function broadcastHeartbeat(payload) {
  sendPort({ type: "hrs:audio-heartbeat", ...payload });
}

function attachWorkletHeartbeats() {
  if (!workletNode) return;
  workletNode.port.onmessage = (e) => {
    if (!e.data) return;
    if (e.data.type === "audioStarted") return;
    if (e.data.type === "heartbeat") {
      broadcastHeartbeat({
        bufferedSeconds: e.data.bufferedSeconds,
        delaySeconds: e.data.delaySeconds,
        requestedDelaySeconds: e.data.requestedDelaySeconds
      });
    } else if (e.data.type === "resetDone") {
      broadcastHeartbeat({
        bufferedSeconds: 0,
        delaySeconds: 0,
        requestedDelaySeconds: delaySeconds
      });
    }
  };
}

function connectPort() {
  port = api.runtime.connect({ name: "hrs-audio" });
  port.onDisconnect.addListener(() => {
    port = null;
    setTimeout(connectPort, 500);
  });
  port.onMessage.addListener(async (msg) => {
    if (!msg || !msg.type) return;
    const id = msg.id;
    const reply = (payload) => sendPort({ type: "hrs:audio-reply", id, ...payload });
    try {
      switch (msg.type) {
        case "hrs:audio-play":
          await play(msg.stationId);
          reply({ ok: true });
          break;
        case "hrs:audio-pause":
          pause();
          reply({ ok: true });
          break;
        case "hrs:audio-reload":
          await reload();
          reply({ ok: true });
          break;
        case "hrs:audio-setDelay":
          setDelay(msg.seconds);
          reply({ ok: true });
          break;
        case "hrs:audio-queryState":
          reply({ playing, station: currentStation });
          break;
        default:
          reply({ ok: false, reason: "unknown-command" });
      }
    } catch (e) {
      reply({ ok: false, reason: String(e && e.message || e) });
    }
  });
}

connectPort();
