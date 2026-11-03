import React, { useEffect, useState } from 'react';
import { ClassroomMedia } from './ClassroomMedia';
import { Icon } from './Icons';
import { ClassManagement } from './ClassManagement';
import { api, Detail, JoinRequest, JoinStatus, Room, User } from './api';

export function Classes({ user, token, initial, onLogout, logoutBusy, logoutError }: { user: User; token: string; initial: Room[]; onLogout: () => void; logoutBusy: boolean; logoutError: string }) {
  const [rooms,setRooms]=useState(initial);
  const [managementOpen,setManagementOpen]=useState(false);
  const [room,setRoom]=useState<Detail|null>(null);
  const [requests,setRequests]=useState<JoinRequest[]>([]);
  const [joinStatuses,setJoinStatuses]=useState<JoinStatus[]>([]);
  const [message,setMessage]=useState('');
  const [busy,setBusy]=useState(false);
  async function refresh() {
    const r=await api('/rooms','GET',undefined,token);setRooms(r.rooms??[]);
    if(user.role==='STUDENT'){const q=await api('/rooms/join-requests','GET',undefined,token);setJoinStatuses(q.joinStatuses??[]);}
  }
  useEffect(()=>{
    let active=true;
    if(user.role==='STUDENT') void api('/rooms/join-requests','GET',undefined,token).then(q=>{if(active)setJoinStatuses(q.joinStatuses??[]);}).catch(e=>{if(active)setMessage(e instanceof Error?e.message:'Không tải được yêu cầu tham gia.');});
    return ()=>{active=false;};
  },[token,user.role]);
  async function open(id:number) {const r=await api('/rooms/'+id,'GET',undefined,token);if(r.room?.owned){const q=await api('/rooms/'+id+'/requests','GET',undefined,token);setRequests(q.requests??[]);}else setRequests([]);setRoom(r.room!);}
  async function action(work:()=>Promise<void>){setBusy(true);setMessage('');try{await work();}catch(e){setMessage(e instanceof Error?e.message:'Thao tác thất bại.');}finally{setBusy(false);}}
  function form(event:React.FormEvent<HTMLFormElement>,work:(data:FormData)=>Promise<void>){event.preventDefault();const target=event.currentTarget;const data=new FormData(target);void action(async()=>{await work(data);target.reset();});}
  return <section className={room ? "dashboard meeting-dashboard" : "dashboard"}>
    <nav className="classroom-rail" aria-label="Điều hướng">
      <div className="rail-brand" aria-label="Classroom"><svg viewBox="0 0 48 56" aria-hidden="true"><circle cx="24" cy="21" r="18"/><path d="m9 30 15-15 15 15-15 18Z"/><path d="m10 31 14 6 14-6"/></svg></div>
      <div className="rail-links"><button className={!room ? 'active' : ''} aria-label="Danh sách lớp" onClick={()=>{setRoom(null);void action(refresh);}}><Icon name="home"/><span>Lớp học</span></button>{room&&<div className="rail-current" aria-current="page"><Icon name="screen"/><span>Phòng học</span></div>}<button onClick={()=>{if(room){setManagementOpen(true);void action(()=>open(room.id));}else{const input=document.querySelector<HTMLInputElement>('.dashboard-grid input');input?.scrollIntoView({behavior:'smooth',block:'center'});input?.focus();}}}><Icon name="book"/><span>{room ? 'Quản lý lớp' : user.role==='TEACHER'?'Tạo lớp':'Tham gia'}</span></button></div>
      <button className="rail-logout" aria-label="Đăng xuất" disabled={logoutBusy} onClick={onLogout}><Icon name="leave"/></button>
    </nav>
    <div className="dashboard-heading"><div><p className="eyebrow">KHÔNG GIAN CỦA BẠN</p><h1>{room?room.name:<>Xin chào,<br/><span>{user.displayName}</span></>}</h1><p>{room?'Giáo viên: '+room.owner:'Quản lý và tham gia lớp học của bạn.'}</p></div></div>
    <p className="form-message" role="alert">{logoutError||message}</p>
    {!room?<div className="dashboard-grid"><div><div className="section-heading"><h2>Lớp học của tôi</h2><span className="count-badge">{rooms.length} lớp</span><button className="ghost-button" disabled={busy} onClick={()=>void action(refresh)}>Làm mới</button></div><div className="rooms-list">{rooms.length===0?<div className="room-card"><div><h3>Bạn chưa tham gia lớp nào</h3><p>{user.role==='TEACHER'?'Tạo lớp đầu tiên để bắt đầu.':'Nhập mã lớp hoặc nhờ giáo viên thêm tài khoản của bạn.'}</p></div></div>:rooms.map(r=><article className="room-card" key={r.id}><div><h3>{r.name}</h3><p>{r.members} học viên</p></div><button className="ghost-button" disabled={busy} onClick={()=>void action(()=>open(r.id))}>Vào lớp</button></article>)}</div></div><aside className="auth-card"><h3>{user.role==='TEACHER'?'Tạo lớp học':'Xin tham gia lớp'}</h3><form onSubmit={e=>form(e,async data=>{if(user.role==='TEACHER'){await api('/rooms','POST',{name:data.get('name')},token);await refresh();}else{await api('/rooms/join-requests','POST',{code:data.get('code')},token);await refresh();setMessage('Đã gửi yêu cầu. Giáo viên cần duyệt trước khi bạn vào lớp.');}})}><fieldset disabled={busy}><label>{user.role==='TEACHER'?'Tên lớp':'Mã lớp'}<input name={user.role==='TEACHER'?'name':'code'} required maxLength={100}/></label><button className="primary-button">{user.role==='TEACHER'?'Tạo lớp':'Gửi yêu cầu'}</button></fieldset></form>{user.role==='STUDENT' && <section className="join-statuses"><h4>Yêu cầu của bạn</h4><p className="password-hint">Bấm Làm mới để cập nhật kết quả duyệt. Đây là kết quả yêu cầu; danh sách lớp ở bên trái thể hiện quyền tham gia hiện tại.</p>{joinStatuses.length===0 ? <p>Chưa gửi yêu cầu nào.</p> : joinStatuses.map(q=><div className="member-row" key={q.roomId}><span>{q.roomName}<small>{q.status==='PENDING'?'Đang chờ duyệt':q.status==='REJECTED'?'Đã bị từ chối':'Yêu cầu đã được duyệt'}</small></span></div>)}</section>}</aside></div>:<>
      <ClassroomMedia roomId={room.id} roomName={room.name} ownerName={room.owner} owned={room.owned} token={token} user={user} onExit={()=>{setManagementOpen(false);setRoom(null);void action(refresh);}} onManage={()=>{setManagementOpen(true);void action(()=>open(room.id));}} />
      {managementOpen&&<ClassManagement room={room} requests={requests} busy={busy} message={message} onClose={()=>setManagementOpen(false)} onRefresh={()=>void action(()=>open(room.id))}
        onAdd={username=>void action(async()=>{if(room.members.some(m=>m.username===username)){setMessage("Tài khoản này đã là thành viên lớp.");return;}await api("/rooms/"+room.id+"/members","POST",{username},token);await open(room.id);setMessage("Đã thêm học viên vào lớp.");})}
        onRemove={member=>{if(confirm("Xóa "+member.displayName+" khỏi lớp? Người này sẽ mất quyền truy cập và cần được thêm hoặc duyệt lại."))void action(async()=>{await api("/rooms/"+room.id+"/members/"+member.id,"DELETE",undefined,token);await open(room.id);setMessage("Đã xóa "+member.displayName+" khỏi lớp.");});}}
        onReview={(account,approve)=>void action(async()=>{await api("/rooms/"+room.id+"/requests/"+account,"POST",{approve},token);await open(room.id);setMessage(approve?"Đã duyệt học viên vào lớp.":"Đã từ chối yêu cầu tham gia.");})}
        onArchive={()=>{if(confirm("Lưu trữ lớp sẽ dừng quyền truy cập của mọi thành viên. Hiện chưa có chức năng khôi phục trên giao diện. Tiếp tục?"))void action(async()=>{await api("/rooms/"+room.id+"/archive","POST",undefined,token);setManagementOpen(false);setRoom(null);await refresh();setMessage("Đã lưu trữ lớp.");});}}
        onLeave={()=>{if(confirm("Rút khỏi danh sách lớp? Bạn sẽ cần được duyệt lại để vào lớp."))void action(async()=>{await api("/rooms/"+room.id+"/leave","POST",undefined,token);setManagementOpen(false);setRoom(null);await refresh();setMessage("Đã rút khỏi lớp.");});}} />}
    </>}
  </section>;
}

