const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const Module=require('node:module');
const ts=require('typescript');

test('reconnect starts a contiguous recent page after more than 50 offline messages',async()=>{
  const state=[],effects=[],sockets=[];let index=0,ended=0,checked=0;
  const hooks={
    useState(initial){const slot=index++;state[slot]=initial;return[initial,value=>{state[slot]=typeof value==='function'?value(state[slot]):value;}];},
    useRef(initial){return{current:initial};},
    useEffect(effect){effects.push(effect);},
  };
  class Socket{static OPEN=1;constructor(){sockets.push(this);}close(){}send(){}}
  const filename=path.join(__dirname,'../src/ClassroomMedia.tsx');
  const compiled=new Module(filename,module);compiled.paths=module.paths;
  compiled.require=name=>name==='react'?hooks:name==='./api'?{api:async()=>({ticket:'qa'})}:name==='./Icons'?{Icon:()=>null}:name==='./chat'?{mergeChat:(a,b)=>[...new Map([...a,...b].map(m=>[m.id,m])).values()].sort((a,b)=>a.id-b.id)}:name==='./mediaPacing'?{adjustScreenRate:(fps)=>({fps,clearSince:null})}:require(name);
  const previous={window:global.window,location:global.location,WebSocket:global.WebSocket,document:global.document};
  global.document={};
  global.window={setInterval(){return 1;},clearInterval(){},clearTimeout(){}};
  global.location={origin:'http://localhost'};global.WebSocket=Socket;
  try{
    compiled._compile(ts.transpileModule(fs.readFileSync(filename,'utf8'),{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true}}).outputText,filename);
    compiled.exports.ClassroomMedia({meetingOpen:true,meetingId:1,roomId:1,roomName:'QA',ownerName:'Owner',owned:false,token:'qa',user:{username:'member',displayName:'Member'},onMeetingEnded(){ended++;},onCheckMeeting(){checked++;}});
    const connect=effects[effects.length-1];let cleanup=connect();await new Promise(setImmediate);
    const history=(socket,start)=>socket.onmessage({data:JSON.stringify({type:'chat-history',meetingId:1,messages:Array.from({length:50},(_,i)=>({id:start+i,username:'member',text:'Message',sentAt:1,meetingId:1}))})});
    history(sockets[0],101);cleanup();cleanup=connect();await new Promise(setImmediate);history(sockets[1],201);
    const chat=state.find(value=>Array.isArray(value)&&value.length&&value[0].text==='Message');
    assert.equal(chat.length,50,'old cached page must not strand the missing offline range');
    assert.equal(chat[0].id,201,'older cursor must now start before 201 to fetch 151–200');
    sockets[1].onmessage({data:JSON.stringify({type:'chat',meetingId:2,id:999,username:'other',text:'Wrong meeting',sentAt:1})});
    sockets[1].onmessage({data:JSON.stringify({type:'chat-history',meetingId:2,messages:[{id:999,text:'Wrong meeting'}]})});
    assert.equal(state.find(value=>Array.isArray(value)&&value.length&&value[0].text==='Message').length,50,'another meeting cannot replace or append to this chat');
    sockets[1].onclose({code:4000});assert.equal(ended,1);assert.equal(checked,1,'a rejected old meeting refreshes the current meeting identity');
    cleanup();
  }finally{Object.assign(global,previous);}
});
