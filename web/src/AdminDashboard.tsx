import React, { useEffect, useState } from 'react';
import { AdminReply, AdminRoom, AdminRoomDetail, AdminUser, api, User } from './api';
import { Icon } from './Icons';
import { PasswordDialog } from './PasswordDialog';

export function AdminDashboard({ user, token, onLogout, logoutBusy, logoutError }: { user: User; token: string; onLogout: () => void; logoutBusy: boolean; logoutError: string }) {
  const [rooms,setRooms]=useState<AdminRoom[]>([]);
  const [roomsLoading,setRoomsLoading]=useState(true);
  const [roomsError,setRoomsError]=useState('');
  const [users,setUsers]=useState<AdminUser[]>([]);
  const [usersLoading,setUsersLoading]=useState(true);
  const [usersError,setUsersError]=useState('');
  const [details,setDetails]=useState<AdminRoomDetail|null>(null);
  const [selectedRoom,setSelectedRoom]=useState<number|null>(null);
  const [ownerRoom,setOwnerRoom]=useState<number|null>(null);
  const [ownerUsername,setOwnerUsername]=useState('');
  const [createOpen,setCreateOpen]=useState(false);
  const [newRoomName,setNewRoomName]=useState('');
  const [newRoomTeacher,setNewRoomTeacher]=useState('');
  const [studentId,setStudentId]=useState('');
  const [targetRoomId,setTargetRoomId]=useState('');
  const [filter,setFilter]=useState('');
  const [statusFilter,setStatusFilter]=useState<'ALL'|'ACTIVE'|'ARCHIVED'>('ALL');
  const [message,setMessage]=useState('');
  const [busy,setBusy]=useState(false);
  const [passwordOpen,setPasswordOpen]=useState(false);

  async function refresh() {
    setRoomsLoading(true);setRoomsError('');
    try{const response=await api<AdminReply>('/admin/rooms','GET',undefined,token);setRooms(response.rooms??[]);return true;}
    catch(error){setRoomsError(error instanceof Error?error.message:'Không tải được danh sách lớp.');return false;}
    finally{setRoomsLoading(false);}
  }
  async function refreshUsers() {
    setUsersLoading(true);setUsersError('');
    try{const response=await api<AdminReply>('/admin/users','GET',undefined,token);setUsers(response.users??[]);return true;}
    catch(error){setUsersError(error instanceof Error?error.message:'Không tải được danh sách người dùng.');return false;}
    finally{setUsersLoading(false);}
  }
  useEffect(()=>{void refresh();void refreshUsers();},[token]);
  async function action(work:()=>Promise<void>) {
    if(busy)return;setBusy(true);setMessage('');
    try{await work();}catch(error){setMessage(error instanceof Error?error.message:'Thao tác thất bại.');}
    finally{setBusy(false);}
  }
  async function openMembers(roomId:number) {
    await action(async()=>{const response=await api<AdminReply>('/admin/rooms/'+roomId,'GET',undefined,token);if(!response.room)throw new Error('Không tải được thành viên lớp.');setDetails(response.room);setSelectedRoom(roomId);setStudentId('');setTargetRoomId('');});
  }
  async function archive(room:AdminRoom) {
    if(!confirm('Lưu trữ lớp “'+room.name+'”? Thành viên sẽ mất quyền truy cập.'))return;
    await action(async()=>{await api('/admin/rooms/'+room.id+'/archive','POST',undefined,token);setSelectedRoom(null);setDetails(null);await refresh();setMessage('Đã lưu trữ lớp.');});
  }
  async function restore(room:AdminRoom) {
    if(!confirm('Khôi phục lớp “'+room.name+'” để thành viên có thể truy cập lại?'))return;
    await action(async()=>{await api('/admin/rooms/'+room.id+'/restore','POST',undefined,token);await refresh();setMessage('Đã khôi phục lớp.');});
  }
  async function assignOwner(event:React.FormEvent<HTMLFormElement>) {
    event.preventDefault();if(ownerRoom===null)return;
    await action(async()=>{await api('/admin/rooms/'+ownerRoom+'/owner','POST',{username:ownerUsername.trim()},token);setOwnerRoom(null);setOwnerUsername('');await refresh();setMessage('Đã đổi giảng viên phụ trách.');});
  }
  async function createRoom(event:React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if(!newRoomName.trim()||!newRoomTeacher)return;
    await action(async()=>{await api('/admin/rooms','POST',{name:newRoomName.trim(),teacherUsername:newRoomTeacher},token);setCreateOpen(false);setNewRoomName('');setNewRoomTeacher('');const loaded=await refresh();if(!loaded)throw new Error('Đã tạo lớp nhưng chưa tải lại được danh sách.');setMessage('Đã tạo lớp và chỉ định giảng viên phụ trách.');});
  }
  async function promoteTeacher(account:AdminUser) {
    await action(async()=>{await api('/admin/users/'+account.id+'/teacher','POST',undefined,token);const loaded=await refreshUsers();if(!loaded)throw new Error('Đã cấp quyền giảng viên nhưng chưa tải lại được danh sách người dùng.');setMessage('Đã cấp quyền giảng viên cho '+account.displayName+'.');});
  }
  async function transfer(event:React.FormEvent<HTMLFormElement>) {
    event.preventDefault();if(selectedRoom===null||!studentId||!targetRoomId)return;
    await action(async()=>{await api('/admin/rooms/'+selectedRoom+'/members/'+studentId+'/transfer','POST',{targetRoomId:Number(targetRoomId)},token);const response=await api<AdminReply>('/admin/rooms/'+selectedRoom,'GET',undefined,token);setDetails(response.room??null);setStudentId('');setMessage('Đã chuyển học viên sang lớp đích.');await refresh();});
  }
  const visibleRooms=rooms.filter(room=>(statusFilter==='ALL'||room.status===statusFilter)&&(room.name+' '+room.ownerDisplayName+' '+room.ownerUsername).toLocaleLowerCase('vi').includes(filter.toLocaleLowerCase('vi')));
  const teachers=users.filter(account=>account.role==='TEACHER');
  return <section className="dashboard admin-dashboard">
    <nav className="classroom-rail" aria-label="Điều hướng quản trị"><div className="rail-brand" role="img" aria-label="Classroom"><svg viewBox="0 0 48 56" aria-hidden="true"><circle cx="24" cy="21" r="18"/><path d="m9 30 15-15 15 15-15 18Z"/><path d="m10 31 14 6 14-6"/></svg></div><div className="rail-links"><span className="rail-current"><Icon name="home"/><span>Quản trị lớp</span></span><button aria-label="Đổi mật khẩu" title="Đổi mật khẩu" onClick={()=>setPasswordOpen(true)}><Icon name="settings"/><span>Tài khoản</span></button></div><button className="rail-logout" aria-label="Đăng xuất" disabled={logoutBusy} onClick={onLogout}><Icon name="leave"/></button></nav>
    <header className="admin-heading"><div><p className="eyebrow">QUẢN TRỊ HỆ THỐNG</p><h1>Quản lý lớp học</h1><p>{user.displayName} · Admin</p></div><button className="ghost-button" disabled={busy} onClick={()=>void action(async()=>{await refresh();setMessage('Đã cập nhật danh sách lớp.');})}>Làm mới</button></header>
    <p className="form-message" role="status" aria-live="polite">{logoutError||message}</p>
    <section className="admin-room-section" aria-labelledby="admin-rooms-title"><div className="section-heading"><h2 id="admin-rooms-title">Quản lý tất cả lớp</h2>{!roomsLoading&&!roomsError&&<span className="count-badge">{rooms.length} lớp</span>}<button type="button" className="primary-button" disabled={busy||teachers.length===0} onClick={()=>setCreateOpen(value=>!value)}>Tạo lớp</button></div>
      {createOpen&&<form className="admin-inline-form" onSubmit={createRoom}><label htmlFor="admin-room-name">Tên lớp</label><input id="admin-room-name" value={newRoomName} onChange={event=>setNewRoomName(event.target.value)} required minLength={2} maxLength={100} disabled={busy}/><label htmlFor="admin-room-teacher">Giảng viên phụ trách</label><select id="admin-room-teacher" value={newRoomTeacher} onChange={event=>setNewRoomTeacher(event.target.value)} required disabled={busy}><option value="">Chọn giảng viên</option>{teachers.map(teacher=><option value={teacher.username} key={teacher.id}>{teacher.displayName} (@{teacher.username})</option>)}</select><button className="primary-button" disabled={busy||!newRoomName.trim()||!newRoomTeacher}>Tạo lớp và gán giảng viên</button></form>}
      {!roomsLoading&&!roomsError&&<div className="admin-filter-row"><label className="admin-filter">Tìm lớp hoặc giảng viên<input value={filter} onChange={event=>setFilter(event.target.value)} placeholder="Tên lớp, giảng viên hoặc tài khoản"/></label><label className="admin-filter admin-status-filter">Trạng thái<select aria-label="Lọc trạng thái lớp" value={statusFilter} onChange={event=>setStatusFilter(event.target.value as 'ALL'|'ACTIVE'|'ARCHIVED')}><option value="ALL">Tất cả</option><option value="ACTIVE">Đang hoạt động</option><option value="ARCHIVED">Đã lưu trữ</option></select></label></div>}
      {roomsLoading?<p className="admin-empty" role="status">Đang tải danh sách lớp…</p>:roomsError?<p className="admin-empty" role="alert">{roomsError}</p>:visibleRooms.length===0?<p className="admin-empty">{rooms.length?'Không tìm thấy lớp phù hợp.':'Chưa có lớp nào.'}</p>:<div className="admin-room-list">{visibleRooms.map(room=><article className={'admin-room-row'+(room.status==='ARCHIVED'?' archived':'')} key={room.id}><div className="admin-room-main"><h3>{room.name}</h3><p>{room.ownerDisplayName} (@{room.ownerUsername}) · {room.members} thành viên · {room.status==='ARCHIVED'?'Đã lưu trữ':'Đang hoạt động'}</p></div><div className="admin-room-actions">{room.status==='ACTIVE'&&<><button className="ghost-button" disabled={busy} onClick={()=>void openMembers(room.id)}>Thành viên</button><button className="ghost-button" disabled={busy} onClick={()=>{setOwnerRoom(ownerRoom===room.id?null:room.id);setOwnerUsername('');}}>Đổi giảng viên</button><button className="ghost-button danger" disabled={busy} onClick={()=>void archive(room)}>Lưu trữ</button></>}{room.status==='ARCHIVED'&&<button className="ghost-button" disabled={busy} onClick={()=>void restore(room)}>Khôi phục</button>}</div>
        {ownerRoom===room.id&&<form className="admin-inline-form" onSubmit={assignOwner}><label htmlFor={'owner-'+room.id}>Tài khoản giảng viên mới</label><input id={'owner-'+room.id} value={ownerUsername} onChange={event=>setOwnerUsername(event.target.value)} required minLength={3} maxLength={32} autoComplete="off"/><button className="primary-button" disabled={busy||!ownerUsername.trim()}>Gán giảng viên</button></form>}
        {selectedRoom===room.id&&details&&<section className="admin-members" aria-label={'Thành viên lớp '+room.name}><h4>Thành viên · {details.name}</h4><ul>{details.members.map(member=><li key={member.id}><span>{member.displayName}<small>@{member.username}{member.id===details.ownerId?' · Giảng viên':''}</small></span>{member.id!==details.ownerId&&<button type="button" className={'ghost-button'+(Number(studentId)===member.id?' selected':'')} onClick={()=>setStudentId(String(member.id))}>Chọn</button>}</li>)}</ul><form className="admin-inline-form transfer-form" onSubmit={transfer}><label htmlFor={'target-'+room.id}>Chuyển học viên</label><select id={'target-'+room.id} value={targetRoomId} onChange={event=>setTargetRoomId(event.target.value)} required><option value="">Chọn lớp đích</option>{rooms.filter(target=>target.status==='ACTIVE'&&target.id!==room.id).map(target=><option value={target.id} key={target.id}>{target.name}</option>)}</select><p>{details.members.find(member=>String(member.id)===studentId)?.displayName??'Chọn một học viên trong danh sách trên.'}</p><button className="primary-button" disabled={busy||!studentId||!targetRoomId}>Chuyển lớp</button></form></section>}
      </article>)}</div>}
    </section>
    <section className="admin-room-section" aria-labelledby="admin-users-title"><div className="section-heading"><h2 id="admin-users-title">Quản lý tài khoản</h2>{!usersLoading&&!usersError&&<span className="count-badge">{users.length} tài khoản</span>}<button type="button" className="ghost-button" disabled={busy} onClick={()=>void refreshUsers()}>Làm mới người dùng</button></div><p className="admin-note">Cấp quyền giảng viên cho tài khoản học viên. Tài khoản được nâng quyền mới có thể được chọn làm chủ lớp.</p>{usersLoading?<p className="admin-empty" role="status">Đang tải danh sách người dùng…</p>:usersError?<p className="admin-empty" role="alert">{usersError}</p>:users.length===0?<p className="admin-empty">Chưa có tài khoản nào.</p>:<div className="admin-room-list">{users.map(account=><article className="admin-room-row" key={account.id}><div className="admin-room-main"><h3>{account.displayName}</h3><p>@{account.username} · {account.role==='ADMIN'?'Quản trị viên':account.role==='TEACHER'?'Giảng viên':'Học viên'}</p></div>{account.role==='STUDENT'&&<button type="button" className="ghost-button" disabled={busy} onClick={()=>void promoteTeacher(account)}>Cấp quyền giảng viên</button>}</article>)}</div>}</section>
    <p className="admin-note">Quyền Admin quản lý lớp không cấp quyền vào buổi học, bật mic hoặc chia sẻ màn hình.</p>
    {passwordOpen&&<PasswordDialog token={token} onClose={()=>setPasswordOpen(false)} onChanged={()=>window.dispatchEvent(new Event('pbl4-session-expired'))}/>}
  </section>;
}
