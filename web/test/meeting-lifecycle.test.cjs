const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');
global.document = { fullscreenEnabled: false };

for (const extension of ['.ts', '.tsx']) {
  require.extensions[extension] = (module, filename) => {
    const { outputText } = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
      compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true },
    });
    module._compile(outputText, filename);
  };
}
const { ClassroomMedia } = require('../src/ClassroomMedia.tsx');
const props = { roomId: 1, roomName: 'Class', ownerName: 'Owner', token: 'test',
  user: { username: 'owner', displayName: 'Owner', role: 'STUDENT' },
  onMeetingChange() {}, onCheckMeeting() {}, onMeetingEnded() {}, onManage() {}, onExit() {} };

test('owner can end an open meeting even before media connects', () => {
  const html = renderToStaticMarkup(React.createElement(ClassroomMedia, { ...props, owned: true, meetingOpen: true }));
  const button = html.match(/<button[^>]*>Kết thúc buổi học<\/button>/)?.[0];
  assert.ok(button, 'End meeting must be visible without opening the people panel');
  assert.doesNotMatch(button, /disabled/);
});

test('closed class offers start only to its owner and keeps media disabled', () => {
  for (const owned of [true, false]) {
    const html = renderToStaticMarkup(React.createElement(ClassroomMedia, { ...props, owned, meetingOpen: false }));
    assert.equal(html.includes('Bắt đầu buổi học'), owned);
    assert.equal(html.includes('Kiểm tra buổi học'), !owned);
    assert.doesNotMatch(html, />Kết thúc buổi học<\/button>/);
    assert.match(html, /<button[^>]*disabled=""[^>]*title="(?:Bật mic|Mic đang bị khóa)"/);
  }
});
