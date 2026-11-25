/**
 * Moonotes reactions beta relay. This Worker is deliberately separate from the
 * existing read-only Moonotes 7TV cache Worker. No chat text is transported or
 * persisted. Room addresses are SHA-256 room capabilities, never raw IPs/codes.
 *
 * IMPORTANT: A room code grants access, NOT Minecraft identity verification.
 * Players must exchange codes privately with trusted friends.
 */
import { isRoomKey, parseFrame, rateAllowed, LIMITS } from './protocol.mjs';

const VERSION = '0.6.2-beta.1';
const HEADERS = {
  'content-type': 'application/json; charset=utf-8',
  'cache-control': 'no-store',
  'x-content-type-options': 'nosniff',
};
function answer(code, status = 200) {
  return new Response(JSON.stringify({ error: code, version: VERSION }),
    { status, headers: HEADERS });
}

export class ReactionRoom {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  async fetch(request) {
    if (request.headers.get('Upgrade')?.toLowerCase() !== 'websocket')
      return answer('websocket_required', 426);
    if (this.ctx.getWebSockets().length >= LIMITS.MAX_CONNECTIONS)
      return answer('room_full', 429);
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ joined: false, uuid: '', name: '', lastAt: 0,
      windowStart: 0, count: 0 });
    return new Response(null, { status: 101, webSocket: client });
  }

  webSocketMessage(socket, raw) {
    const state = socket.deserializeAttachment();
    if (!state || typeof raw !== 'string') {
      socket.close(1003, 'text_only');
      return;
    }
    const data = parseFrame(raw);
    if (!data) {
      socket.send(JSON.stringify({ type: 'error', reason: 'invalid_frame' }));
      return;
    }
    if (!state.joined) {
      if (data.type !== 'join') {
        socket.close(1008, 'join_required');
        return;
      }
      // One live connection per declared UUID within the shared secret room.
      // This prevents accidental duplicates but does NOT verify game identity.
      if (this.ctx.getWebSockets().some(peer => peer !== socket
        && peer.deserializeAttachment()?.joined
        && peer.deserializeAttachment()?.uuid === data.uuid)) {
        socket.send(JSON.stringify({ type: 'error', reason: 'duplicate_player' }));
        socket.close(1008, 'duplicate_player');
        return;
      }
      state.joined = true;
      state.uuid = data.uuid;
      state.name = data.name;
      socket.serializeAttachment(state);
      socket.send(JSON.stringify({ type: 'ready', version: VERSION,
        peers: this.memberCount() - 1 }));
      this.broadcast({ type: 'peers', count: this.memberCount() }, null);
      return;
    }
    if (data.type !== 'reaction') return; // Cannot change identity after join.
    const now = Date.now();
    if (!rateAllowed(state, now)) {
      socket.send(JSON.stringify({ type: 'error', reason: 'too_fast' }));
      return;
    }
    socket.serializeAttachment(state);
    this.broadcast({ type: 'reaction', uuid: state.uuid, player: state.name,
      id: data.id, name: data.name, duration: 4 }, socket);
  }

  memberCount() {
    return this.ctx.getWebSockets().filter(socket =>
      socket.deserializeAttachment()?.joined).length;
  }

  broadcast(data, exclude) {
    const encoded = JSON.stringify(data);
    for (const socket of this.ctx.getWebSockets()) {
      if (socket !== exclude && socket.deserializeAttachment()?.joined) {
        try { socket.send(encoded); } catch { /* closed socket: next event removes it */ }
      }
    }
  }

  webSocketClose(socket, code, reason, clean) {
    this.broadcast({ type: 'peers', count: Math.max(0, this.memberCount() - 1) }, socket);
    try { socket.close(1000, 'closed'); } catch { }
  }
  webSocketError(socket) {
    this.broadcast({ type: 'peers', count: Math.max(0, this.memberCount() - 1) }, socket);
    try { socket.close(1011, 'socket_error'); } catch { }
  }
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (request.method === 'GET' && url.pathname === '/health' && !url.search) {
      return new Response(JSON.stringify({ status: 'ok', version: VERSION,
        relay: 'durable-object-websocket', roomAuth: 'shared-capability-beta' }),
        { status: 200, headers: HEADERS });
    }
    if (request.method !== 'GET') return answer('method_not_allowed', 405);
    if (url.search) return answer('invalid_query', 400);
    const match = /^\/v1\/room\/([a-f0-9]{64})$/.exec(url.pathname);
    if (!match || !isRoomKey(match[1])) return answer('not_found', 404);
    if (request.headers.get('Upgrade')?.toLowerCase() !== 'websocket')
      return answer('websocket_required', 426);
    if (!env.ROOMS) return answer('room_backend_unavailable', 503);
    const stub = env.ROOMS.get(env.ROOMS.idFromName(match[1]));
    return stub.fetch(request);
  },
};
