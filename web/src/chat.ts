import type { ChatMessage } from './api';

export function mergeChat(current: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
  return [...new Map([...current, ...incoming].map(message => [message.id, message])).values()]
    .sort((a, b) => a.id - b.id);
}
