const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const Module=require('node:module');
const ts=require('typescript');
const React=require('react');

function harness({owned=true,open=true,role='STUDENT'}={}){
  const slots=[],effects=[];let cursor=0,tree,delayedDetail;
  const detail={id:1,name:'QA class',owner:'Owner',owned,members:[],meetingOpen:open,meetingId:open?10:null};
  const initial=[{id:1,name:'QA class',owned,members:1},{id:2,name:'Other class',owned,members:1}];
  const calls=[],Media=()=>null,History=()=>null;
  const hooks={...React,
    useState(initial){const i=cursor++;if(!(i in slots))slots[i]=typeof initial==='function'?initial():initial;return[slots[i],value=>{slots[i]=typeof value==='function'?value(slots[i]):value;}];},
    useRef(initial){const i=cursor++;return slots[i]??(slots[i]={current:initial});},useEffect(effect){effects.push(effect);},
  };
  const filename=path.join(__dirname,'../src/Classes.tsx'),compiled=new Module(filename,module);compiled.paths=module.paths;
  compiled.require=name=>name==='react'?hooks:name==='./ClassroomMedia'?{ClassroomMedia:Media}:name==='./MeetingHistory'?{MeetingHistoryPanel:History}:name==='./api'?{api:async(url)=>{
    if(delayedDetail&&/^\/rooms\/\d+$/.test(url)){const pending=delayedDetail;delayedDetail=null;return pending.promise;}
    calls.push(url);if(url.endsWith('/meeting/start')){detail.meetingOpen=true;detail.meetingId=(detail.meetingId??10)+1;}
    if(url.endsWith('/meeting/end'))detail.meetingOpen=false;
    const id=Number(url.match(/^\/rooms\/(\d+)$/)?.[1]);
    return{room:structuredClone(id===2?{...detail,id:2,name:'Other class'}:detail),rooms:initial,requests:[],joinStatuses:[]};
  }}:name.startsWith('./')?new Proxy({},{get:()=>()=>null}):require(name);
  compiled._compile(ts.transpileModule(fs.readFileSync(filename,'utf8'),{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true}}).outputText,filename);
  function render(){cursor=0;tree=compiled.exports.Classes({user:{username:'qa',displayName:'QA',role},token:'qa',initial,onLogout(){},logoutBusy:false,logoutError:''});return tree;}
  function nodes(node,parents=[]){if(Array.isArray(node))return node.flatMap(x=>nodes(x,parents));if(!node||typeof node!=='object')return[];return[{node,parents},...nodes(node.props?.children,[...parents,node])];}
  function text(node){if(Array.isArray(node))return node.map(text).join('');if(node==null||typeof node==='boolean')return'';return typeof node==='object'?text(node.props?.children):String(node);}
  const component=type=>nodes(tree).find(x=>x.node.type===type);
  async function settle(){await new Promise(setImmediate);await new Promise(setImmediate);render();}
  async function click(label,index=0){const node=nodes(tree).filter(x=>x.node.type==='button'&&(x.node.props['aria-label']||text(x.node))===label)[index]?.node;assert.ok(node,'Button '+label+' exists');node.props.onClick();await settle();}
  async function join(){const panel=component(History);assert.ok(panel,'Class overview exists');panel.node.props.onJoin();await settle();}
  function delayNextDetail(){const snapshot=structuredClone(detail);let resolve;const promise=new Promise(done=>{resolve=()=>done({room:snapshot});});delayedDetail={promise};return resolve;}
  render();return{render,settle,click,join,detail,calls,delayNextDetail,startPolling:()=>effects[effects.length-1](),media:()=>component(Media),history:()=>component(History),text:()=>text(tree)};
}

test('opening a class shows its meetings without connecting media, and leaving keeps the class selected',async()=>{
  const h=harness();await h.click('Mở lớp');assert.ok(h.history());assert.equal(h.media(),undefined);
  await h.join();assert.ok(h.media());h.media().node.props.onExit();await h.settle();assert.equal(h.media(),undefined);assert.ok(h.history());assert.match(h.text(),/QA class/);
});
test('class list and class overview navigation keep the joined meeting mounted',async()=>{
  const h=harness();await h.click('Mở lớp');await h.join();await h.click('Danh sách lớp');assert.ok(h.media());assert.ok(h.media().parents.some(x=>x.props.hidden));
  await h.click('Trang lớp');assert.ok(h.media());assert.ok(h.media().parents.some(x=>x.props.hidden));await h.click('Phòng học');assert.ok(h.media());assert.ok(h.media().parents.every(x=>!x.props.hidden));
});
test('ending or receiving meeting-ended returns to the class feed',async()=>{
  for(const remote of [false,true]){const h=harness();await h.click('Mở lớp');await h.join();const props=h.media().node.props;if(remote)props.onMeetingEnded();else props.onMeetingChange(false);await h.settle();assert.equal(h.media(),undefined);assert.ok(h.history());assert.equal(h.history().node.props.meetingOpen,false);}
});
test('starting a meeting joins its new identity; stale or closed meetings do not auto-connect',async()=>{
  const h=harness({open:false});await h.click('Mở lớp');await h.click('Bắt đầu buổi học');assert.ok(h.media());assert.equal(h.media().node.props.meetingId,h.detail.meetingId);
  h.detail.meetingId++;h.media().node.props.onCheckMeeting();await h.settle();assert.equal(h.media(),undefined,'polling a different meeting must not reconnect the old view');
  h.detail.meetingOpen=false;await h.join();assert.equal(h.media(),undefined);
});
test('browsing another class is allowed before joining, and blocked while connected',async()=>{
  const h=harness();await h.click('Mở lớp');await h.click('Danh sách lớp');await h.click('Mở lớp',1);assert.equal(h.history().node.props.roomId,2);
  await h.join();await h.click('Danh sách lớp');await h.click('Mở lớp',0);assert.equal(h.media().node.props.roomId,2);assert.match(h.text(),/Rời buổi học hiện tại/);
});
test('after leaving a meeting, a teacher can still create and join other classes',async()=>{
  const h=harness({role:'TEACHER'});await h.click('Mở lớp');await h.join();h.media().node.props.onExit();await h.settle();await h.click('Danh sách lớp');
  assert.match(h.text(),/Tạo lớp mới/);assert.match(h.text(),/Nhập mã lớp/);assert.match(h.text(),/Yêu cầu của bạn/);
});
test('a student can join classes but sees no class-creation controls',async()=>{
  const h=harness({role:'STUDENT'});
  assert.doesNotMatch(h.text(),/Tạo lớp/);
  assert.match(h.text(),/Nhập mã lớp/);
  await h.click('Mở lớp');
  assert.doesNotMatch(h.text(),/Tạo lớp/);
});
test('a closed-room poll delayed across Start cannot replace the newly joined meeting',async()=>{
  const previous={document:global.document,window:global.window};global.document={hidden:false};global.window={setInterval(){return 1;},clearInterval(){}};
  let cleanup;
  try{
    const h=harness({open:false});await h.click('Mở lớp');const resolve=h.delayNextDetail();cleanup=h.startPolling();await h.click('Bắt đầu buổi học');assert.ok(h.media());const id=h.media().node.props.meetingId;
    resolve();await h.settle();assert.ok(h.media(),'the stale closed snapshot must not unmount live media');assert.equal(h.media().node.props.meetingId,id);
  }finally{cleanup?.();Object.assign(global,previous);}
});
