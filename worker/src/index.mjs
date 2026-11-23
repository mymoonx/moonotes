/** Moonotes R2 0.5.3. Private R2; public, bounded 7TV-only catalogue/image reads.
 * Existing v0kky objects and image keys remain compatible with older clients.
 * No arbitrary URL proxy, account token, telemetry endpoint or public upload API.
 */
const VERSION = '0.5.3';
const MAX_IMAGE_BYTES = 3 * 1024 * 1024;
const MAX_UPSTREAM_JSON_BYTES = 8 * 1024 * 1024;
const MAX_MANIFEST_BYTES = 1024 * 1024;
const MAX_EMOTES = 2000;
const UPSTREAM_TIMEOUT_MS = 12000;
const ID = /^(?:[0-9A-HJKMNP-TV-Z]{26}|[a-f0-9]{16,40})$/i;
const CHANNEL_PATH = /^\/v1\/7tv\/channel\/([a-z0-9_]{1,25})(?:\.json)?$/i;
const SET_PATH = /^\/v1\/7tv\/set\/([a-z0-9]{16,40})(?:\.json)?$/i;
const IMAGE_PATH = /^\/v1\/7tv\/([a-z0-9]{16,40})\/(1x|2x|3x|4x)\.webp$/i;
const MANIFEST_TTL_MS = 12 * 60 * 60 * 1000;
const IMAGE_TTL_SECONDS = 60 * 60 * 24 * 30;
const JSON_HEADERS = {
  'content-type': 'application/json; charset=utf-8',
  'cache-control': 'no-store',
  'x-content-type-options': 'nosniff',
  'x-moonotes-version': VERSION,
};
const decoder = new TextDecoder('utf-8', { fatal: true });
const encoder = new TextEncoder();
// Per-isolate only, not a global rate limit. No cross-request I/O promises are
// shared: a second cold request gets a short Retry-After while a fill is busy.
const filling = new Set();
const retryAfter = new Map();
const MAX_TRACKED_KEYS = 128;

class SourceError extends Error {
  constructor(code, status = 502, retry = 0, upstreamStatus = 0, stage = "") {
    super(code); this.code = code; this.status = status; this.retry = retry;
    this.upstreamStatus = upstreamStatus; this.stage = stage;
  }
}

function json(obj, status = 200, request, extra = {}) {
  return new Response(request?.method === 'HEAD' ? null : JSON.stringify(obj), {
    status, headers: { ...JSON_HEADERS, ...extra },
  });
}

function failure(error, request) {
  const known = error instanceof SourceError;
  return json({ error: known ? error.code : 'gateway_unavailable',
    ...(known && error.stage ? { stage: error.stage } : {}),
    ...(known && error.upstreamStatus ? { upstreamStatus: error.upstreamStatus } : {}), version: VERSION }, known ? error.status : 503,
    request, known && error.retry ? { 'retry-after': String(error.retry) } : {});
}

async function atStage(stage, operation) {
  try { return await operation(); }
  catch (error) { if (error instanceof SourceError) { if (!error.stage) error.stage = stage; throw error; }
    throw new SourceError('gateway_unavailable', 503, 15, 0, stage); }
}

function upstreamRetry(value) {
  const seconds = /^\d+$/.test(value || '') ? Number(value) : (Date.parse(value || '') - Date.now()) / 1000;
  return Number.isFinite(seconds) ? Math.min(3600, Math.max(5, Math.ceil(seconds))) : 60;
}

/** Bounds both headers and the entire streaming body, including chunked bodies.
 * Redirects never leave the fixed 7TV origin. An absolute deadline also handles
 * a server that sends headers and then stalls indefinitely.
 */
