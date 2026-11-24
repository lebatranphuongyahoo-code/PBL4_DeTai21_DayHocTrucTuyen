const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const ts = require('typescript');

const filename = path.join(__dirname, '../src/mediaPacing.ts');
const compiled = new Module(filename, module);
compiled._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText, filename);
const { adjustScreenRate } = compiled.exports;

test('screen pacing halves quickly under congestion and never falls below two fps', () => {
  assert.deepEqual(adjustScreenRate(8, 100_000, 0, 100), { fps: 4, clearSince: null });
  assert.deepEqual(adjustScreenRate(3, 100_000, 0, 100), { fps: 2, clearSince: null });
  assert.deepEqual(adjustScreenRate(2, 100_000, 0, 100), { fps: 2, clearSince: null });
});

test('screen pacing adds one fps after three seconds of a clear queue and caps at eight', () => {
  let state = adjustScreenRate(4, 10_000, null, 0);
  assert.deepEqual(state, { fps: 4, clearSince: 0 });
  state = adjustScreenRate(state.fps, 10_000, state.clearSince, 2_999);
  assert.deepEqual(state, { fps: 4, clearSince: 0 });
  state = adjustScreenRate(state.fps, 10_000, state.clearSince, 3_000);
  assert.deepEqual(state, { fps: 5, clearSince: 3_000 });
  assert.deepEqual(adjustScreenRate(8, 10_000, 3_000, 10_000), { fps: 8, clearSince: null });
});

test('a moderate queue resets the stable interval before recovery', () => {
  assert.deepEqual(adjustScreenRate(4, 40_000, 1_000, 2_000), { fps: 4, clearSince: null });
});
