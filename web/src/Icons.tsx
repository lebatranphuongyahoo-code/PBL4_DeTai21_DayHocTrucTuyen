import React from 'react';

export function Icon({ name }: { name: 'home' | 'book' | 'screen' | 'mic' | 'volume' | 'leave' | 'send' | 'expand' | 'users' | 'settings' }) {
  const paths: Record<string, React.ReactNode> = {
    home: <><path d="m3 10 9-7 9 7v11H3Z"/><path d="M9 21v-8h6v8"/></>,
    book: <><path d="M12 5c-3-2-6-2-10-1v16c4-1 7-1 10 1 3-2 6-2 10-1V4c-4-1-7-1-10 1Z"/><path d="M12 5v16"/></>,
    screen: <><rect x="2" y="3" width="20" height="14" rx="2"/><path d="M12 17v4m-5 0h10m-5-9V6m-3 3 3-3 3 3"/></>,
    mic: <><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3m-4 0h8"/></>,
    volume: <><path d="M11 4 6 8H2v8h4l5 4ZM15 8a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14"/></>,
    leave: <><path d="M4 9V3h12v18H4v-6m5-3h13m-4-4 4 4-4 4"/></>,
    send: <><path d="m22 2-7 20-4-9-9-4Z"/><path d="m11 13 11-11"/></>,
    expand: <path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5"/>,
    users: <><circle cx="9" cy="7" r="4"/><path d="M2 21v-3a7 7 0 0 1 14 0v3m1-18a4 4 0 0 1 0 8m1 4a6 6 0 0 1 4 6"/></>,
    settings: <><path d="M4 5h16M4 12h16M4 19h16"/><circle cx="9" cy="5" r="2"/><circle cx="15" cy="12" r="2"/><circle cx="8" cy="19" r="2"/></>,
  };
  return <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
}