async function fetchBytes(url, init, limit, timeoutMs = UPSTREAM_TIMEOUT_MS) {
  const parsed = new URL(url);
  if (parsed.protocol !== 'https:' || !['7tv.io', 'cdn.7tv.app'].includes(parsed.hostname) || parsed.port) {
    throw new SourceError('invalid_upstream', 400);
  }
  const controller = new AbortController();
  let reader;
  let timer;
  const expired = new Promise((_, reject) => {
    timer = setTimeout(() => {
      controller.abort();
      reject(new SourceError('upstream_timeout', 504, 15));
    }, timeoutMs);
  });
  try {
    const response = await Promise.race([
      fetch(url, { ...init, redirect: 'manual', signal: controller.signal }), expired,
    ]);
    if (!response.ok) {
      if (response.status >= 300 && response.status < 400)
        throw new SourceError('upstream_redirect', 502, 15, response.status);
      if (response.status === 404) throw new SourceError('not_found_on_7tv', 404, 0, 404);
      if (response.status === 429) throw new SourceError('upstream_rate_limited', 503, upstreamRetry(response.headers.get('retry-after')), 429);
      throw new SourceError('upstream_unavailable', 502, 15, response.status);
    }
    const length = response.headers.get('content-length');
    if (length && /^\d+$/.test(length) && Number(length) > limit) throw new SourceError('upstream_body_too_large', 413);
    if (!response.body) throw new SourceError('upstream_empty_body');
    reader = response.body.getReader();
    const chunks = [];
    let total = 0;
    for (;;) {
      const part = await Promise.race([reader.read(), expired]);
      if (part.done) break;
      if (!(part.value instanceof Uint8Array)) throw new SourceError('invalid_upstream_body');
      total += part.value.byteLength;
      if (total > limit) throw new SourceError('upstream_body_too_large', 413);
      chunks.push(part.value);
    }
    const bytes = new Uint8Array(total);
    let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
    return { bytes, mime: (response.headers.get('content-type') || '').split(';', 1)[0].trim().toLowerCase() };
  } catch (error) {
    if (error instanceof SourceError) throw error;
    throw new SourceError(controller.signal.aborted ? 'upstream_timeout' : 'upstream_unreachable', controller.signal.aborted ? 504 : 502, 15);
  } finally {
    clearTimeout(timer);
    // Do not await an untrusted/stalled stream's cancel promise.
    if (reader) reader.cancel().catch(() => {});
    controller.abort();
  }
}

async function fetchJSON(url, init = {}, timeoutMs = UPSTREAM_TIMEOUT_MS) {
  const { bytes, mime } = await fetchBytes(url, { ...init, headers: { accept: 'application/json', ...init.headers } },
    MAX_UPSTREAM_JSON_BYTES, timeoutMs);
  if (mime !== 'application/json' && !mime.endsWith('+json')) throw new SourceError('upstream_invalid_json_type');
  try {
    const value = JSON.parse(decoder.decode(bytes));
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error();
    return value;
  } catch (_) { throw new SourceError('upstream_invalid_json'); }
}

function safeName(value) {
  return typeof value === 'string' && value.length >= 1 && value.length <= 80 && !value.startsWith('/') &&
    !/[\s\p{Cc}\p{Cf}§]/u.test(value);
}

function manifestFromSet(payload, channel) {
  const setId = typeof payload?.id === 'string' ? payload.id.toLowerCase() : '';
  if (!ID.test(setId) || !Array.isArray(payload.emotes) || payload.emotes.length > MAX_EMOTES) {
    throw new SourceError('invalid_emote_set');
  }
  const seen = new Set();
  const emotes = [];
  for (const item of payload.emotes) {
    const id = typeof item?.id === 'string' ? item.id.toLowerCase() : '';
    // Use the set's alias, never data.name (the emote's original global name).
    if (!ID.test(id) || !safeName(item.name)) throw new SourceError('invalid_emote_entry');
    const key = `${id}:${item.name}`;
    if (seen.has(key)) continue;
    seen.add(key);
    emotes.push({ id, name: item.name, animated: item.data?.animated === true,
      zeroWidth: (Number(item.data?.flags || 0) & 256) !== 0 });
  }
  let name = typeof payload.name === 'string' ? payload.name : (channel || `7TV ${setId}`);
  name = name.replace(/[\p{Cc}\p{Cf}§]/gu, '').slice(0, 80).trim();
  const manifest = { schemaVersion: 1, ...(channel ? { channel } : {}), setId,
    name: name || channel || '7TV', fetchedAt: new Date().toISOString(), count: emotes.length, emotes };
  if (encoder.encode(JSON.stringify(manifest)).byteLength > MAX_MANIFEST_BYTES) throw new SourceError('manifest_too_large', 413);
  return manifest;
}

