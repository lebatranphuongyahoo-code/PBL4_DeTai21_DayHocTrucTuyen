const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const Module=require('node:module');
const ts=require('typescript');
const React=require('react');

function harness(options={}){
  const slots=[],effects=[],calls=[];let cursor=0,tree;
  const rooms=options.rooms??[{id:1,name:'Algebra',members:2,ownerId:10,ownerUsername:'teacher',ownerDisplayName:'Teacher',status:'ACTIVE'},{id:2,name:'Archived calculus',members:3,ownerId:12,ownerUsername:'oldteacher',ownerDisplayName:'Old Teacher',status:'ARCHIVED'}];
  const users=options.users??[{id:10,username:'teacher',displayName:'Teacher',role:'TEACHER'},{id:11,username:'student',displayName:'Student',role:'STUDENT'},{id:12,username:'admin',displayName:'Admin',role:'ADMIN'}];
  const detail={...rooms[0],meetingOpen:false,members:[{id:10,username:'teacher',displayName:'Teacher',role:'OWNER'},{id:11,username:'student',displayName:'Student',role:'MEMBER'}]};
  const hooks={...React,
    useState(initial){const i=cursor++;if(!(i in slots))slots[i]=typeof initial==='function'?initial():initial;return[slots[i],value=>{slots[i]=typeof value==='function'?value(slots[i]):value;}];},
    useEffect(effect){effects.push(effect);},
  };
  const filename=path.join(__dirname,'../src/AdminDashboard.tsx'),compiled=new Module(filename,module);compiled.paths=module.paths;
  compiled.require=name=>name==='react'?hooks:name==='./api'?{api:async(url,method='GET',body)=>{calls.push({url,method,body});if(url==='/admin/rooms'&&method==='GET'){if(options.roomsError)throw new Error('Không kết nối được.');return{rooms};}if(url==='/admin/users'&&method==='GET'){if(options.usersError)throw new Error('Không tải được người dùng.');return{users};}if(url==='/admin/users/11/teacher'&&method==='POST'){users.find(account=>account.id===11).role='TEACHER';return{ok:true};}return url==='/admin/rooms/1'?{room:structuredClone(detail)}:{ok:true};}}:name.startsWith('./')?new Proxy({},{get:()=>()=>null}):require(name);
  compiled._compile(ts.transpileModule(fs.readFileSync(filename,'utf8'),{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true}}).outputText,filename);
  function render(){cursor=0;tree=compiled.exports.AdminDashboard({user:{username:'admin',displayName:'Admin',role:'ADMIN'},token:'admin-token',onLogout(){},logoutBusy:false,logoutError:''});return tree;}
  function nodes(node){if(Array.isArray(node))return node.flatMap(nodes);if(!node||typeof node!=='object')return[];return[node,...nodes(node.props?.children)];}
  function text(node){if(Array.isArray(node))return node.map(text).join('');if(node==null||typeof node==='boolean')return'';return typeof node==='object'?text(node.props?.children):String(node);}
  async function settle(){await new Promise(setImmediate);await new Promise(setImmediate);render();}
  render();return{render,nodes,text,calls,effects,settle,tree:()=>tree};
}

test('admin dashboard loads all room states and uses admin-only archive/restore routes',async()=>{
  const previous=global.confirm;global.confirm=()=>true;
  try{
    const h=harness();h.effects[0]();await h.settle();
    assert.match(h.text(h.tree()),/Algebra/);
    assert.match(h.text(h.tree()),/Archived calculus/);
    const status=h.nodes(h.tree()).find(node=>node.props.ariaLabel==='Lọc trạng thái lớp');
    assert.ok(status,'admin can filter room state');
    status.props.onChange('ACTIVE');h.render();
    assert.match(h.text(h.tree()),/Algebra/);
    assert.doesNotMatch(h.text(h.tree()),/Archived calculus/);
    status.props.onChange('ALL');h.render();
    let button=h.nodes(h.tree()).find(node=>node.type==='button'&&h.text(node.props.children)==='Thành viên');
    button.props.onClick();await h.settle();
    assert.ok(h.calls.some(call=>call.url==='/admin/rooms/1'&&call.method==='GET'));
    button=h.nodes(h.tree()).find(node=>node.type==='button'&&h.text(node.props.children)==='Lưu trữ');
    button.props.onClick();await h.settle();
    assert.ok(h.calls.some(call=>call.url==='/admin/rooms/1/archive'&&call.method==='POST'));
    button=h.nodes(h.tree()).find(node=>node.type==='button'&&h.text(node.props.children)==='Khôi phục');
    assert.ok(button,'archived room must expose restore');
    button.props.onClick();await h.settle();
    assert.ok(h.calls.some(call=>call.url==='/admin/rooms/2/restore'&&call.method==='POST'));
    assert.ok(h.calls.every(call=>!call.url.startsWith('/rooms/')),'admin operations must not use teacher/member room APIs');
  }finally{global.confirm=previous;}
});

test('admin can create a class and assign an active teacher owner',async()=>{
  const h=harness();h.effects[0]();await h.settle();
  h.nodes(h.tree()).find(node=>node.type==='button'&&h.text(node.props.children)==='Tạo lớp').props.onClick();h.render();
  const name=h.nodes(h.tree()).find(node=>node.type==='input'&&node.props.id==='admin-room-name');
  name.props.onChange({target:{value:'Lớp vật lý'}});h.render();
  const teacher=h.nodes(h.tree()).find(node=>node.props.id==='admin-room-teacher');
  teacher.props.onChange('teacher');h.render();
  const form=h.nodes(h.tree()).find(node=>node.type==='form'&&node.props.onSubmit?.toString().includes('preventDefault'));
  await form.props.onSubmit({preventDefault(){}});await h.settle();
  assert.ok(h.calls.some(call=>call.url==='/admin/rooms'&&call.method==='POST'&&call.body.name==='Lớp vật lý'&&call.body.teacherUsername==='teacher'));
});

test('admin can list accounts and promote a student to teacher',async()=>{
  const h=harness();h.effects[0]();await h.settle();
  assert.match(h.text(h.tree()),/Student/);
  const promote=h.nodes(h.tree()).find(node=>node.type==='button'&&h.text(node.props.children)==='Cấp quyền giảng viên');
  assert.ok(promote);promote.props.onClick();await h.settle();
  assert.ok(h.calls.some(call=>call.url==='/admin/users/11/teacher'&&call.method==='POST'));
  assert.match(h.text(h.tree()),/Giảng viên/);
});

test('room loading and network errors do not render a false zero-room empty state',async()=>{
  const loading=harness({rooms:[]});loading.effects[0]();
  assert.match(loading.text(loading.render()),/Đang tải danh sách lớp/);
  assert.doesNotMatch(loading.text(loading.tree()),/0 lớp|Chưa có lớp nào/);
  await loading.settle();
  assert.match(loading.text(loading.tree()),/Chưa có lớp nào/);
  const failed=harness({roomsError:true});failed.effects[0]();await failed.settle();
  assert.match(failed.text(failed.tree()),/Không kết nối được/);
  assert.doesNotMatch(failed.text(failed.tree()),/0 lớp|Chưa có lớp nào/);
});
