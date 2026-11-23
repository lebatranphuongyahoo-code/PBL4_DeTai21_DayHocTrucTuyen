const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const Module=require('node:module');
const ts=require('typescript');
const React=require('react');

function harness({teacher=false,delayLoad=false}={}){
  const slots=[],effects=[],calls=[];let cursor=0,tree,nextId=1,releaseLoad;
  const items=[],submissions=[];
  async function api(url,method='GET',body){
    calls.push({url,method,body});
    if(url==='/rooms/1/activities'&&method==='GET'){
      if(delayLoad&&!releaseLoad)return new Promise(resolve=>{releaseLoad=()=>resolve({activities:structuredClone(items)});});
      return{activities:structuredClone(items)};
    }
    if(url==='/rooms/1/activities'&&method==='POST'){items.unshift({id:nextId++,title:body.title,prompt:body.prompt,createdAt:1,submissionCount:0,submission:null});return{ok:true};}
    const activityId=Number(url.match(/activities\/(\d+)/)?.[1]);
    if(url.endsWith('/submissions')&&method==='GET')return{submissions:structuredClone(submissions)};
    if(url.endsWith('/submissions')&&method==='POST'){
      const submission={accountId:2,username:'student',displayName:'Student',answer:body.answer,submittedAt:2,score:null,feedback:null,reviewedAt:null};
      items[0].submission=submission;submissions[0]=submission;return{submission};
    }
    return{ok:true};
  }
  const hooks={...React,
    useState(initial){const i=cursor++;if(!(i in slots))slots[i]=typeof initial==='function'?initial():initial;return[slots[i],value=>{slots[i]=typeof value==='function'?value(slots[i]):value;}];},
    useEffect(effect){effects.push(effect);},
  };
  const filename=path.join(__dirname,'../src/Activities.tsx'),compiled=new Module(filename,module);compiled.paths=module.paths;
  compiled.require=name=>name==='react'?hooks:name==='./api'?{api}:require(name);
  compiled._compile(ts.transpileModule(fs.readFileSync(filename,'utf8'),{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true}}).outputText,filename);
  function render(){cursor=0;tree=compiled.exports.Activities({roomId:1,token:'token',teacher});return tree;}
  function nodes(node){if(Array.isArray(node))return node.flatMap(nodes);if(!node||typeof node!=='object')return[];return[node,...nodes(node.props?.children)];}
  function text(node){if(Array.isArray(node))return node.map(text).join('');if(node==null||typeof node==='boolean')return'';return typeof node==='object'?text(node.props?.children):String(node);}
  async function settle(){await new Promise(setImmediate);await new Promise(setImmediate);render();}
  render();return{render,nodes,text,tree:()=>tree,calls,effects,settle,items,releaseLoad:()=>releaseLoad?.()};
}

test('teacher can create a text activity and reload it',async()=>{
  const h=harness({teacher:true});h.effects[0]();await h.settle();
  const forms=h.nodes(h.tree()).filter(node=>node.type==='form');assert.equal(forms.length,1);
  const fields=h.nodes(forms[0]).filter(node=>node.type==='input'||node.type==='textarea');
  fields[0].props.onChange({target:{value:'Reading'}});h.render();
  const prompt=h.nodes(h.tree()).filter(node=>node.type==='textarea')[0];prompt.props.onChange({target:{value:'Explain the main idea'}});h.render();
  h.nodes(h.tree()).find(node=>node.type==='form').props.onSubmit({preventDefault(){}});await h.settle();
  assert.ok(h.calls.some(call=>call.url==='/rooms/1/activities'&&call.method==='POST'),JSON.stringify(h.calls));
  assert.ok(h.calls.some(call=>call.method==='POST'&&call.url==='/rooms/1/activities'));
});

test('a student submits an answer to the activity in their class',async()=>{
  const h=harness();
  h.items.push({id:7,title:'Reading',prompt:'Explain the main idea',createdAt:1,submissionCount:0,submission:null});
  h.effects[0]();await h.settle();
  const answer=h.nodes(h.tree()).find(node=>node.type==='textarea'&&node.props['aria-label']==='Câu trả lời Reading');
  assert.ok(answer);answer.props.onChange({target:{value:'The text argues for cooperation.'}});h.render();
  const button=h.nodes(h.tree()).find(node=>node.type==='button'&&h.text(node.props.children)==='Nộp câu trả lời');
  button.props.onClick();await h.settle();
  assert.ok(h.calls.some(call=>call.url==='/rooms/1/activities/7/submissions'&&call.method==='POST'&&call.body.answer==='The text argues for cooperation.'));
  assert.ok(h.calls.some(call=>call.url==='/rooms/1/activities/7/submissions'&&call.method==='POST'));
});

test('busy activities show an accessible loading indicator until the request finishes',async()=>{
  const h=harness({delayLoad:true});
  const pending=h.effects[0]();
  h.render();
  const status=h.nodes(h.tree()).find(node=>node.type==='p'&&node.props.role==='status');
  assert.ok(status);
  assert.match(h.text(status),/Đang tải hoặc cập nhật bài tập/);
  assert.equal(h.tree().props['aria-busy'],true);
  h.releaseLoad();await pending;await h.settle();
  assert.equal(h.nodes(h.tree()).some(node=>node.type==='p'&&node.props.role==='status'),false);
  assert.equal(h.tree().props['aria-busy'],false);
});
