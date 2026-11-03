import React, { useEffect, useRef, useState } from 'react';
import { api, User } from './api';

type Props = { roomId: number; owned: boolean; token: string; user: User };
type Playback = { context: AudioContext; times: Map<string, number>; sequences: Map<string, number> };
type Participant = { peerId: string; sourceId: string; username: string; owner: boolean; micAllowed: boolean; screenRequested: boolean; micRequested: boolean };
type Controls = { peerId: string; presenter: string; microphonesLocked: boolean; participants: Participant[] };
const SCREEN = 1, AUDIO = 2;

export function ClassroomMedia({ roomId, owned, token }: Props) {
  const socket = useRef<WebSocket | null>(null), generation = useRef(0);
  const screen = useRef<MediaStream | null>(null), frameTimer = useRef<number | undefined>(undefined);
  const microphone = useRef<MediaStream | null>(null), capture = useRef<AudioContext | null>(null);
  const playback = useRef<Playback | null>(null), sequence = useRef(0), frameUrl = useRef('');
  const micVersion = useRef(0), screenVersion = useRef(0);
  const [connected, setConnected] = useState(false), [attempt, setAttempt] = useState(0);
  const [status, setStatus] = useState('Đang kết nối phòng học…');
  const [sharing, setSharing] = useState(false), [micOn, setMicOn] = useState(false);
  const [shareBusy, setShareBusy] = useState(false), [micBusy, setMicBusy] = useState(false);
  const [listening, setListening] = useState(false), [image, setImage] = useState('');
  const [chat, setChat] = useState<{ username: string; text: string }[]>([]), [draft, setDraft] = useState('');
  const [controls, setControls] = useState<Controls | null>(null);
  const permissions = useRef<Controls | null>(null), receivedAudio = useRef(0), sentAudio = useRef(0);
  const [diagnostics, setDiagnostics] = useState('');
  const me = controls?.participants.find(p => p.peerId === controls.peerId);
  const canShare = owned || (!!controls && controls.presenter === controls.peerId);
  const canSpeak = owned || !!me?.micAllowed;
  function command(type: string, extra: Record<string, unknown> = {}) {
    if (socket.current?.readyState === WebSocket.OPEN) socket.current.send(JSON.stringify({ type, ...extra }));
  }

  function replaceImage(url: string) {
    if (frameUrl.current) URL.revokeObjectURL(frameUrl.current);
    frameUrl.current = url; setImage(url);
  }
  function stopScreen(notify = true) {
    ++screenVersion.current;
    window.clearTimeout(frameTimer.current); frameTimer.current = undefined;
    const previous = screen.current; screen.current = null;
    previous?.getTracks().forEach(track => track.stop()); setSharing(false); setShareBusy(false);
    if (notify && socket.current?.readyState === WebSocket.OPEN)
      socket.current.send(JSON.stringify({ type: 'screen-stop' }));
  }
  function stopMic() {
    ++micVersion.current;
    microphone.current?.getTracks().forEach(track => track.stop()); microphone.current = null;
    const old = capture.current; capture.current = null;
    if (old && old.state !== 'closed') void old.close();
    setMicOn(false); setMicBusy(false);
  }
  function stopListening() {
    const old = playback.current; playback.current = null;
    if (old && old.context.state !== 'closed') void old.context.close();
    setListening(false);
  }
  async function listen() {
    const current = generation.current;
    if (playback.current?.context.state === 'running') return;
    if (playback.current && playback.current.context.state !== 'closed') {
      await playback.current.context.resume(); setListening(true); return;
    }
    const context = new AudioContext();
    await context.resume();
    if (current !== generation.current) { await context.close(); return; }
    playback.current = { context, times: new Map(), sequences: new Map() }; setListening(true);
    context.onstatechange = () => { if (playback.current?.context === context) setListening(context.state === 'running'); };
  }
  function playPacket(data: ArrayBuffer) {
    if (data.byteLength !== 653 || !playback.current) return;
    const view = new DataView(data); if (view.getUint8(0) !== AUDIO) return;
    ++receivedAudio.current;
    const { context, times, sequences } = playback.current;
    if (context.state !== 'running') return;
    const sender = view.getBigUint64(1).toString(), seq = view.getUint32(9);
    const oldSeq = sequences.get(sender);
    if (oldSeq !== undefined && seq <= oldSeq && oldSeq - seq < 0x80000000) return;
    sequences.set(sender, seq);
    const buffer = context.createBuffer(1, 320, 16000), samples = buffer.getChannelData(0);
    for (let i = 0; i < 320; i++) samples[i] = view.getInt16(13 + i * 2, true) / 32768;
    const now = context.currentTime;
    let start = Math.max(times.get(sender) ?? now + 0.04, now + 0.01);
    // Drop accumulated latency after tab suspension or a slow network.
    if (start > now + 0.16) start = now + 0.04;
    const source = context.createBufferSource(); source.buffer = buffer; source.connect(context.destination);
    source.onended = () => source.disconnect(); source.start(start); times.set(sender, start + 0.02);
  }
  useEffect(() => {
    const current = ++generation.current;
    setConnected(false); setChat([]); replaceImage(''); setStatus('Đang kết nối phòng học…');
    setControls(null); permissions.current = null; receivedAudio.current = 0; sentAudio.current = 0;
    let lastMessage = Date.now();
    const timer = window.setInterval(() => {
      command('ping');
      setDiagnostics(`Mic: ${capture.current?.state ?? 'tắt'} · Loa: ${playback.current?.context.state ?? 'tắt'} · Gói gửi/nhận: ${sentAudio.current}/${receivedAudio.current}`);
      if (socket.current?.readyState === WebSocket.OPEN && Date.now() - lastMessage > 45_000) { socket.current.close(); setStatus('Media không phản hồi. Hãy kết nối lại.'); }
    }, 5000);
    void (async () => {
      try {
        const reply = await api('/media/ticket', 'POST', undefined, token);
        if (generation.current !== current) return;
        const ws = new WebSocket(location.origin.replace(/^http/, 'ws') + '/ws/classroom?ticket=' + encodeURIComponent(reply.ticket!));
        socket.current = ws; ws.binaryType = 'arraybuffer';
        ws.onopen = () => ws.send(JSON.stringify({ type: 'join', roomId }));
        ws.onmessage = event => {
          if (generation.current !== current) return;
          lastMessage = Date.now();
          if (typeof event.data === 'string') {
            try {
              const message = JSON.parse(event.data);
              if (message.type === 'controls') {
                const next = message as Controls; permissions.current = next; setControls(next);
                const self = next.participants.find(p => p.peerId === next.peerId);
                if (!self?.micAllowed) stopMic();
                if (next.presenter !== next.peerId) stopScreen(false);
                const active = new Set(next.participants.map(p => p.sourceId));
                playback.current?.times.forEach((_, id) => { if (!active.has(id)) { playback.current?.times.delete(id); playback.current?.sequences.delete(id); } });
              }
              if (message.type === 'joined') { setConnected(true); setStatus('Đã kết nối lớp học.'); }
              if (message.type === 'chat') setChat(items => [...items, message].slice(-100));
              if (message.type === 'screen-stop') replaceImage('');
              if (message.type === 'presence') setStatus(message.username + (message.state === 'joined' ? ' đã vào lớp.' : ' đã rời lớp.'));
              if (message.type === 'error') setStatus(message.message);
            } catch { setStatus('Máy chủ gửi dữ liệu không hợp lệ.'); }
          } else {
            const bytes = event.data as ArrayBuffer;
            if (!bytes.byteLength) return;
            if (new DataView(bytes).getUint8(0) === SCREEN) replaceImage(URL.createObjectURL(new Blob([bytes.slice(1)], { type: 'image/jpeg' })));
            else playPacket(bytes);
          }
        };
        ws.onclose = event => {
          if (generation.current !== current) return;
          setConnected(false); stopScreen(false); stopMic(); stopListening(); replaceImage('');
          setStatus(`Media đã ngắt (mã ${event.code}). ${event.code === 1008 ? 'Kiểm tra phiên đăng nhập và quyền thành viên.' : 'Kiểm tra mạng/server rồi kết nối lại.'}`);
        };
        ws.onerror = () => { if (generation.current === current) setStatus('Không thể kết nối máy chủ media.'); };
      } catch (error) { if (generation.current === current) setStatus(error instanceof Error ? error.message : 'Không kết nối được lớp.'); }
    })();
    return () => {
      window.clearInterval(timer);
      ++generation.current; stopScreen(false); stopMic(); stopListening(); replaceImage('');
      socket.current?.close(); socket.current = null;
    };
  }, [roomId, token, attempt]);

  async function startScreen() {
    if (!canShare || !connected || shareBusy) return;
    const version = ++screenVersion.current; setShareBusy(true);
    let stream: MediaStream | undefined;
    try {
      const options = {
        video: { displaySurface: 'window', frameRate: { ideal: 8 }, width: { ideal: 1280 }, height: { ideal: 720 } },
        audio: false, selfBrowserSurface: 'exclude', monitorTypeSurfaces: 'exclude', surfaceSwitching: 'exclude'
      } as DisplayMediaStreamOptions;
      stream = await navigator.mediaDevices.getDisplayMedia(options);
      if (version !== screenVersion.current || socket.current?.readyState !== WebSocket.OPEN) { stream.getTracks().forEach(t => t.stop()); return; }
      const track = stream.getVideoTracks()[0];
      if (track.getSettings().displaySurface === 'monitor') {
        stream.getTracks().forEach(t => t.stop());
        setStatus('Hãy chọn một cửa sổ hoặc tab bài giảng. Chia sẻ toàn desktop có thể tạo phản chiếu.'); return;
      }
      screen.current = stream; track.onended = () => stopScreen();
      command('screen-start');
      const video = document.createElement('video'); video.srcObject = stream; video.muted = true; await video.play();
      const canvas = document.createElement('canvas'), ctx = canvas.getContext('2d');
      const nextFrame = async () => {
        if (version !== screenVersion.current || screen.current !== stream || !ctx) return;
        const ws = socket.current;
        if (ws?.readyState === WebSocket.OPEN && ws.bufferedAmount < 128_000 && video.videoWidth) {
          const scale = Math.min(1, 1280 / video.videoWidth, 720 / video.videoHeight);
          canvas.width = Math.round(video.videoWidth * scale); canvas.height = Math.round(video.videoHeight * scale);
          ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
          const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.66));
          if (blob && blob.size <= 1_500_000 && version === screenVersion.current) {
            const bytes = await blob.arrayBuffer(), packet = new Uint8Array(bytes.byteLength + 1);
            packet[0] = SCREEN; packet.set(new Uint8Array(bytes), 1);
            if (ws.readyState === WebSocket.OPEN && version === screenVersion.current) ws.send(packet);
          }
        }
        if (version === screenVersion.current) frameTimer.current = window.setTimeout(() => void nextFrame(), 125);
      };
      setSharing(true); setStatus('Đang chia sẻ bài giảng.'); void nextFrame();
    } catch (error) {
      stream?.getTracks().forEach(t => t.stop());
      setStatus(error instanceof DOMException && error.name === 'NotAllowedError' ? 'Bạn đã hủy hoặc chưa cho phép chia sẻ.' : 'Không chia sẻ được. Hãy dùng Chrome/Edge trên HTTPS hoặc localhost.');
    } finally { if (version === screenVersion.current) setShareBusy(false); }
  }
  async function startMic() {
    if (!connected || !canSpeak || micBusy) return;
    const version = ++micVersion.current; setMicBusy(true);
    let stream: MediaStream | undefined, context: AudioContext | undefined;
    try {
      // Resume playback during the user's click; microphone permission is independent.
      const listeningPromise = listen();
      stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }, video: false });
      await listeningPromise;
      if (version !== micVersion.current) { stream.getTracks().forEach(t => t.stop()); return; }
      context = new AudioContext(); await context.audioWorklet.addModule('/audio-capture.js');
      if (version !== micVersion.current) { stream.getTracks().forEach(t => t.stop()); await context.close(); return; }
      microphone.current = stream; capture.current = context;
      const node = new AudioWorkletNode(context, 'classroom-capture');
      node.port.onmessage = event => {
        const rights = permissions.current;
        if (!owned && !rights?.participants.find(p => p.peerId === rights.peerId)?.micAllowed) return;
        const ws = socket.current; if (version !== micVersion.current || ws?.readyState !== WebSocket.OPEN || ws.bufferedAmount > 64_000) return;
        const samples = event.data as Float32Array; if (samples.length !== 320) return;
        const packet = new ArrayBuffer(645), view = new DataView(packet); view.setUint8(0, AUDIO); view.setUint32(1, sequence.current++);
        for (let i = 0; i < 320; i++) view.setInt16(5 + 2 * i, Math.round(Math.max(-1, Math.min(1, samples[i])) * 32767), true);
        ws.send(packet);
        ++sentAudio.current;
      };
      const mute = context.createGain(); mute.gain.value = 0;
      context.createMediaStreamSource(stream).connect(node); node.connect(mute); mute.connect(context.destination);
      await context.resume();
      if (version !== micVersion.current) { stream.getTracks().forEach(t => t.stop()); await context.close(); return; }
      context.onstatechange = () => { if (capture.current === context && context?.state !== 'running') { stopMic(); setStatus('Mic bị trình duyệt tạm dừng. Bật mic lại để phục hồi.'); } };
      stream.getAudioTracks()[0].onended = stopMic;
      setMicOn(true); setStatus('Mic đang bật. Dùng tai nghe để tránh vọng âm.');
    } catch { stream?.getTracks().forEach(t => t.stop()); if (context && context.state !== 'closed') void context.close(); setStatus('Không bật được mic. Kiểm tra quyền và thiết bị âm thanh.'); }
    finally { if (version === micVersion.current) setMicBusy(false); }
  }
  function sendChat(event: React.FormEvent) {
    event.preventDefault(); const text = draft.trim();
    if (text && connected && socket.current?.readyState === WebSocket.OPEN) { socket.current.send(JSON.stringify({ type: 'chat', text })); setDraft(''); }
  }
  return <div className="classroom-media">
    <div className="media-controls">
      <span className="connection-state" role="status"><i className={connected ? 'connected' : ''} />{status}</span>
      {!connected && <button className="ghost-button" onClick={() => setAttempt(x => x + 1)}>Kết nối lại</button>}
      {canShare && <button className="primary-button" disabled={!connected || shareBusy} onClick={() => sharing ? stopScreen() : void startScreen()}>{shareBusy ? 'Đang chọn nguồn…' : sharing ? 'Dừng chia sẻ' : 'Chia sẻ bài giảng'}</button>}
      {!owned && !canShare && <button className="ghost-button" disabled={!connected} onClick={() => command(me?.screenRequested ? 'screen-cancel' : 'screen-request')}>{me?.screenRequested ? 'Hủy yêu cầu chia sẻ' : 'Xin chia sẻ màn hình'}</button>}
      <button className="ghost-button" disabled={!connected || micBusy || !canSpeak} aria-pressed={micOn} onClick={() => micOn ? stopMic() : void startMic()}>{!canSpeak ? 'Mic đang bị khóa' : micBusy ? 'Đang mở mic…' : micOn ? 'Tắt mic' : 'Bật mic'}</button>
      {!canSpeak && <button className="ghost-button" disabled={!connected || me?.micRequested} onClick={() => command('mic-request')}>{me?.micRequested ? 'Đang chờ cho phép phát biểu' : 'Xin phát biểu'}</button>}
      <button className="ghost-button" disabled={!connected} aria-pressed={listening} onClick={() => listening ? stopListening() : void listen().catch(() => setStatus('Không mở được loa.'))}>{listening ? 'Tắt loa' : 'Bật loa'}</button>
    </div>
    <p className="password-hint" aria-live="polite">{diagnostics}</p>
    {owned && <section className="media-panel"><h3>Điều hành buổi học</h3>
      <button className="ghost-button" disabled={!connected} onClick={() => command('mic-lock', { locked: !controls?.microphonesLocked })}>{controls?.microphonesLocked ? 'Mở quyền mic cả lớp' : 'Khóa mic học viên cả lớp'}</button>
      {!!controls?.presenter && <button className="ghost-button" onClick={() => command('screen-revoke')}>Dừng người đang chia sẻ</button>}
      {controls?.participants.filter(p => !p.owner).map(p => <p key={p.peerId}><strong>{p.username}</strong> · {p.micAllowed ? 'Được bật mic' : 'Mic bị khóa'} · {controls.presenter === p.peerId ? 'Có quyền chia sẻ' : 'Chưa được chia sẻ'}
        {p.screenRequested && <><button className="ghost-button" onClick={() => command('screen-review', { peerId: p.peerId, approve: true })}>Duyệt chia sẻ</button><button className="ghost-button" onClick={() => command('screen-review', { peerId: p.peerId, approve: false })}>Từ chối chia sẻ</button></>}
        {controls.microphonesLocked && <button className="ghost-button" onClick={() => command('mic-review', { peerId: p.peerId, approve: !p.micAllowed })}>{p.micAllowed ? 'Thu hồi quyền mic' : p.micRequested ? 'Duyệt phát biểu' : 'Cho phép bật mic'}</button>}
      </p>)}
      <p className="password-hint">Mở quyền mic không tự bật micro của học viên. Mỗi người tự chọn bật mic và nguồn chia sẻ. Quyền cấp riêng hết hiệu lực khi người đó rời phòng.</p>
    </section>}
    <div className="media-grid">
      <section className="media-panel"><h3>Bài giảng trực tiếp</h3>
        <div className="screen-placeholder">{image && !sharing ? <img className="shared-screen" src={image} alt="Màn hình đang được chia sẻ" /> : <div><span className="stage-symbol">▣</span><strong>{sharing ? 'Bài giảng đang được chia sẻ' : owned ? 'Sẵn sàng cho buổi học' : 'Đang chờ người trình bày chia sẻ'}</strong><p>{owned ? 'Chọn cửa sổ hoặc tab tài liệu. Bản xem trước được ẩn để tránh phản chiếu.' : 'Bật loa để nghe bài giảng. Chỉ bật mic khi bạn cần phát biểu.'}</p></div>}</div>
        {owned && <p className="password-hint">Chọn đúng cửa sổ bài giảng trong hộp thoại của trình duyệt; không chọn cửa sổ lớp học.</p>}
      </section>
      <section className="media-panel chat-panel"><h3>Trao đổi trong lớp</h3>
        <div className="chat-messages" aria-live="polite">{chat.length === 0 && <p className="chat-empty">Câu hỏi và trao đổi sẽ xuất hiện tại đây.</p>}{chat.map((message, i) => <p key={i}><strong>{message.username}</strong><span>{message.text}</span></p>)}</div>
        <form onSubmit={sendChat}><input aria-label="Tin nhắn lớp học" value={draft} onChange={e => setDraft(e.target.value)} maxLength={500} placeholder="Nhập câu hỏi…" /><button className="ghost-button" disabled={!connected || !draft.trim()}>Gửi</button></form>
      </section>
    </div>
  </div>;
}
