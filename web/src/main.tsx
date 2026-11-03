import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';

type User = { username: string; displayName: string; role: string };
type Room = { id: number; name: string; members: number };
type Reply = { ok: boolean; message?: string; errors?: Record<string,string>; user?: User; token?: string; rooms?: Room[] };
async function api(path: string, method = 'GET', body?: unknown, token?: string): Promise<Reply> {
  const response = await fetch(`/api${path}`, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const data: Reply = await response.json();
  if (!response.ok) throw new Error(Object.values(data.errors ?? {}).join(' ') || data.message || 'Yêu cầu thất bại.');
  return data;
}

function App() {
  const [mode, setMode] = useState<'login'|'register'>('login');
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState('');
  const [rooms, setRooms] = useState<Room[]>([]);
  const [message, setMessage] = useState('');
  const [success, setSuccess] = useState(false);
  const [busy, setBusy] = useState(false);
  const register = mode === 'register';
  function switchMode(next: 'login'|'register') { if (!busy) { setMode(next); setMessage(''); setSuccess(false); } }
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setBusy(true); setMessage(''); setSuccess(false);
    try {
      const credentials = { username: data.get('username'), password: data.get('password') };
      const response = await api(register ? '/auth/register' : '/auth/login', 'POST', register ? { ...credentials, displayName: data.get('displayName') } : credentials);
      if (register) {
        setMode('login'); form.reset(); setMessage('Tạo tài khoản thành công. Bạn có thể đăng nhập ngay.'); setSuccess(true);
      } else if (response.user && response.token) {
        const list = await api('/rooms','GET',undefined,response.token);
        setRooms(list.rooms ?? []); setUser(response.user); setToken(response.token);
      }
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Không thể kết nối máy chủ.'); }
    finally { setBusy(false); }
  }
  async function logout() {
    setBusy(true);
    try { await api('/auth/logout','POST',undefined,token); setUser(null); setToken(''); setRooms([]); setMessage(''); }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Không thể đăng xuất.'); }
    finally { setBusy(false); }
  }
  return <div className="app-shell">
    <header className="topbar"><a className="brand" href="/"><span className="brand-mark">P4</span><span><strong>Classroom</strong><small>PBL4 · Đề tài 21</small></span></a><span className="topbar-status">Không gian học tập trực tuyến</span></header>
    <main>{!user ? <section className="auth-layout">
      <div className="intro-panel"><p className="eyebrow">CÙNG HỌC · CÙNG TIẾN BỘ</p><h1>Vào lớp học.<br/><em>Tập trung hơn.</em></h1><p className="intro-copy">Một không gian để giáo viên tổ chức lớp và học viên theo dõi bài giảng. Bắt đầu bằng tài khoản của bạn.</p><div className="feature-list"><div><span className="feature-icon">01</span><span><b>Tài khoản cá nhân</b><small>Họ tên rõ ràng, quyền truy cập do server kiểm soát.</small></span></div><div><span className="feature-icon">02</span><span><b>Không gian lớp học</b><small>Chỉ hiển thị lớp bạn sở hữu hoặc đã là thành viên.</small></span></div></div></div>
      <div className="auth-card"><div className="auth-tabs"><button className={`tab ${!register ? 'is-active' : ''}`} onClick={()=>switchMode('login')} disabled={busy}>Đăng nhập</button><button className={`tab ${register ? 'is-active' : ''}`} onClick={()=>switchMode('register')} disabled={busy}>Tạo tài khoản</button></div><div className="auth-heading"><p className="eyebrow">{register ? 'BẮT ĐẦU TỪ ĐÂY' : 'CHÀO MỪNG QUAY LẠI'}</p><h2>{register ? 'Tạo tài khoản học viên' : 'Đăng nhập vào lớp học'}</h2><p>{register ? 'Điền thông tin để tạo tài khoản cá nhân.' : 'Sử dụng tài khoản của bạn để tiếp tục.'}</p></div>
      <form onSubmit={submit}><fieldset disabled={busy}>{register && <label>Họ và tên<input name="displayName" autoComplete="name" required minLength={2} maxLength={80} placeholder="Nguyễn Văn An"/></label>}<label>Tên tài khoản<input name="username" autoComplete="username" required minLength={3} maxLength={32} placeholder="vd: nguyenvanan"/></label><label>Mật khẩu<input name="password" type="password" autoComplete={register ? 'new-password' : 'current-password'} required placeholder="Nhập mật khẩu"/></label>{register && <><p className="password-hint">Tối thiểu 8 ký tự, gồm chữ hoa A–Z, chữ thường a–z, số và ký tự đặc biệt. Tối đa 72 byte UTF-8.</p><div className="notice"><span><strong>Quyền giáo viên</strong>Đăng ký công khai chỉ tạo tài khoản học viên. Luồng cấp quyền giáo viên đang được thiết kế.</span></div></>}<button className="primary-button" type="submit"><span>{busy ? 'Đang xử lý…' : register ? 'Tạo tài khoản' : 'Đăng nhập'}</span><span className="button-arrow">→</span></button></fieldset></form><p className={`form-message ${success ? 'success' : ''}`} role="alert">{message}</p></div>
    </section> : <section className="dashboard"><div className="dashboard-heading"><div><p className="eyebrow">KHÔNG GIAN CỦA BẠN</p><h1>Xin chào,<br/><span>{user.displayName}</span></h1><p>Những lớp học bạn được phép truy cập.</p></div><button className="ghost-button" onClick={logout} disabled={busy}>Đăng xuất</button></div><div className="dashboard-grid"><div><div className="section-heading"><h2>Lớp học của tôi</h2><span className="count-badge">{rooms.length} lớp</span></div>{rooms.length === 0 ? <div className="room-card"><div><h3>Bạn chưa tham gia lớp nào</h3><p>Chức năng tạo lớp và xét duyệt thành viên sẽ có ở mốc tiếp theo.</p></div></div> : rooms.map(room=><article className="room-card" key={room.id}><div><h3>{room.name}</h3><p>{room.members} thành viên</p></div></article>)}</div><aside className="next-panel"><p className="eyebrow">TÀI KHOẢN CỦA BẠN</p><h3>{user.displayName}</h3><p>@{user.username}</p><div className="next-rule"/><span className="mini-label">VAI TRÒ</span><strong>{user.role==='STUDENT' ? 'Học viên' : user.role==='TEACHER' ? 'Giáo viên' : 'Quản trị viên'}</strong></aside></div><p className="form-message" role="alert">{message}</p></section>}</main>
    <footer><span>PBL4 · Dạy học trực tuyến</span><span>Mốc 1 · Tài khoản & nền tảng dữ liệu</span></footer>
  </div>;
}
createRoot(document.getElementById('root')!).render(<App/>);
