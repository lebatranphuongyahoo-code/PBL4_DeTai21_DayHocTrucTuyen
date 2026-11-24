export type ScreenPacing = { fps: number; clearSince: number | null };

export function adjustScreenRate(fps: number, bufferedBytes: number, clearSince: number | null, now: number): ScreenPacing {
  if (bufferedBytes > 96_000) return { fps: Math.max(2, Math.floor(fps / 2)), clearSince: null };
  if (bufferedBytes >= 24_000 || fps >= 8) return { fps, clearSince: null };

  const started = clearSince ?? now;
  return now - started >= 3_000
    ? { fps: fps + 1, clearSince: now }
    : { fps, clearSince: started };
}
