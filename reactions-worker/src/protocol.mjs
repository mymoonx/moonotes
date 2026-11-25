const ROOM = /^[a-f0-9]{64}$/;
const PLAYER_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const PLAYER_NAME = /^[A-Za-z0-9_]{3,16}$/;
const EMOTE_ID = /^(?:[0-9A-HJKMNP-TV-Z]{26}|[a-f0-9]{16,40})$/i;
const MAX_MSG_BYTES = 1024;
const MAX_CONNECTIONS = 12;
const MIN_REACTION_INTERVAL_MS = 800;
const MAX_REACTIONS_PER_10S = 8;

export function isRoomKey(value) { return typeof value === 'string' && ROOM.test(value); }
export function validPlayer(id, name) {
  return typeof id === 'string' && PLAYER_ID.test(id)
    && typeof name === 'string' && PLAYER_NAME.test(name);
}
export function validEmote(id, name) {
  return typeof id === 'string' && EMOTE_ID.test(id)
    && typeof name === 'string' && name.length >= 2 && name.length <= 80
    && !/[\s\p{Cc}\p{Cf}§/]/u.test(name);
}
export function parseFrame(value) {
  if (typeof value !== 'string' || new TextEncoder().encode(value).length > MAX_MSG_BYTES) return null;
  try {
    const data = JSON.parse(value);
    if (!data || Array.isArray(data) || typeof data !== 'object') return null;
    if (data.type === 'join' && validPlayer(data.uuid, data.name))
      return { type: 'join', uuid: data.uuid.toLowerCase(), name: data.name };
    if (data.type === 'reaction' && validEmote(data.id, data.name))
      return { type: 'reaction', id: data.id.toLowerCase(), name: data.name };
  } catch { /* Invalid frames never enter the room. */ }
  return null;
}
export function rateAllowed(state, now) {
  if (now - (state.lastAt ?? 0) < MIN_REACTION_INTERVAL_MS) return false;
  const windowStart = now - (state.windowStart ?? 0) >= 10000 ? now : state.windowStart;
  const count = windowStart === now ? 0 : (state.count ?? 0);
  if (count >= MAX_REACTIONS_PER_10S) return false;
  state.lastAt = now;
  state.windowStart = windowStart;
  state.count = count + 1;
  return true;
}
export const LIMITS = { MAX_CONNECTIONS, MAX_MSG_BYTES };
