import React, { useEffect, useRef, useState } from 'react';
import { ClassroomMedia } from './ClassroomMedia';
import { Icon } from './Icons';
import { PasswordDialog } from './PasswordDialog';
import { RoomEntryDialog } from './RoomEntryDialog';
import { ClassManagement } from './ClassManagement';
import { MeetingHistoryPanel } from './MeetingHistory';
import { Activities } from './Activities';
import { api, Detail, JoinRequest, JoinStatus, Room, User } from './api';

export function Classes({ user, token, initial, onLogout, logoutBusy, logoutError }: { user: User; token: string; initial: Room[]; onLogout: () => void; logoutBusy: boolean; logoutError: string }) {
  const [rooms,setRooms]=useState(initial);
  const [passwordOpen,setPasswordOpen]=useState(false);
  const [entryOpen,setEntryOpen]=useState(false);
  const [entryMode,setEntryMode]=useState<"create"|"join">("create");
  const mutation=useRef(false);
  const revision=useRef(0);
  const [managementOpen,setManagementOpen]=useState(false);
  const [room,setRoom]=useState<Detail|null>(null);
  const [showingClasses,setShowingClasses]=useState(true);
  const [joinedMeeting,setJoinedMeeting]=useState<number|null>(null);
  const [showingMeeting,setShowingMeeting]=useState(false);
  const inMeeting=!!room&&joinedMeeting!==null&&room.meetingOpen&&room.meetingId===joinedMeeting;
  const [requests,setRequests]=useState<JoinRequest[]>([]);
  const [joinStatuses,setJoinStatuses]=useState<JoinStatus[]>([]);
  const [message,setMessage]=useState('');
  const [busy,setBusy]=useState(false);
  async function refresh() {
    const r=await api('/rooms','GET',undefined,token);setRooms(r.rooms??[]);
    {const q=await api('/rooms/join-requests','GET',undefined,token);setJoinStatuses(q.joinStatuses??[]);}
  }
  useEffect(()=>{
    let active=true;let inFlight=false;
    async function poll(){if(inFlight||document.hidden||mutation.current)return;inFlight=true;const current=revision.current;const valid=()=>active&&!mutation.current&&current===revision.current;try{
      if(showingClasses){const [r,q]=await Promise.all([api("/rooms","GET",undefined,token),api("/rooms/join-requests","GET",undefined,token)]);if(valid()){setRooms(r.rooms??[]);setJoinStatuses(q.joinStatuses??[]);}}
      if(room){const r=await api('/rooms/'+room.id,'GET',undefined,token);if(valid())setRoom(previous=>previous?.id===r.room?.id?r.room!:previous);}
      if(room?.owned&&managementOpen){const q=await api("/rooms/"+room.id+"/requests","GET",undefined,token);if(valid())setRequests(q.requests??[]);}
    }catch(e){if(valid())setMessage(e instanceof Error?e.message:"Không cập nhật được lớp. Hãy thử Làm mới.");}finally{inFlight=false;}}
    void poll();const timer=window.setInterval(()=>void poll(),5000);return()=>{active=false;window.clearInterval(timer);};
  },[token,room?.id,managementOpen,showingClasses]);
  async function submitEntry(value:string){await action(async()=>{if(entryMode==="create"){await api("/rooms","POST",{name:value},token);await refresh();setMessage("Đã tạo lớp. Bấm Vào lớp để bắt đầu.");}else{await api("/rooms/join-requests","POST",{code:value},token);await refresh();setMessage("Đã gửi yêu cầu. Kết quả sẽ tự cập nhật sau khi chủ lớp duyệt.");}setEntryOpen(false);});}
  async function open(id:number,navigate=false) {if(inMeeting&&room.id!==id){setMessage('Rời buổi học hiện tại bằng nút điện thoại đỏ trước khi vào lớp khác.');return;}const r=await api('/rooms/'+id,'GET',undefined,token);if(r.room?.owned){const q=await api('/rooms/'+id+'/requests','GET',undefined,token);setRequests(q.requests??[]);}else setRequests([]);if(room?.id!==id)setJoinedMeeting(null);setRoom(r.room!);if(navigate){setShowingClasses(false);setShowingMeeting(false);setManagementOpen(false);}return r.room;}
  async function action(work:()=>Promise<unknown>){if(mutation.current)return;++revision.current;mutation.current=true;setBusy(true);setMessage('');try{await work();}catch(e){setMessage(e instanceof Error?e.message:'Thao tác thất bại.');}finally{mutation.current=false;setBusy(false);}}
  function showClassList(){setManagementOpen(false);setShowingClasses(true);void action(refresh);}
  function showClassHome(){setManagementOpen(false);setShowingClasses(false);setShowingMeeting(false);}
  function showMeeting(){setManagementOpen(false);setShowingClasses(false);setShowingMeeting(true);}
  function leaveMeeting(){++revision.current;setJoinedMeeting(null);setShowingMeeting(false);setManagementOpen(false);}
  async function joinMeeting(){if(!room)return;const next=await open(room.id);if(!next?.meetingOpen||next.meetingId===null){setMessage('Buổi học đã kết thúc hoặc chưa mở. Chờ chủ lớp bắt đầu.');return;}setJoinedMeeting(next.meetingId);showMeeting();}
  async function changeMeeting(openMeeting:boolean){if(!room)return;await api('/rooms/'+room.id+'/meeting/'+(openMeeting?'start':'end'),'POST',undefined,token);const next=await open(room.id);setJoinedMeeting(openMeeting&&next?.meetingOpen?next.meetingId:null);setShowingMeeting(openMeeting);setShowingClasses(false);setManagementOpen(false);}
  return <section className={inMeeting&&showingMeeting&&!showingClasses ? "dashboard meeting-dashboard" : room&&!showingClasses?"dashboard class-dashboard":"dashboard"}>
    <nav className="classroom-rail" aria-label="Điều hướng">
      <div className="rail-brand" role="img" aria-label="Classroom"><svg viewBox="0 0 48 56" aria-hidden="true"><circle cx="24" cy="21" r="18"/><path d="m9 30 15-15 15 15-15 18Z"/><path d="m10 31 14 6 14-6"/></svg></div>
      <div className="rail-links"><button className={showingClasses ? 'active' : ''} aria-label="Danh sách lớp" title="Danh sách lớp" onClick={showClassList}><Icon name="home"/><span>Lớp học</span></button>{room&&<button className={!showingClasses&&!(inMeeting&&showingMeeting)?'active':''} onClick={showClassHome}><Icon name="book"/><span>Trang lớp</span></button>}{inMeeting&&<button className={!showingClasses&&showingMeeting?'active':''} onClick={showMeeting}><Icon name="screen"/><span>Phòng học</span></button>}<button onClick={()=>{if(room){setManagementOpen(true);setShowingClasses(false);void action(()=>open(room.id));}else{setMessage('');setEntryMode('create');setEntryOpen(true);}}}><Icon name="users"/><span>{room ? 'Quản lý lớp' : 'Tạo lớp'}</span></button>{!room&&<button onClick={()=>{setMessage('');setEntryMode('join');setEntryOpen(true);}}><Icon name="users"/><span>Tham gia</span></button>}<button aria-label="Đổi mật khẩu" onClick={()=>setPasswordOpen(true)}><Icon name="settings"/><span>Tài khoản</span></button></div>
      <button className="rail-logout" aria-label="Đăng xuất" disabled={logoutBusy} onClick={onLogout}><Icon name="leave"/></button>
    </nav>
    <div className="dashboard-heading"><div><p className="eyebrow">KHÔNG GIAN CỦA BẠN</p><h1>{room&&!showingClasses?room.name:<>Xin chào,<br/><span>{user.displayName}</span></>}</h1><p>{room&&!showingClasses?'Giáo viên: '+room.owner:'Quản lý và tham gia lớp học của bạn.'}</p></div></div>
    <p className="form-message" role="alert">{logoutError||message}</p>
    {(!room||showingClasses)&&<div className="dashboard-grid"><div><div className="section-heading"><h2>Lớp học của tôi</h2><span className="count-badge">{rooms.length} lớp</span><button className="ghost-button" disabled={busy} onClick={()=>void action(refresh)}>Làm mới</button></div>{room&&<p role="status">Bạn đang mở lớp “{room.name}”. Bấm “Trang lớp” để quay lại.</p>}<div className="rooms-list">{rooms.length===0?<div className="room-card"><div><h3>Bạn chưa tham gia lớp nào</h3><p>Tạo lớp của bạn hoặc nhập mã để tham gia lớp khác.</p></div></div>:rooms.map(r=><article className="room-card" key={r.id}><div><h3>{r.name}</h3><p>{r.owned?'Chủ lớp':'Thành viên'} · {r.members} người học</p></div><button className="ghost-button" disabled={busy} onClick={()=>void action(()=>open(r.id,true))}>Mở lớp</button></article>)}</div></div>{!inMeeting&&<aside className="auth-card"><h3>Tạo hoặc tham gia lớp</h3><p>Quyền của bạn được xác định riêng trong mỗi lớp. Người tạo là chủ lớp; người tham gia là thành viên.</p><div className="toolbar"><button className="primary-button" onClick={()=>{setMessage('');setEntryMode('create');setEntryOpen(true);}}>Tạo lớp mới</button><button className="ghost-button" onClick={()=>{setMessage('');setEntryMode('join');setEntryOpen(true);}}>Nhập mã lớp</button></div>{<section className="join-statuses"><h4>Yêu cầu của bạn</h4><p className="password-hint">Kết quả tự cập nhật mỗi 5 giây khi trang đang mở. Đây là kết quả yêu cầu; danh sách lớp ở bên trái thể hiện quyền tham gia hiện tại.</p>{joinStatuses.length===0 ? <p>Chưa gửi yêu cầu nào.</p> : joinStatuses.map(q=><div className="member-row" key={q.roomId}><span>{q.roomName}<small>{q.status==='PENDING'?'Đang chờ duyệt':q.status==='REJECTED'?'Đã bị từ chối':'Yêu cầu đã được duyệt'}</small></span></div>)}</section>}</aside>}</div>}
    {room&&<div hidden={showingClasses}>
      <section className="class-overview" hidden={inMeeting&&showingMeeting} aria-label="Trang lớp">
        <div className="class-overview-bar"><div><span className={'class-meeting-state'+(room.meetingOpen?' is-open':'')}>{room.meetingOpen?'Buổi học đang diễn ra':'Không có buổi học đang diễn ra'}</span><p>{inMeeting?'Bạn đang tham gia buổi học. Xem trang lớp vẫn giữ kết nối.':'Xem lại các buổi đã học hoặc tham gia buổi đang mở.'}</p></div>{room.meetingOpen?<button className="primary-button" disabled={busy} onClick={()=>void action(joinMeeting)}>{inMeeting?'Quay lại buổi học':'Vào buổi học'}</button>:room.owned?<button className="primary-button" disabled={busy} onClick={()=>void action(()=>changeMeeting(true))}>Bắt đầu buổi học</button>:<p>Chờ chủ lớp bắt đầu buổi học.</p>}</div>
        <MeetingHistoryPanel key={room.id} roomId={room.id} token={token} owned={room.owned} refreshKey={String(room.meetingOpen)+':'+room.meetingId} meetingOpen={room.meetingOpen} currentMeetingId={room.meetingId} onJoin={()=>void action(joinMeeting)} joinBusy={busy}/>
        <Activities key={'activities-'+room.id} roomId={room.id} token={token} teacher={room.owned}/>
      </section>
      {inMeeting&&<div hidden={!showingMeeting}><ClassroomMedia roomId={room.id} roomName={room.name} ownerName={room.owner} owned={room.owned} token={token} user={user} meetingId={room.meetingId} onCheckMeeting={()=>void action(()=>open(room.id))} onMeetingEnded={()=>{leaveMeeting();setRoom(previous=>previous?.id===room.id?{...previous,meetingOpen:false}:previous);}} meetingBusy={busy} meetingOpen={room.meetingOpen} onMeetingChange={openMeeting=>void action(()=>changeMeeting(openMeeting))} onExit={leaveMeeting} onManage={()=>{setManagementOpen(true);void action(()=>open(room.id));}} /></div>}
      {managementOpen&&<ClassManagement room={room} token={token} requests={requests} busy={busy} message={message} onClose={()=>setManagementOpen(false)} onRefresh={()=>void action(()=>open(room.id))}
        onAdd={username=>void action(async()=>{if(room.members.some(m=>m.username===username)){setMessage("Tài khoản này đã là thành viên lớp.");return;}await api("/rooms/"+room.id+"/members","POST",{username},token);await open(room.id);setMessage("Đã thêm học viên vào lớp.");})}
        onRemove={member=>{if(confirm("Xóa "+member.displayName+" khỏi lớp? Người này sẽ mất quyền truy cập và cần được thêm hoặc duyệt lại."))void action(async()=>{await api("/rooms/"+room.id+"/members/"+member.id,"DELETE",undefined,token);await open(room.id);setMessage("Đã xóa "+member.displayName+" khỏi lớp.");});}}
        onReview={(account,approve)=>void action(async()=>{await api("/rooms/"+room.id+"/requests/"+account,"POST",{approve},token);await open(room.id);setMessage(approve?"Đã duyệt học viên vào lớp.":"Đã từ chối yêu cầu tham gia.");})}
        onArchive={()=>{if(confirm("Lưu trữ lớp sẽ dừng quyền truy cập của mọi thành viên. Hiện chưa có chức năng khôi phục trên giao diện. Tiếp tục?"))void action(async()=>{await api("/rooms/"+room.id+"/archive","POST",undefined,token);setManagementOpen(false);setRoom(null);await refresh();setMessage("Đã lưu trữ lớp.");});}}
        onLeave={()=>{if(confirm("Rút khỏi danh sách lớp? Bạn sẽ cần được duyệt lại để vào lớp."))void action(async()=>{await api("/rooms/"+room.id+"/leave","POST",undefined,token);setManagementOpen(false);setRoom(null);await refresh();setMessage("Đã rút khỏi lớp.");});}} />}
    </div>}
    {passwordOpen&&<PasswordDialog token={token} onClose={()=>setPasswordOpen(false)} onChanged={()=>window.dispatchEvent(new Event('pbl4-session-expired'))}/>}
    {entryOpen&&<RoomEntryDialog teacher={entryMode==="create"} busy={busy} message={message} onClose={()=>setEntryOpen(false)} onSubmit={value=>void submitEntry(value)}/> }
  </section>;
}

