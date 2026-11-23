import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';

import { api, User, Room } from './api';
import { Classes } from './Classes';
import { AdminDashboard } from './AdminDashboard';

function App() {
  const [mode, setMode] = useState<'login'|'register'>('login');
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState('');
  const [rooms, setRooms] = useState<Room[]>([]);
  const [message, setMessage] = useState('');
  const [success, setSuccess] = useState(false);
  const [busy, setBusy] = useState(false);
  const [restoring,setRestoring]=useState(true);
  useEffect(()=>{let active=true;void api('/auth/session').then(async reply=>{if(!reply.user||!reply.token)return;const list=await api('/rooms','GET',undefined,reply.token);if(active){setRooms(list.rooms??[]);setUser(reply.user);setToken(reply.token);}}).catch(error=>{if(active&&!(error instanceof Error&&error.message.includes('Phiên đã hết hạn')))setMessage('Không khôi phục được phiên. Bạn có thể đăng nhập lại.');}).finally(()=>{if(active)setRestoring(false);});return()=>{active=false;};},[]);
  useEffect(()=>{const expire=()=>{setUser(null);setToken('');setRooms([]);setMessage('Phiên đã hết hạn hoặc bị thu hồi. Hãy đăng nhập lại.');};window.addEventListener('pbl4-session-expired',expire);return()=>window.removeEventListener('pbl4-session-expired',expire);},[]);
  const register = mode === 'register';
  function switchMode(next: 'login'|'register') { if (!busy) { setMode(next); setMessage(''); setSuccess(false); } }
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    if (register && data.get('password') !== data.get('confirmPassword')) {
      setSuccess(false); setMessage('Mật khẩu nhập lại chưa khớp.'); return;
    }
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
  return <div className={user ? 'app-shell signed-in' : 'app-shell'}>
    <header className="topbar"><a className="brand" href="/"><span className="brand-mark">P4</span><span><strong>Classroom</strong><small>PBL4 · Đề tài 21</small></span></a><span className="topbar-status">Không gian học tập trực tuyến</span></header>
    <main>{restoring?<p role="status">Đang khôi phục phiên đăng nhập…</p>:!user ? <section className="auth-layout">
      <div className="intro-panel"><div className="auth-panel-top"><a className="auth-brand" href="/"><span className="brand-mark">P4</span><span><strong>Classroom</strong><small>PBL4 · Đề tài 21</small></span></a><span className="auth-index">HỌC TRỰC TUYẾN</span></div><div className="auth-story"><p className="eyebrow">CÙNG HỌC · CÙNG TIẾN BỘ</p><h1>Mở cửa lớp học.<br/><em>Kết nối tri thức.</em></h1><p className="intro-copy">Một nơi để giáo viên và học viên gặp nhau, trao đổi bài học và cùng tiến bộ.</p></div><div className="auth-scene" aria-hidden="true"><div className="scene-orbit orbit-one"></div><div className="scene-orbit orbit-two"></div><div className="class-snapshot"><div className="snapshot-head"><span className="snapshot-mark">P4</span><span>KHÔNG GIAN LỚP HỌC</span><i></i></div><div className="snapshot-content"><div className="snapshot-lesson"><small>TOÁN ỨNG DỤNG</small><strong>Học cùng nhau,<br/>tiến xa hơn.</strong><div className="lesson-lines"><i></i><i></i><i></i></div></div><div className="snapshot-people"><span className="student-orb orb-a">A</span><span className="student-orb orb-b">M</span><span className="student-orb orb-c">L</span><small>cùng học hôm nay</small></div></div><div className="snapshot-foot"><span>Trao đổi trực tiếp</span><span className="snapshot-pulse"><i></i> Đang kết nối</span></div></div><span className="scene-note scene-note-a">Bài tập tương tác <b>↗</b></span><span className="scene-note scene-note-b">Một lớp học chung <b>✳</b></span></div><div className="auth-panel-footer"><p>“Học tốt hơn khi cùng nhau kết nối.”</p><small>Không gian học tập trực tuyến · PBL4</small></div></div>
      <div className="auth-card"><div className="auth-tabs" aria-label="Chọn đăng nhập hoặc đăng ký"><button type="button" className={`tab ${!register ? 'is-active' : ''}`} aria-pressed={!register} onClick={()=>switchMode('login')} disabled={busy}>Đăng nhập</button><button type="button" className={`tab ${register ? 'is-active' : ''}`} aria-pressed={register} onClick={()=>switchMode('register')} disabled={busy}>Tạo tài khoản</button></div><div className="auth-heading"><p className="eyebrow">{register ? 'BẮT ĐẦU TỪ ĐÂY' : 'CHÀO MỪNG QUAY LẠI'}</p><h2>{register ? 'Tạo tài khoản' : 'Đăng nhập vào lớp học'}</h2><p>{register ? 'Tên tài khoản 3–32 ký tự, bắt đầu bằng chữ không dấu, chỉ gồm chữ, số và dấu _.' : 'Sử dụng tài khoản của bạn để tiếp tục.'}</p></div>
      <form onSubmit={submit}><fieldset disabled={busy}>{register && <label>Họ và tên<input name="displayName" autoComplete="name" required minLength={2} maxLength={80} placeholder="Nguyễn Văn An"/></label>}<label>Tên tài khoản<input name="username" autoComplete="username" required minLength={3} maxLength={32} placeholder="vd: nguyenvanan"/></label><label>Mật khẩu<input name="password" type="password" autoComplete={register ? 'new-password' : 'current-password'} required placeholder="Nhập mật khẩu"/></label>{register && <><label>Nhập lại mật khẩu<input name="confirmPassword" type="password" autoComplete="new-password" required placeholder="Nhập lại mật khẩu"/></label><p className="password-hint">Tối thiểu 8 ký tự, gồm chữ hoa A–Z, chữ thường a–z, số và ký tự đặc biệt. Tối đa 72 byte UTF-8.</p><div className="notice"><span><strong>Quyền tài khoản</strong>Tài khoản mới bắt đầu với vai trò học viên. Admin có thể cấp quyền giảng viên sau khi đăng ký.</span></div></>}<button className="primary-button" type="submit"><span>{busy ? 'Đang xử lý…' : register ? 'Tạo tài khoản' : 'Đăng nhập'}</span><span className="button-arrow">→</span></button></fieldset></form><p className={`form-message ${success ? 'success' : ''}`} role="alert">{message}</p></div>
    </section> : user.role === 'ADMIN' ? <AdminDashboard user={user} token={token} onLogout={()=>void logout()} logoutBusy={busy} logoutError={message}/> : <Classes user={user} token={token} initial={rooms} onLogout={()=>void logout()} logoutBusy={busy} logoutError={message}/>}</main>
    <footer><span>PBL4 · Dạy học trực tuyến</span><span>Kết nối lớp học · Chia sẻ bài giảng</span></footer>
  </div>;
}
createRoot(document.getElementById('root')!).render(<App/>);