export async function loadChannelManifest(channel) {
  if (typeof channel !== 'string' || !/^[a-z0-9_]{1,25}$/.test(channel)) throw new SourceError('invalid_channel', 400);
  const deadline = Date.now() + UPSTREAM_TIMEOUT_MS;
  const gql = await atStage('search', () => fetchJSON('https://7tv.io/v3/gql', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ operationName: 'FindChannel',
      query: 'query FindChannel($name: String!) { users(query: $name, limit: 20) { connections { id platform username } } }',
      variables: { name: channel } }),
  }, Math.max(1, deadline - Date.now())));
  if (gql.errors?.length || !Array.isArray(gql?.data?.users)) throw new SourceError('channel_lookup_unavailable', 502, 15, 0, 'search');
  const ids = new Set();
  for (const user of gql.data.users.slice(0, 20)) {
    if (!Array.isArray(user?.connections)) continue;
    for (const connection of user.connections.slice(0, 20)) {
      if (connection.platform === 'TWITCH' && String(connection.username || '').toLowerCase() === channel &&
          /^[0-9]{1,25}$/.test(String(connection.id || ''))) ids.add(String(connection.id));
    }
  }
  if (ids.size !== 1) throw new SourceError(ids.size ? 'channel_lookup_ambiguous' : 'channel_not_found', 404, 0, 0, 'search');
  const twitchId = [...ids][0];
  return atStage('connection', async () => {
    const payload = await fetchJSON(`https://7tv.io/v3/users/twitch/${twitchId}`, {}, Math.max(1, deadline - Date.now()));
    if (payload.platform !== 'TWITCH' || String(payload.id) !== twitchId || String(payload.username || '').toLowerCase() !== channel)
      throw new SourceError('channel_lookup_mismatch');
    if (!payload.emote_set) throw new SourceError('channel_has_no_set', 404);
    const manifest = manifestFromSet(payload.emote_set, channel);
    if (payload.emote_set_id && String(payload.emote_set_id).toLowerCase() !== manifest.setId)
      throw new SourceError('set_lookup_mismatch');
    return manifest;
  });
}

export async function loadSetManifest(id) {
  if (typeof id !== 'string' || !ID.test(id)) throw new SourceError('invalid_set_id', 400);
  id = id.toLowerCase();
  return atStage('set', async () => {
    const payload = await fetchJSON(`https://7tv.io/v3/emote-sets/${id}`);
    const manifest = manifestFromSet(payload);
    if (manifest.setId !== id) throw new SourceError('set_lookup_mismatch');
    return manifest;
  });
}

function trackFailure(key, error) {
  if (retryAfter.size >= MAX_TRACKED_KEYS && !retryAfter.has(key)) retryAfter.delete(retryAfter.keys().next().value);
  const seconds = error instanceof SourceError && error.retry ? error.retry : 60;
  retryAfter.set(key, Date.now() + Math.max(seconds, 15) * 1000);
}

function cooldown(key) {
  const until = retryAfter.get(key) || 0;
  if (until <= Date.now()) { retryAfter.delete(key); return 0; }
  return Math.ceil((until - Date.now()) / 1000);
}

async function refreshManifest(env, kind, id, key) {
  if (filling.has(key) || filling.size >= MAX_TRACKED_KEYS) throw new SourceError('catalogue_busy', 503, 5);
  const wait = cooldown(key);
  if (wait) throw new SourceError('catalogue_retry_later', 503, wait);
  filling.add(key);
  try {
    const manifest = kind === 'channel' ? await loadChannelManifest(id) : await loadSetManifest(id);
    const content = JSON.stringify(manifest);
    await atStage('cache', () => env.EMOTES.put(key, content, { httpMetadata: { contentType: 'application/json; charset=utf-8' } }));
    retryAfter.delete(key);
    return content;
  } catch (error) { trackFailure(key, error); throw error; }
  finally { filling.delete(key); }
}

function manifestResponse(body, request, state) {
  return new Response(request.method === 'HEAD' ? null : body, {
    headers: { ...JSON_HEADERS, 'cache-control': 'public, max-age=120', 'x-moonotes-cache': state },
  });
}

