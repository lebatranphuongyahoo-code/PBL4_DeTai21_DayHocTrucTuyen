const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

test('class list navigation keeps the active meeting mounted', () => {
  const source = fs.readFileSync(path.join(__dirname, '../src/Classes.tsx'), 'utf8');

  assert.match(source, /showingClasses/);
  assert.match(source, /hidden=\{showingClasses\}/);
  assert.doesNotMatch(source, /Quay về danh sách lớp sẽ rời buổi học hiện tại/);
  assert.match(source, /onClick=\{showClassList\}/);
});
