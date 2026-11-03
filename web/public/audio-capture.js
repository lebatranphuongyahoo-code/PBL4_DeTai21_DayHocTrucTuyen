class ClassroomCapture extends AudioWorkletProcessor {
  constructor() { super(); this.samples = []; this.position = 0; }
  process(inputs) {
    const input = inputs[0]?.[0];
    if (!input) return true;
    for (const sample of input) this.samples.push(sample);
    const ratio = sampleRate / 16000;
    while (this.samples.length >= 320 * ratio + 1) {
      const out = new Float32Array(320);
      for (let i = 0; i < 320; i++) {
        const at = this.position + i * ratio;
        const start = Math.floor(at), fraction = at - start;
        out[i] = this.samples[start] * (1 - fraction) + this.samples[start + 1] * fraction;
      }
      const consumed = this.position + 320 * ratio;
      this.samples.splice(0, Math.floor(consumed));
      this.position = consumed - Math.floor(consumed);
      this.port.postMessage(out, [out.buffer]);
    }
    return true;
  }
}
registerProcessor("classroom-capture", ClassroomCapture);
