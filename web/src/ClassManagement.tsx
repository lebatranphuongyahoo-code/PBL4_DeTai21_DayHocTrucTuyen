import React, { useEffect, useRef, useState } from 'react';
import { Detail, JoinRequest, Member } from './api';

type Props = { room: Detail; requests: JoinRequest[]; busy: boolean; message: string; onClose: () => void; onRefresh: () => void; onAdd: (username: string) => void; onRemove: (member: Member) => void; onReview: (account: number, approve: boolean) => void; onArchive: () => void; onLeave: () => void };

export function ClassManagement({ room, requests, busy, message, onClose, onRefresh, onAdd, onRemove, onReview, onArchive, onLeave }: Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [username, setUsername] = useState('');
  const [query, setQuery] = useState('');
  const [copyStatus, setCopyStatus] = useState('');
  useEffect(() => { const node = dialog.current; node?.showModal(); return () => node?.close(); }, []);
  const members = room.members.filter(m => (m.displayName + ' ' + m.username).toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi')));
  async function copyCode() {
    try { await navigator.clipboard.writeText(room.inviteCode!); setCopyStatus('Đã sao chép mã lớp.'); }
    catch { setCopyStatus('Không sao chép được. Bạn có thể chọn mã rồi nhấn Ctrl+C.'); }
  }
  return <dialog className="class-management-dialog" ref={dialog} aria-labelledby="management-title" onCancel={onClose}>
    <header className="management-heading"><div><p>QUẢN LÝ LỚP HỌC</p><h2 id="management-title">{room.name}</h2><span>Danh sách đăng ký gồm {room.members.length} người, kể cả chủ lớp. Số này khác số kết nối đang ở trong phòng.</span></div><button type="button" className="ghost-button" onClick={onClose}>Đóng</button></header>
    <div className="management-body">
      <div className="management-feedback" role="status" aria-live="polite">{busy ? 'Đang xử lý…' : message}</div>
      <div className="management-columns">
        <section className="management-section"><div className="section-heading"><h3>Thành viên đã đăng ký ({room.members.length})</h3><button type="button" className="ghost-button" disabled={busy} onClick={onRefresh}>Làm mới</button></div><label>Tìm theo tên hoặc tài khoản<input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Tìm thành viên…"/></label><div className="registered-members">{members.map(m=><div className="registered-member" key={m.id}><span className="avatar">{m.displayName.slice(0,2).toUpperCase()}</span><div><strong>{m.displayName}</strong><small>@{m.username} · {m.role==='TEACHER'?'Chủ lớp':'Học viên'}</small></div>{room.owned && m.role==='STUDENT' && <button type="button" className="ghost-button danger" disabled={busy} onClick={()=>onRemove(m)}>Xóa khỏi lớp</button>}</div>)}{members.length===0&&<p>Không tìm thấy thành viên phù hợp.</p>}</div></section>
        <div className="management-actions">{room.owned ? <>
          <section className="management-section"><h3>Mời học viên</h3><p>Gửi mã này cho học viên. Học viên nhập mã rồi chờ bạn duyệt.</p><div className="invite-code"><code>{room.inviteCode}</code><button type="button" className="ghost-button" onClick={()=>void copyCode()}>Sao chép mã</button></div><p role="status">{copyStatus}</p><form onSubmit={e=>{e.preventDefault();onAdd(username.trim());}}><label htmlFor="add-student">Thêm trực tiếp bằng tài khoản có sẵn</label><p className="field-help">Nhập tên tài khoản học viên đã đăng ký. Thêm trực tiếp sẽ cấp quyền vào lớp ngay.</p><input id="add-student" name="username" value={username} onChange={e=>setUsername(e.target.value)} required maxLength={32} placeholder="Ví dụ: nguyenvanan" disabled={busy}/><button className="primary-button" type="submit" disabled={busy||!username.trim()}>Thêm học viên</button></form></section>
          <section className="management-section"><h3>Yêu cầu chờ duyệt ({requests.length})</h3><p>Yêu cầu tự cập nhật mỗi 5 giây khi cửa sổ này đang mở. Bạn cũng có thể bấm Làm mới.</p>{requests.length===0?<p className="empty-management">Không có yêu cầu đang chờ.</p>:requests.map(q=><div className="join-request" key={q.accountId}><strong>{q.displayName}</strong><small>@{q.username}</small><div><button type="button" className="primary-button" disabled={busy} onClick={()=>onReview(q.accountId,true)}>Duyệt</button><button type="button" className="ghost-button" disabled={busy} onClick={()=>onReview(q.accountId,false)}>Từ chối</button></div></div>)}</section>
        </> : <section className="management-section"><h3>Tư cách thành viên</h3><p>Bạn đã được thêm vào lớp. Đóng cửa sổ này để tiếp tục buổi học.</p><p>Rời buổi học chỉ ngắt kết nối. Rút khỏi lớp sẽ xóa tư cách thành viên và bạn cần được duyệt lại.</p></section>}</div>
      </div>
      <section className="membership-danger-zone"><div><h3>{room.owned?'Lưu trữ lớp học':'Rút khỏi danh sách lớp'}</h3><p>{room.owned?'Dừng quyền truy cập của tất cả thành viên. Đây không phải nút kết thúc một buổi học. Hiện chưa có chức năng khôi phục lớp trên giao diện.':'Xóa tư cách thành viên của bạn. Nếu chỉ muốn ngắt kết nối, hãy dùng Rời buổi học.'}</p></div><button type="button" className="ghost-button danger" disabled={busy} onClick={room.owned?onArchive:onLeave}>{room.owned?'Lưu trữ lớp':'Rút khỏi lớp'}</button></section>
    </div>
  </dialog>;
}
