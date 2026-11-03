const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");

for (const rate of [16000, 44100, 48000]) {
  test(`capture resamples ${rate} Hz into finite 20ms frames without drift`, () => {
    let Processor; const packets = [];
    const context = { sampleRate: rate, Float32Array,
      AudioWorkletProcessor: class { constructor() { this.port = { postMessage: samples => packets.push(samples) }; } },
      registerProcessor: (_, cls) => { Processor = cls; } };
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, "../public/audio-capture.js"), "utf8"), context);
    const processor = new Processor(); let count = 0;
    for (let block = 0; block < Math.floor(rate * 3 / 128); block++) {
      const input = new Float32Array(128);
      for (let i = 0; i < 128; i++) input[i] = Math.sin(2 * Math.PI * 440 * count++ / rate);
      assert.equal(processor.process([[input]]), true);
    }
    assert.ok(Math.abs(packets.length - count / rate * 50) < 2);
    for (const frame of packets) {
      assert.equal(frame.length, 320);
      assert.ok(frame.every(value => Number.isFinite(value) && Math.abs(value) <= 1));
    }
    assert.ok(processor.samples.length < rate / 50 + 130);
  });
}
