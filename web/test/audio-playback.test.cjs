const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

test('a burst of packets does not schedule overlapping audio from one sender', () => {
  const text = fs.readFileSync(path.join(__dirname, '../src/ClassroomMedia.tsx'), 'utf8');
  const start = text.indexOf('  function playPacket(');
  const body = text.slice(start, text.indexOf('  useEffect(', start))
    .replace('data: ArrayBuffer', 'data');
  const starts = [];
  const context = {
    state: 'running', currentTime: 1, destination: {},
    createBuffer: () => ({ getChannelData: () => new Float32Array(320) }),
    createBufferSource: () => ({ connect() {}, disconnect() {}, start(t) { starts.push(t); } })
  };
  const sandbox = { DataView, AUDIO: 2, receivedAudio: { current: 0 },
    playback: { current: { context, times: new Map(), sequences: new Map() } } };
  vm.runInNewContext(body + '\nthis.playPacket = playPacket;', sandbox);
  for (let i = 0; i < 20; i++) {
    const packet = new ArrayBuffer(653), view = new DataView(packet);
    view.setUint8(0, 2); view.setBigUint64(1, 1n); view.setUint32(9, i);
    sandbox.playPacket(packet);
  }
  for (let i = 1; i < starts.length; i++)
    assert.ok(starts[i] >= starts[i - 1] + 0.01999, 'scheduled audio overlaps');
  assert.ok(starts.length < 20, 'excess buffered audio is dropped');
});