async function serveManifest(request, env, ctx, kind, id) {
  // Keep this exact channel key for pre-0.4 clients and manually uploaded files.
  const key = kind === 'channel' ? `manifests/7tv/${id}.json` : `manifests/7tv/sets/${id}.json`;
  const read = name => atStage('cache', () => request.method === 'HEAD' && env.EMOTES.head ? env.EMOTES.head(name) : env.EMOTES.get(name));
  let cached = await read(key), legacy = false;
  // 0.5.1 used a different path for additional channels. Read it, migrate only on a GET refresh.
  if (!cached && kind === 'channel' && id !== 'v0kky') {
    cached = await read(`manifests/7tv/channels/${id}.json`); legacy = Boolean(cached);
  }
  if (cached) {
    if (Number(cached.size || 0) > MAX_MANIFEST_BYTES) throw new SourceError('cached_manifest_too_large', 413);
    const age = Date.now() - new Date(cached.uploaded).getTime();
    const fresh = !legacy && Number.isFinite(age) && age >= 0 && age < MANIFEST_TTL_MS;
    // HEAD is an existence check; it never causes an upstream fetch or R2 write.
    if (!fresh && request.method === 'GET' && ctx?.waitUntil && !filling.has(key) && !cooldown(key)) {
      ctx.waitUntil(refreshManifest(env, kind, id, key).catch(() => {}));
    }
    return manifestResponse(cached.body, request, fresh ? 'HIT' : 'STALE');
  }
  if (request.method === 'HEAD') return json({ error: 'not_cached' }, 404, request);
  const content = await refreshManifest(env, kind, id, key);
  return manifestResponse(content, request, 'MISS');
}

function imageHeaders(cache) {
  return { 'content-type': 'image/webp', 'cache-control': `public, max-age=${IMAGE_TTL_SECONDS}, immutable`,
    'x-content-type-options': 'nosniff', 'x-moonotes-version': VERSION, 'x-moonotes-cache': cache };
}

function validWebp(data) {
  if (data.byteLength < 20) return false;
  const tag = offset => String.fromCharCode(...data.subarray(offset, offset + 4));
  if (tag(0) !== 'RIFF' || tag(8) !== 'WEBP' || !['VP8 ', 'VP8L', 'VP8X'].includes(tag(12))) return false;
  const view = new DataView(data.buffer, data.byteOffset, data.byteLength);
  return view.getUint32(4, true) + 8 === data.byteLength && view.getUint32(16, true) <= data.byteLength - 20;
}

/** Canonical CDN IDs differ from our stable lowercase cache keys.
 * 7TV issues HTTP 308 for lowercase ULIDs AND old 24-hex ObjectIds. Construct
 * the canonical ID using SevenTV's published ObjectId -> ULID mapping, keeping
 * redirects disabled. No Location header or user-provided host is followed.
 */
function canonicalImageId(id) {
  if (typeof id !== 'string' || !ID.test(id)) throw new SourceError('invalid_emote_id', 400);
  if (/^[0-7][0-9A-HJKMNP-TV-Z]{25}$/i.test(id)) return id.toUpperCase();
  if (/^[a-f0-9]{24}$/i.test(id)) {
    let value = (BigInt(`0x${id.slice(0, 8)}`) * 1000n << 80n) | BigInt(`0x${id.slice(8)}`);
    const alphabet = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
    let encoded = '';
    for (let i = 0; i < 26; i++) { encoded = alphabet[Number(value & 31n)] + encoded; value >>= 5n; }
    return encoded;
  }
  return id.toLowerCase();
}

/** Also used by the optional read-only live check: exactly the deployed path. */
export async function fetchCdnImage(id, size = '2x') {
  if (!/^(1x|2x|3x|4x)$/.test(size)) throw new SourceError('invalid_image_size', 400);
  return atStage('image', async () => {
    const canonical = canonicalImageId(id);
    const { bytes, mime } = await fetchBytes(`https://cdn.7tv.app/emote/${canonical}/${size}.webp`,
      { headers: { accept: 'image/webp' }, cf: { cacheTtl: IMAGE_TTL_SECONDS, cacheEverything: true } }, MAX_IMAGE_BYTES);
    if (!['image/webp', 'application/octet-stream'].includes(mime)) throw new SourceError('image_invalid_type');
    if (!validWebp(bytes)) throw new SourceError('image_invalid_webp');
    return bytes;
  });
}

