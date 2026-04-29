const MAX_DELAY_SECONDS = 60;
const CROSSFADE_SAMPLES = 480;

class DelayProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    const size = Math.ceil(MAX_DELAY_SECONDS * sampleRate);
    this.buffers = [new Float32Array(size), new Float32Array(size)];
    this.size = size;
    this.writePos = 0;
    this.samplesWritten = 0;
    this.requestedDelaySamples = 0;
    this.delaySamples = 0;
    this.realAudioSeen = false;
    this.crossfadeRemaining = 0;
    this.oldDelaySamples = 0;
    this.heartbeatCounter = 0;

    this.port.onmessage = (e) => {
      if (e.data && e.data.type === "reset") {
        this.buffers[0].fill(0);
        this.buffers[1].fill(0);
        this.writePos = 0;
        this.samplesWritten = 0;
        this.delaySamples = 0;
        this.crossfadeRemaining = 0;
        this.heartbeatCounter = 0;
        this.realAudioSeen = false;
        this.port.postMessage({ type: "resetDone" });
        return;
      }
      if (e.data && typeof e.data.delaySeconds === "number") {
        const d = Math.max(0, Math.min(MAX_DELAY_SECONDS, e.data.delaySeconds));
        this.requestedDelaySamples = Math.floor(d * sampleRate);
        const safeMax = Math.max(0, this.samplesWritten - 1);
        const newDelay = Math.min(this.requestedDelaySamples, safeMax);
        if (newDelay !== this.delaySamples) {
          if (this.crossfadeRemaining === 0) {
            this.oldDelaySamples = this.delaySamples;
            this.crossfadeRemaining = CROSSFADE_SAMPLES;
          }
          this.delaySamples = newDelay;
        }
      }
    };
  }

  _readAt(channel, delaySamples) {
    let readPos = this.writePos - delaySamples;
    while (readPos < 0) readPos += this.size;
    while (readPos >= this.size) readPos -= this.size;
    return this.buffers[channel][readPos];
  }

  process(inputs, outputs) {
    const input = inputs[0];
    const output = outputs[0];
    if (!output || output.length === 0) return true;

    const frames = output[0].length;
    const numChannels = Math.min(output.length, this.buffers.length);
    const haveInput = input && input.length > 0 && input[0] && input[0].length > 0;

    if (!haveInput) {
      for (let c = 0; c < numChannels; c++) {
        for (let i = 0; i < frames; i++) output[c][i] = 0;
      }
      return true;
    }

    if (!this.realAudioSeen) {
      const inCh = input[0];
      let peak = 0;
      for (let i = 0; i < inCh.length; i++) {
        const a = Math.abs(inCh[i]);
        if (a > peak) peak = a;
      }
      if (peak < 1e-5) {
        for (let c = 0; c < numChannels; c++) {
          for (let i = 0; i < frames; i++) output[c][i] = 0;
        }
        return true;
      }
      this.realAudioSeen = true;
      this.port.postMessage({ type: "audioStarted" });
    }

    for (let i = 0; i < frames; i++) {
      for (let c = 0; c < this.buffers.length; c++) {
        const inCh = input[c] || input[0];
        this.buffers[c][this.writePos] = inCh[i];
      }

      if (this.delaySamples < this.requestedDelaySamples) {
        const safeMax = Math.max(0, this.samplesWritten - 1);
        if (this.delaySamples < safeMax) {
          this.delaySamples = Math.min(this.delaySamples + 1, this.requestedDelaySamples, safeMax);
        }
      }

      for (let c = 0; c < numChannels; c++) {
        if (this.crossfadeRemaining > 0) {
          const t = 1 - (this.crossfadeRemaining / CROSSFADE_SAMPLES);
          const oldSample = this._readAt(c, this.oldDelaySamples);
          const newSample = this._readAt(c, this.delaySamples);
          output[c][i] = oldSample * (1 - t) + newSample * t;
        } else {
          output[c][i] = this._readAt(c, this.delaySamples);
        }
      }
      if (this.crossfadeRemaining > 0) this.crossfadeRemaining--;

      this.writePos = (this.writePos + 1) % this.size;
      if (this.samplesWritten < this.size) this.samplesWritten++;
    }

    this.heartbeatCounter++;
    if (this.heartbeatCounter >= Math.floor(sampleRate / 128)) {
      this.heartbeatCounter = 0;
      this.port.postMessage({
        type: "heartbeat",
        bufferedSeconds: this.samplesWritten / sampleRate,
        delaySeconds: this.delaySamples / sampleRate,
        requestedDelaySeconds: this.requestedDelaySamples / sampleRate
      });
    }

    return true;
  }
}

registerProcessor("delay-processor", DelayProcessor);
