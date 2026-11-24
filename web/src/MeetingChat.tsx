import React, { useEffect, useState } from 'react';
import { api, ChatMessage } from './api';
import { mergeChat } from './chat';

export function MeetingChat({roomId,meetingId,token}: {roomId:number;meetingId:number;token:string}) {
  const [messages,setMessages]=useState<ChatMessage[]>([]),[busy,setBusy]=useState(false),[more,setMore]=useState(false),[error,setError]=useState('');
  const [page,setPage]=useState(0),[refresh,setRefresh]=useState(0);
  useEffect(()=>{
    let active=true;setBusy(true);setError('');
    const path='/rooms/'+roomId+'/messages?meeting='+meetingId+(page?'&before='+page:'');
    api(path,'GET',undefined,token).then(reply=>{
      if(active){setMessages(current=>page?mergeChat(current,reply.messages??[]):reply.messages??[]);setMore((reply.messages?.length??0)===50);}
    }).catch(e=>{if(active)setError(e.message);}).finally(()=>{if(active)setBusy(false);});
    return()=>{active=false;};
  },[roomId,meetingId,token,page,refresh]);
  return <section>{meetingId!==0&&<h4>Chat của buổi học</h4>}
    <p>{meetingId===0?'Tin từ phiên bản cũ được giữ riêng vì chưa có mã buổi.':'Lịch sử chỉ đọc. Muốn gửi tin, hãy quay lại buổi đang mở.'}</p>
    <button className="ghost-button" disabled={busy} onClick={()=>{setPage(0);setRefresh(x=>x+1);}}>Làm mới chat</button>
    {more&&messages.length>0&&<button className="ghost-button" disabled={busy} onClick={()=>setPage(messages[0].id)}>Tải chat cũ hơn</button>}
    {busy&&<p role="status">Đang tải chat…</p>}{error&&<p role="alert">{error}</p>}
    {!busy&&!error&&!messages.length&&<p>Chưa có tin nhắn.</p>}
    <ol>{messages.map(message=><li key={message.id}><strong>{message.username}</strong> · <time dateTime={new Date(message.sentAt).toISOString()}>{new Date(message.sentAt).toLocaleString('vi-VN')}</time><p style={{whiteSpace:'pre-wrap'}}>{message.text}</p></li>)}</ol>
  </section>;
}