async function serveImage(request, env, ctx, id, size) {
  const key = `7tv/${id}/${size}.webp`;
  const edge = globalThis.caches?.default;
  // Query strings are rejected at the router; case variations share this key.
  const edgeKey = new Request(new URL(`/v1/7tv/${id}/${size}.webp`, request.url).href);
  if (edge) {
    // An edge-cache outage must not hide a usable R2 object.
    const hit = await edge.match(edgeKey).catch(() => null);
    if (hit) {
      const headers = new Headers(hit.headers);
      headers.set('x-moonotes-cache', 'EDGE');
      return new Response(request.method === 'HEAD' ? null : hit.body, { status: hit.status, headers });
    }
  }
  const cached = request.method === 'HEAD' && env.EMOTES.head ? await env.EMOTES.head(key) : await env.EMOTES.get(key);
  if (cached) {
    if (Number(cached.size || 0) > MAX_IMAGE_BYTES) throw new SourceError('cached_image_too_large', 413);
    const response = new Response(request.method === 'HEAD' ? null : cached.body, { headers: imageHeaders('HIT') });
    if (edge && request.method === 'GET' && ctx?.waitUntil) ctx.waitUntil(edge.put(edgeKey, response.clone()).catch(() => {}));
    return response;
  }
  if (request.method === 'HEAD') return json({ error: 'not_cached' }, 404, request);
  if (filling.has(key) || filling.size >= MAX_TRACKED_KEYS) throw new SourceError('image_busy', 503, 5);
  const wait = cooldown(key);
  if (wait) throw new SourceError('image_retry_later', 503, wait);
  filling.add(key);
  try {
    const bytes = await fetchCdnImage(id, size);
    await atStage('cache', () => env.EMOTES.put(key, bytes,
      { httpMetadata: { contentType: 'image/webp', cacheControl: `public, max-age=${IMAGE_TTL_SECONDS}` } }));
    retryAfter.delete(key);
    const response = new Response(bytes, { headers: imageHeaders('MISS') });
    if (edge && ctx?.waitUntil) ctx.waitUntil(edge.put(edgeKey, response.clone()).catch(() => {}));
    return response;
  } catch (error) { trackFailure(key, error); throw error; }
  finally { filling.delete(key); }
}

export default {
  async fetch(request, env, ctx) {
    if (request.method !== 'GET' && request.method !== 'HEAD') {
      return json({ error: 'method_not_allowed' }, 405, request, { allow: 'GET, HEAD' });
    }
    const url = new URL(request.url);
    const path = url.pathname;
    if (url.search || path.length > 200) return json({ error: 'invalid_request' }, 400, request);
    if (path === '/health') return json({ service: 'moonotes-r2', version: VERSION,
      status: env.EMOTES ? 'ok' : 'missing_bucket', bucketBound: Boolean(env.EMOTES),
      capabilities: ['channels', 'sets', 'cached-manifests', 'images', 'gql-twitch-login', 'typed-errors', 'aliases', 'canonical-cdn-ids'] }, env.EMOTES ? 200 : 503, request);
    if (!env.EMOTES) return json({ error: 'r2_not_bound' }, 503, request);
    try {
      const channel = CHANNEL_PATH.exec(path);
      if (channel) return await serveManifest(request, env, ctx, 'channel', channel[1].toLowerCase());
      const set = SET_PATH.exec(path);
      if (set && ID.test(set[1])) return await serveManifest(request, env, ctx, 'set', set[1].toLowerCase());
      const image = IMAGE_PATH.exec(path);
      if (image && ID.test(image[1])) return await serveImage(request, env, ctx, image[1].toLowerCase(), image[2].toLowerCase());
      return json({ error: 'not_found' }, 404, request);
    } catch (error) { return failure(error, request); }
  },
};

// Deterministic validation/deadline checks without contacting a deployed Worker.
export const testing = { manifestFromSet, fetchBytes, validWebp, safeName, canonicalImageId,
  reset() { filling.clear(); retryAfter.clear(); } };
