export type User = { username: string; displayName: string; role: string };
export type Room = { id: number; name: string; members: number; owned: boolean };
export type Member = { id: number; username: string; displayName: string; role: string };
export type Detail = { id: number; name: string; owner: string; owned: boolean; inviteCode: string | null; members: Member[]; meetingOpen: boolean };
export type JoinRequest = { accountId: number; username: string; displayName: string };
export type JoinStatus = { roomId: number; roomName: string; status: string };
export type Reply = { ok: boolean; message?: string; errors?: Record<string,string>; user?: User; token?: string; rooms?: Room[]; room?: Detail; requests?: JoinRequest[]; joinStatuses?: JoinStatus[]; ticket?: string };
export async function api(path: string, method = 'GET', body?: unknown, token?: string): Promise<Reply> {
  const response = await fetch('/api'+path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer '+token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const data: Reply = await response.json();
  if(response.status===401&&token)window.dispatchEvent(new Event('pbl4-session-expired'));
  if (!response.ok) throw new Error(Object.values(data.errors ?? {}).join(' ') || data.message || 'Yêu cầu thất bại.');
  return data;
}

