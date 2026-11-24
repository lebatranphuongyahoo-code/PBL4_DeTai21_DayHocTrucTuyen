export type User = { username: string; displayName: string; role: string };
export type Room = { id: number; name: string; members: number; owned: boolean };
export type Member = { id: number; username: string; displayName: string; role: string };
export type Detail = { id: number; name: string; owner: string; owned: boolean; inviteCode: string | null; members: Member[]; meetingOpen: boolean; meetingId: number | null };
export type JoinRequest = { accountId: number; username: string; displayName: string };
export type JoinStatus = { roomId: number; roomName: string; status: string };
export type MeetingHistory = { id: number; startedAt: number; endedAt: number | null; startUncertain: boolean; endReason: string | null; connections: { id: number; username: string; displayName: string; joinedAt: number; leftAt: number | null; interrupted: boolean }[] };
export type ChatMessage = { id: number; username: string; text: string; sentAt: number; meetingId: number | null };
export type MeetingHistoryReply = { ok: boolean; meetings?: MeetingHistory[]; hasLegacyChat?: boolean };
export type ActivitySubmission = { accountId: number; username: string; displayName: string; answer: string | null; submittedAt: number | null; score: number | null; feedback: string | null; reviewedAt: number | null };
export type Activity = { id: number; title: string; prompt: string; createdAt: number; submissionCount: number; submission: ActivitySubmission | null };
export type Reply = { ok: boolean; message?: string; errors?: Record<string,string>; user?: User; token?: string; rooms?: Room[]; room?: Detail; requests?: JoinRequest[]; joinStatuses?: JoinStatus[]; meetings?: MeetingHistory[]; messages?: ChatMessage[]; activities?: Activity[]; activity?: Activity; submissions?: ActivitySubmission[]; submission?: ActivitySubmission; ticket?: string };
export type AdminRoom = { id: number; name: string; members: number; ownerId: number; ownerUsername: string; ownerDisplayName: string; status: 'ACTIVE' | 'ARCHIVED' };
export type AdminUser = { id: number; username: string; displayName: string; role: 'STUDENT' | 'TEACHER' | 'ADMIN'; createdAt: number };
export type AdminRoomDetail = Omit<AdminRoom, 'members'> & { meetingOpen: boolean; members: Member[] };
export type AdminReply = { ok: boolean; message?: string; errors?: Record<string,string>; rooms?: AdminRoom[]; room?: AdminRoomDetail; users?: AdminUser[]; roomId?: number };
export async function api<T = Reply>(path: string, method = 'GET', body?: unknown, token?: string): Promise<T> {
  const response = await fetch('/api'+path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer '+token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const data: Reply = await response.json();
  if(response.status===401&&token)window.dispatchEvent(new Event('pbl4-session-expired'));
  if (!response.ok) throw new Error(Object.values(data.errors ?? {}).join(' ') || data.message || 'Yêu cầu thất bại.');
  return data as T;
}

