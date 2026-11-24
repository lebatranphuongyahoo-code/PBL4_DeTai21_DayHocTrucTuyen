import React, { useEffect, useRef, useState } from 'react';
import { api, MeetingHistory as Meeting, MeetingHistoryReply } from './api';
import { MeetingChat } from './MeetingChat';
import { Icon } from './Icons';

const endReasons: Record<string,string> = {
  OWNER: 'Chủ lớp kết thúc',
  OWNER_TIMEOUT: 'Tự đóng: chủ lớp vắng quá 10 phút',
  SERVER_RESTART: 'Đóng khi server khởi động lại; giờ kết thúc thực tế không xác định',
};

export function MeetingCard({meeting,roomId,token,owned,onJoin,joinDisabled}: {meeting:Meeting;roomId:number;token:string;owned:boolean;onJoin?:()=>void;joinDisabled:boolean}) {
  const [expanded,setExpanded]=useState(false);
  const ended=meeting.endedAt!==null;
  const chatId='meeting-chat-'+roomId+'-'+meeting.id;
  let duration='';
  if(ended&&!meeting.startUncertain&&meeting.endReason!=='SERVER_RESTART'){
    const seconds=Math.max(0,Math.floor((meeting.endedAt!-meeting.startedAt)/1000));
    duration=[Math.floor(seconds/3600)?Math.floor(seconds/3600)+' giờ':'',Math.floor(seconds%3600/60)?Math.floor(seconds%3600/60)+' phút':'',seconds%60||!seconds?seconds%60+' giây':''].filter(Boolean).join(' ');
  }
  return <article className={'meeting-card'+(!ended?' meeting-card-live':'')}>
    <div className="meeting-card-heading"><span className="meeting-card-icon"><Icon name="screen"/></span><div><span className="meeting-card-status">{ended?'Đã kết thúc':'Đang diễn ra'}</span><h3>Buổi học ngày {new Date(meeting.startedAt).toLocaleDateString('vi-VN')}</h3></div></div>
    <div className="meeting-card-times"><span>{meeting.startUncertain?'Ghi nhận từ':'Bắt đầu'} <time dateTime={new Date(meeting.startedAt).toISOString()}>{new Date(meeting.startedAt).toLocaleString('vi-VN')}</time></span>{ended&&<span>Kết thúc <time dateTime={new Date(meeting.endedAt!).toISOString()}>{new Date(meeting.endedAt!).toLocaleString('vi-VN')}</time></span>}</div>
    {ended&&<p className="meeting-duration">{duration?'Thời lượng: '+duration:'Không đủ dữ liệu xác định thời lượng.'}</p>}
    {meeting.endReason&&<p className="meeting-end-reason">{endReasons[meeting.endReason]??meeting.endReason}</p>}
    {meeting.startUncertain&&<p>Thời điểm bắt đầu thực tế chưa được xác minh.</p>}
    <div className="meeting-card-actions">{!ended&&onJoin&&<button className="primary-button" disabled={joinDisabled} onClick={onJoin}>Vào buổi học</button>}<button className="ghost-button" aria-expanded={expanded} aria-controls={chatId} onClick={()=>setExpanded(value=>!value)}>{expanded?'Ẩn tin nhắn':'Xem tin nhắn'}</button></div>
    {expanded&&<div id={chatId} className="meeting-card-chat"><MeetingChat roomId={roomId} meetingId={meeting.id} token={token}/></div>}
    {owned&&<details className="meeting-statistics"><summary>Lượt kết nối gần nhất (tối đa 200)</summary>
      {meeting.connections.length===0?<p>Chưa ghi nhận lượt kết nối.</p>:<ul>{meeting.connections.map(c=><li key={c.id}>{c.displayName} (@{c.username}) · vào {new Date(c.joinedAt).toLocaleString('vi-VN')} · {c.leftAt?'rời '+new Date(c.leftAt).toLocaleString('vi-VN'):c.interrupted?'gián đoạn, không rõ giờ rời':meeting.endedAt?'đã đóng buổi':'đang kết nối'}</li>)}</ul>}
    </details>}
  </article>;
}

export function MeetingHistoryPanel({roomId,token,refreshKey,owned,meetingOpen,currentMeetingId,onJoin,joinBusy}: {roomId:number;token:string;refreshKey:string;owned:boolean;meetingOpen:boolean;currentMeetingId:number|null;onJoin:()=>void;joinBusy:boolean}) {
  const [meetings,setMeetings]=useState<Meeting[]>([]),[busy,setBusy]=useState(false),[more,setMore]=useState(false),[error,setError]=useState('');
  const [legacyOpen,setLegacyOpen]=useState(false);
  const [hasLegacyChat,setHasLegacyChat]=useState(false);
  const [refresh,setRefresh]=useState(0);
  const version=useRef(0);
  useEffect(()=>{
    let active=true;++version.current;setBusy(true);setError('');
    api<MeetingHistoryReply>('/rooms/'+roomId+'/meetings','GET',undefined,token).then(reply=>{
      if(active){setMeetings(reply.meetings??[]);setMore((reply.meetings?.length??0)===50);setHasLegacyChat(reply.hasLegacyChat===true);}
    }).catch(e=>{if(active)setError(e.message);}).finally(()=>{if(active)setBusy(false);});
    return()=>{active=false;++version.current;};
  },[roomId,token,refreshKey,refresh]);
  async function older(){
    if(busy)return;setBusy(true);setError('');
    const current=version.current;
    try {const reply=await api('/rooms/'+roomId+'/meetings?before='+meetings[meetings.length-1].id,'GET',undefined,token);
      if(current===version.current){setMeetings(items=>[...items,...(reply.meetings??[])]);setMore((reply.meetings?.length??0)===50);}
    }catch(e){if(current===version.current)setError(e instanceof Error?e.message:'Không tải được lịch sử.');}finally{if(current===version.current)setBusy(false);}
  }
  return <section className="meeting-feed" aria-label="Các buổi học"><div className="section-heading"><h2>Các buổi học</h2>
    <button className="ghost-button" disabled={busy} onClick={()=>setRefresh(x=>x+1)}>Làm mới lịch sử</button></div>
    {error&&<p role="alert">{error}</p>}{busy&&<p role="status">Đang tải lịch sử…</p>}
    {!busy&&!meetings.length&&!error&&<div className="meeting-feed-empty"><Icon name="book"/><h3>Chưa có buổi học</h3><p>{owned?'Bắt đầu buổi học đầu tiên để cùng học và trao đổi với lớp.':'Các buổi học sẽ xuất hiện tại đây khi chủ lớp bắt đầu.'}</p></div>}
    <div className="meeting-timeline">{meetings.map(meeting=><MeetingCard key={meeting.id+':'+refresh+':'+refreshKey} meeting={meeting} roomId={roomId} token={token} owned={owned} onJoin={meetingOpen&&meeting.id===currentMeetingId?onJoin:undefined} joinDisabled={joinBusy}/>)}</div>
    {more&&<button className="ghost-button" disabled={busy} onClick={()=>void older()}>Tải buổi học cũ hơn</button>}
    {hasLegacyChat&&<details className="legacy-chat" onToggle={event=>setLegacyOpen(event.currentTarget.open)}><summary>Tin nhắn cũ chưa xác định buổi</summary>{legacyOpen&&<MeetingChat roomId={roomId} meetingId={0} token={token}/>}</details>}
  </section>;
}
