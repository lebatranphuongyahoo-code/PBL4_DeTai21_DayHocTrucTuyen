const { test } = require('node:test');
const assert = require('node:assert/strict');
const ts = require('typescript');
const fs = require('node:fs');
const Module = require('node:module');
const filename = require('node:path').join(__dirname, '../src/chat.ts');
const compiled = new Module(filename, module);
compiled._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText, filename);
const { mergeChat } = compiled.exports;
test('history arriving after a live message keeps that message and removes overlap', () => {
  const message = id => ({id, username:'member', text:'Message '+id, sentAt:1000+id});
  assert.deepEqual(mergeChat([message(3),message(4)], [message(1),message(2),message(3)]).map(m=>m.id), [1,2,3,4]);
  assert.deepEqual(mergeChat([message(1)], [message(1)]).map(m=>m.id), [1]);
});
