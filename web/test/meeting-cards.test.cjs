const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const ts=require('typescript');
const React=require('react');
const {renderToStaticMarkup}=require('react-dom/server');

for(const extension of ['.ts','.tsx'])require.extensions[extension]=(module,filename)=>module._compile(ts.transpileModule(fs.readFileSync(filename,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true}}).outputText,filename);
const {MeetingCard}=require('../src/MeetingHistory.tsx');
const meeting={id:7,startedAt:1_000_000,endedAt:6_400_000,startUncertain:false,endReason:'OWNER',connections:[]};
function render(overrides={},props={}){return renderToStaticMarkup(React.createElement(MeetingCard,{meeting:{...meeting,...overrides},roomId:1,token:'qa',owned:false,onJoin(){},joinDisabled:false,...props}));}

test('ended meeting is a card with times, duration and a chat action',()=>{
  const html=render();assert.match(html,/<article/);assert.match(html,/Đã kết thúc/);assert.match(html,/1 giờ 30 phút/);assert.equal((html.match(/<time /g)||[]).length,2);assert.match(html,/Xem tin nhắn/);assert.doesNotMatch(html,/Vào buổi học/);
});
test('only an open current meeting has a join action',()=>{
  assert.match(render({endedAt:null,endReason:null}),/Vào buổi học/);
  assert.doesNotMatch(render({endedAt:null,endReason:null},{onJoin:undefined}),/Vào buổi học/);
  assert.match(render({endedAt:null,endReason:null},{joinDisabled:true}),/<button[^>]*disabled=""[^>]*>Vào buổi học/);
});
test('uncertain start or restart never shows a precise duration',()=>{
  for(const overrides of [{startUncertain:true},{endReason:'SERVER_RESTART'}]){
    const html=render(overrides);assert.doesNotMatch(html,/1 giờ 30 phút/);assert.match(html,/Không đủ dữ liệu xác định thời lượng/);
  }
});
test('owner sees recent connection history without an attendance report',()=>{
  const html=render({}, {owned:true});
  assert.match(html,/Lượt kết nối gần nhất/);
  assert.doesNotMatch(html,/Thống kê tham gia|Thời gian tham gia theo tài khoản|Cập nhật thời gian tham gia/);
});
