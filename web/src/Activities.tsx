import React, { useEffect, useState } from 'react';
import { Activity, ActivitySubmission, api } from './api';

function Submission({ item, teacher, onReview }: { item: ActivitySubmission; teacher: boolean; onReview: (item: ActivitySubmission) => void }) {
  const [score, setScore] = useState(item.score == null ? '' : String(item.score));
  const [feedback, setFeedback] = useState(item.feedback ?? '');
  return <article className="activity-submission">
    <div className="activity-submission-heading"><strong>{item.displayName}</strong><span>@{item.username}</span></div>
    {item.answer ? <p className="activity-answer">{item.answer}</p> : <p className="activity-muted">Chưa nộp câu trả lời.</p>}
    {teacher && item.answer && <form className="activity-review" onSubmit={event => { event.preventDefault(); onReview({ ...item, score: score === '' ? null : Number(score), feedback }); }}>
      <label>Điểm (0–10)<input type="number" min="0" max="10" step="1" value={score} onChange={event => setScore(event.target.value)} /></label>
      <label>Phản hồi<textarea maxLength={2000} value={feedback} onChange={event => setFeedback(event.target.value)} /></label>
      <button className="ghost-button" type="submit">Lưu phản hồi</button>
    </form>}
    {!teacher && item.score != null && <p className="activity-feedback"><strong>{item.score}/10</strong>{item.feedback ? ' · '+item.feedback : ''}</p>}
  </article>;
}

export function Activities({ roomId, token, teacher }: { roomId: number; token: string; teacher: boolean }) {
  const [items, setItems] = useState<Activity[]>([]), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const [title, setTitle] = useState(''), [prompt, setPrompt] = useState('');
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const [submissions, setSubmissions] = useState<Record<number, ActivitySubmission[]>>({});
  async function load() {
    setBusy(true); setError('');
    try {
      const reply = await api<{ activities?: Activity[] }>('/rooms/'+roomId+'/activities', 'GET', undefined, token);
      setItems(reply.activities ?? []);
      if (teacher) await Promise.all((reply.activities ?? []).map(async item => {
        const result = await api<{ submissions?: ActivitySubmission[] }>('/rooms/'+roomId+'/activities/'+item.id+'/submissions', 'GET', undefined, token);
        setSubmissions(current => ({ ...current, [item.id]: result.submissions ?? [] }));
      }));
    } catch (e) { setError(e instanceof Error ? e.message : 'Không tải được bài tập.'); }
    finally { setBusy(false); }
  }
  useEffect(() => { void load(); }, [roomId, token, teacher]);
  async function create(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try { await api('/rooms/'+roomId+'/activities', 'POST', { title, prompt }, token); setTitle(''); setPrompt(''); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : 'Không tạo được bài tập.'); setBusy(false); }
  }
  async function submit(item: Activity) {
    const answer = (answers[item.id] ?? '').trim(); if (!answer) return;
    setBusy(true); setError('');
    try { const reply = await api<{ submission?: ActivitySubmission }>('/rooms/'+roomId+'/activities/'+item.id+'/submissions', 'POST', { answer }, token); setItems(current => current.map(value => value.id===item.id ? { ...value, submission: reply.submission ?? null } : value)); }
    catch (e) { setError(e instanceof Error ? e.message : 'Không nộp được câu trả lời.'); }
    finally { setBusy(false); }
  }
  async function review(item: Activity, submission: ActivitySubmission) {
    setBusy(true); setError('');
    try { const reply = await api<{ submission?: ActivitySubmission }>('/rooms/'+roomId+'/activities/'+item.id+'/submissions/'+submission.accountId+'/review', 'POST', { score: submission.score, feedback: submission.feedback }, token); setSubmissions(current => ({ ...current, [item.id]: (current[item.id] ?? []).map(value => value.accountId===submission.accountId ? (reply.submission ?? submission) : value) })); }
    catch (e) { setError(e instanceof Error ? e.message : 'Không lưu được phản hồi.'); }
    finally { setBusy(false); }
  }
  return <section className="activities-panel" aria-label="Bài tập tương tác" aria-busy={busy}><div className="section-heading"><h2>Bài tập tương tác</h2><button className="ghost-button" disabled={busy} onClick={() => void load()}>Làm mới</button></div>
    {busy && <p className="activity-muted" role="status" aria-live="polite">Đang tải hoặc cập nhật bài tập…</p>}
    {error && <p className="form-message" role="alert">{error}</p>}
    {teacher && <form className="activity-create" onSubmit={create}><label>Tiêu đề<input required minLength={2} maxLength={120} value={title} onChange={event => setTitle(event.target.value)} /></label><label>Nội dung bài tập<textarea required maxLength={2000} value={prompt} onChange={event => setPrompt(event.target.value)} /></label><button className="primary-button" type="submit" disabled={busy}>Đăng bài tập</button></form>}
    {!busy && !items.length && <p className="activity-muted">Chưa có bài tập nào.</p>}
    <div className="activities-list">{items.map(item => <article className="activity-card" key={item.id}><div><h3>{item.title}</h3><p>{item.prompt}</p></div>{teacher ? <div className="activity-submissions">{(submissions[item.id] ?? []).map(submission => <Submission key={submission.accountId} item={submission} teacher onReview={next => void review(item, next)} />)}</div> : <div className="activity-student"><textarea aria-label={'Câu trả lời '+item.title} maxLength={5000} value={answers[item.id] ?? item.submission?.answer ?? ''} onChange={event => setAnswers(current => ({ ...current, [item.id]: event.target.value }))} placeholder="Viết câu trả lời của bạn…" /><button className="ghost-button" disabled={busy} onClick={() => void submit(item)}>Nộp câu trả lời</button>{item.submission && <Submission item={item.submission} teacher={false} onReview={() => {}} />}</div>}</article>)}</div>
  </section>;
}
