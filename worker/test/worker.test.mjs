import test from 'node:test';
import assert from 'node:assert/strict';
import worker, { testing } from '../src/index.mjs';

const nativeFetch = globalThis.fetch, nativeCaches = globalThis.caches;
const endpoint = 'https://moonotes-emotes.example';
const emoteId = '01f6mzgcng000255k4x1k7nthr', setId = '01j00000000000000000000000';
const image = Uint8Array.from(Buffer.from('UklGRh4AAABXRUJQVlA4TBEAAAAvAAAAAAfQ//73v/+BiOh/AAA=', 'base64'));
test.beforeEach(() => { testing.reset(); delete globalThis.caches; });
test.afterEach(() => { globalThis.fetch = nativeFetch; if (nativeCaches) globalThis.caches = nativeCaches; else delete globalThis.caches; });

function setup(channel = 'v0kky') {
  const saved = new Map(), calls = [], reads = [], writes = [];
  const emotes = [{ id: emoteId, name: 'Alias', data: { name: 'OriginalName', animated: true, flags: 256 } },
    { id: emoteId, name: 'Alias2', data: { animated: true, flags: 256 } }, { id: emoteId, name: 'Alias' }];
  const set = { id: setId, name: 'My Set', emotes };
  const r2 = {
    async get(key) {
      reads.push(key); const value = saved.get(key);
      return value ? { uploaded: value.date, size: Buffer.byteLength(value.data), body: new Response(value.data).body } : null;
    },
    async head(key) {
      reads.push(key); const value = saved.get(key);
      return value ? { uploaded: value.date, size: Buffer.byteLength(value.data) } : null;
    },
    async put(key, body) {
      writes.push(key); saved.set(key, { data: typeof body === 'string' ? body : new Uint8Array(body), date: new Date() });
    },
  };
  globalThis.fetch = async (url, init) => {
    calls.push({ url: String(url), init });
    if (String(url).endsWith('/v3/gql')) return Response.json({ data: { users: [
      { username: channel, connections: [{ platform: 'TWITCH', id: '111', username: 'someoneelse' }] },
      { username: 'seventv_name_differs', connections: [{ platform: 'TWITCH', id: '1234', username: channel }] },
    ] } });
    if (String(url).endsWith('/v3/users/twitch/1234')) return Response.json({ platform: 'TWITCH', id: '1234', username: channel, emote_set: set });
    if (String(url).endsWith(`/v3/emote-sets/${setId}`)) return Response.json(set);
    if (String(url).startsWith('https://cdn.7tv.app/')) return new Response(image, { headers: { 'content-type': 'image/webp' } });
    throw new Error('Unexpected URL');
  };
  return { saved, calls, reads, writes, r2, set };
}
const request = (path, method = 'GET') => new Request(endpoint + path, { method });
const run = (r2, path, method = 'GET', ctx) => worker.fetch(request(path, method), { EMOTES: r2 }, ctx);

test('arbitrary channel resolves exact Twitch connection, preserves aliases and flags', async () => {
  const { r2, saved, calls } = setup('some_channel');
  const result = await run(r2, '/v1/7tv/channel/Some_Channel.json');
  assert.equal(result.status, 200);
  const data = await result.json();
  assert.equal(data.channel, 'some_channel'); assert.equal(data.setId, setId); assert.equal(data.count, 2);
  assert.equal(data.emotes[0].name, 'Alias'); assert.equal(data.emotes[1].name, 'Alias2');
  assert.equal(data.emotes[0].zeroWidth, true); assert.equal(data.emotes[0].animated, true);
  assert.ok(saved.has('manifests/7tv/some_channel.json')); assert.ok(calls[1].url.endsWith('/1234'));
  assert.equal(calls[0].init.redirect, 'manual');
});

test('existing v0kky R2 catalogue is served without 7TV or writes', async () => {
  const { r2, saved, calls, writes } = setup();
  saved.set('manifests/7tv/v0kky.json', { data: JSON.stringify({ channel: 'v0kky', count: 704, emotes: [] }), date: new Date() });
  const res = await run(r2, '/v1/7tv/channel/v0kky');
  assert.equal(res.headers.get('x-moonotes-cache'), 'HIT'); assert.equal((await res.json()).count, 704);
  assert.equal(calls.length, 0); assert.equal(writes.length, 0);
});

test('set route caches compact manifest under independent key', async () => {
  const { r2, saved, calls } = setup();
  let res = await run(r2, `/v1/7tv/set/${setId.toUpperCase()}`);
  assert.equal(res.status, 200); const data = await res.json();
  assert.equal(data.setId, setId); assert.equal(data.channel, undefined);
  assert.ok(saved.has(`manifests/7tv/sets/${setId}.json`));
  res = await run(r2, `/v1/7tv/set/${setId}`);
  assert.equal(res.headers.get('x-moonotes-cache'), 'HIT'); assert.equal(calls.length, 1);
});

test('empty structurally valid set can synchronize removal of all emotes', () => {
  assert.equal(testing.manifestFromSet({ id: setId, name: 'Empty', emotes: [] }).count, 0);
  assert.throws(() => testing.manifestFromSet({ id: setId }));
});

test('malformed entries and huge sets cannot become partial catalogues', () => {
  for (const entry of [null, { id: '../outside', name: 'X' }, { id: emoteId, name: 'two words' },
    { id: emoteId, name: 'X\nY' }, { id: emoteId, name: '§kbad' }, { id: emoteId, name: '/command' },
    { id: emoteId, name: 'X\u202eY' }, { id: emoteId, name: 'a'.repeat(81) }]) {
    assert.throws(() => testing.manifestFromSet({ id: setId, emotes: [entry] }));
  }
  assert.throws(() => testing.manifestFromSet({ id: setId, emotes: Array.from({ length: 2001 }, () => ({ id: emoteId, name: 'X' })) }));
});

test('stale manifest remains immediately available when refresh fails', async () => {
  const { r2, saved, writes } = setup();
  const old = JSON.stringify({ channel: 'v0kky', count: 1, emotes: [{ id: emoteId, name: 'Old' }] });
  saved.set('manifests/7tv/v0kky.json', { data: old, date: new Date(Date.now() - 48 * 3600000) });
  globalThis.fetch = async () => { throw new Error('private network details'); };
  const jobs = [];
  const response = await run(r2, '/v1/7tv/channel/v0kky', 'GET', { waitUntil(p) { jobs.push(p); } });
  assert.equal(response.status, 200); assert.equal(response.headers.get('x-moonotes-cache'), 'STALE');
  assert.equal(await response.text(), old); await Promise.all(jobs);
  assert.equal(writes.length, 0); assert.equal(saved.get('manifests/7tv/v0kky.json').data, old);
});

test('HEAD never fills or refreshes cache, including stale objects', async () => {
  const { r2, saved, calls, writes } = setup();
  saved.set('manifests/7tv/v0kky.json', { data: '{}', date: new Date(0) });
  const jobs = [];
  for (const path of ['/v1/7tv/channel/v0kky', `/v1/7tv/set/${setId}`, `/v1/7tv/${emoteId}/2x.webp`]) {
    const res = await run(r2, path, 'HEAD', { waitUntil(p) { jobs.push(p); } }); assert.equal(await res.text(), '');
  }
  assert.equal(calls.length, 0); assert.equal(writes.length, 0); assert.equal(jobs.length, 0);
});

test('image fetch uses fixed CDN once, then shared R2 key', async () => {
  const { r2, saved, calls } = setup(); const path = `/v1/7tv/${emoteId}/2x.webp`;
  let res = await run(r2, path);
  assert.equal(res.status, 200); assert.equal(res.headers.get('x-moonotes-cache'), 'MISS');
  assert.deepEqual(new Uint8Array(await res.arrayBuffer()), image); assert.ok(saved.has(`7tv/${emoteId}/2x.webp`));
  res = await run(r2, path);
  assert.equal(res.headers.get('x-moonotes-cache'), 'HIT'); assert.equal(calls.length, 1);
  assert.equal(calls[0].url, `https://cdn.7tv.app/emote/${emoteId.toUpperCase()}/2x.webp`);
  assert.equal(calls[0].init.redirect, 'manual');
});

test('second catalogue lowercase ULID is canonicalized before CDN and reuses R2', async () => {
  const { r2, saved, calls } = setup();
  const id = '01f5z4b9700008kdxa2tjqbngw', canonical = id.toUpperCase();
  globalThis.fetch = async (url, init) => {
    calls.push({ url: String(url), init });
    if (String(url) === `https://cdn.7tv.app/emote/${canonical}/2x.webp`)
      return new Response(image, { headers: { 'content-type': 'image/webp' } });
    return new Response(null, { status: 308, headers: { location: `/emote/${canonical}/2x.webp` } });
  };
  const path = `/v1/7tv/${id}/2x.webp`;
  assert.equal((await run(r2, path)).status, 200);
  assert.equal(calls.length, 1); assert.ok(saved.has(`7tv/${id}/2x.webp`));
  assert.equal((await run(r2, `/v1/7tv/${canonical}/2x.webp`)).headers.get('x-moonotes-cache'), 'HIT');
  assert.equal(calls.length, 1);
});

test('legacy ObjectId uses exact published 7TV mapping while preserving existing R2 key', async () => {
  const { r2, saved, calls } = setup();
  const id = '60a304efac2bcb20ef20fa89';
  assert.equal(testing.canonicalImageId(id), '01F5YCPHCR000ARAYB43QJ1YM9');
  assert.equal(testing.canonicalImageId('60B9E0E9B55F4FA0ED9E2785'), '01F7B1D3H8000BAQTFM3PSW9W5');
  assert.equal((await run(r2, `/v1/7tv/${id}/2x.webp`)).status, 200);
  assert.equal(calls[0].url, 'https://cdn.7tv.app/emote/01F5YCPHCR000ARAYB43QJ1YM9/2x.webp');
  assert.ok(saved.has(`7tv/${id}/2x.webp`));
  assert.equal((await run(r2, `/v1/7tv/${id}/2x.webp`)).headers.get('x-moonotes-cache'), 'HIT');
  assert.equal(calls.length, 1);
});

test('unexpected redirect never follows any Location and reports image stage/status', async () => {
  const { r2, writes } = setup(); let calls = 0;
  globalThis.fetch = async (_url, init) => {
    calls++; assert.equal(init.redirect, 'manual');
    return new Response(null, { status: 308, headers: { location: 'http://127.0.0.1/private?token=secret' } });
  };
  const result = await run(r2, `/v1/7tv/${emoteId}/2x.webp`);
  assert.equal(result.status, 502);
  assert.deepEqual(await result.json(), { error: 'upstream_redirect', stage: 'image', upstreamStatus: 308, version: '0.5.3' });
  assert.equal(calls, 1); assert.equal(writes.length, 0);
});

test('canonical image upstream failure and R2 write failure expose separate stages', async () => {
  const { r2 } = setup();
  globalThis.fetch = async () => new Response('private data', { status: 403 });
  let result = await run(r2, `/v1/7tv/${emoteId}/2x.webp`);
  assert.deepEqual(await result.json(), { error: 'upstream_unavailable', stage: 'image', upstreamStatus: 403, version: '0.5.3' });
  testing.reset();
  globalThis.fetch = async () => new Response(image, { headers: { 'content-type': 'image/webp' } });
  r2.put = async () => { throw new Error('SecretAccessKey=private'); };
  result = await run(r2, `/v1/7tv/${emoteId}/2x.webp`);
  assert.deepEqual(await result.json(), { error: 'gateway_unavailable', stage: 'cache', version: '0.5.3' });
});

test('edge cache avoids R2 reads and HEAD has no body', async () => {
  const { r2, calls, reads } = setup();
  globalThis.caches = { default: { async match() { return new Response(image, { headers: { 'content-type': 'image/webp' } }); } } };
  const res = await run(r2, `/v1/7tv/${emoteId}/2x.webp`, 'HEAD');
  assert.equal(res.headers.get('x-moonotes-cache'), 'EDGE'); assert.equal(await res.text(), '');
  assert.equal(reads.length, 0); assert.equal(calls.length, 0);
});

test('edge cache outage falls back to R2', async () => {
  const { r2, saved } = setup(); saved.set(`7tv/${emoteId}/2x.webp`, { data: image, date: new Date() });
  globalThis.caches = { default: { async match() { throw new Error('edge unavailable'); } } };
  const res = await run(r2, `/v1/7tv/${emoteId}/2x.webp`);
  assert.equal(res.status, 200); assert.equal(res.headers.get('x-moonotes-cache'), 'HIT');
});

test('invalid paths, arbitrary URLs, query strings and writes never fetch upstream', async () => {
  const { r2, calls } = setup();
  for (const path of ['/proxy?url=http://127.0.0.1', '/v1/7tv/channel/a%2fb', '/v1/7tv/set/../../x',
    `/v1/7tv/${emoteId}/20x.webp`, '/v1/7tv/channel/v0kky?url=https://evil.test', '/v1/7tv/channel/' + 'a'.repeat(26)]) {
    const res = await run(r2, path); assert.ok(res.status === 400 || res.status === 404);
  }
  assert.equal((await run(r2, '/v1/7tv/channel/v0kky', 'POST')).status, 405); assert.equal(calls.length, 0);
  await assert.rejects(testing.fetchBytes('http://127.0.0.1/', {}, 1024));
});

test('bounded streaming rejects oversized chunked body and cancels reader', async () => {
  let cancelled = false;
  globalThis.fetch = async () => new Response(new ReadableStream({
    start(controller) { controller.enqueue(new Uint8Array(32)); controller.enqueue(new Uint8Array(32)); },
    cancel() { cancelled = true; },
  }));
  await assert.rejects(testing.fetchBytes('https://cdn.7tv.app/emote/x/2x.webp', {}, 48), /upstream_body_too_large/);
  assert.equal(cancelled, true);
});

test('absolute deadline covers stalled body after successful headers', async () => {
  let cancelled = false;
  globalThis.fetch = async () => new Response(new ReadableStream({ cancel() { cancelled = true; } }));
  await assert.rejects(testing.fetchBytes('https://cdn.7tv.app/emote/x/2x.webp', {}, 100, 20), /upstream_timeout/);
  assert.equal(cancelled, true);
});

test('announced oversize and wrong MIME cannot be stored', async () => {
  const { r2, writes } = setup();
  globalThis.fetch = async () => new Response('small', { headers: { 'content-length': String(3 * 1024 * 1024 + 1), 'content-type': 'image/webp' } });
  let res = await run(r2, `/v1/7tv/${emoteId}/2x.webp`); assert.equal(res.status, 413); assert.equal(writes.length, 0);
  testing.reset();
  globalThis.fetch = async () => new Response('<html>blocked</html>', { headers: { 'content-type': 'text/html' } });
  res = await run(r2, `/v1/7tv/${emoteId}/2x.webp`); assert.equal(res.status, 502); assert.equal(writes.length, 0);
});

test('truncated WebP cannot be stored', async () => {
  const { r2, writes } = setup();
  globalThis.fetch = async () => new Response(image.subarray(0, image.length - 1), { headers: { 'content-type': 'image/webp' } });
  assert.equal((await run(r2, `/v1/7tv/${emoteId}/2x.webp`)).status, 502); assert.equal(writes.length, 0);
});

test('upstream 429 respects Retry-After and avoids repeated attempts in this isolate', async () => {
  const { r2 } = setup(); let calls = 0;
  globalThis.fetch = async () => { calls++; return new Response(null, { status: 429, headers: { 'retry-after': '120' } }); };
  const path = `/v1/7tv/set/${setId}`;
  let res = await run(r2, path); assert.equal(res.status, 503); assert.equal(res.headers.get('retry-after'), '120');
  res = await run(r2, path); assert.equal(res.status, 503); assert.ok(Number(res.headers.get('retry-after')) >= 119);
  assert.equal(calls, 1);
});

test('simultaneous cold catalogue requests do not duplicate upstream work', async () => {
  const { r2 } = setup(); const source = globalThis.fetch; let resolveFetch, entered;
  const started = new Promise(resolve => { entered = resolve; });
  globalThis.fetch = async (url, init) => {
    if (String(url).endsWith('/v3/gql')) { entered(); await new Promise(resolve => { resolveFetch = resolve; }); }
    return source(url, init);
  };
  const first = run(r2, '/v1/7tv/channel/v0kky'); await started;
  const second = await run(r2, '/v1/7tv/channel/v0kky');
  assert.equal(second.status, 503); assert.equal(second.headers.get('retry-after'), '5');
  resolveFetch(); assert.equal((await first).status, 200);
});

test('ambiguous channel and mismatched set ID are rejected', async () => {
  const { r2 } = setup();
  globalThis.fetch = async () => Response.json({ data: { users: [{ connections: [
    { platform: 'TWITCH', id: '1', username: 'v0kky' }, { platform: 'TWITCH', id: '2', username: 'v0kky' },
  ] }] } });
  assert.equal((await run(r2, '/v1/7tv/channel/v0kky')).status, 404);
  globalThis.fetch = async () => Response.json({ id: emoteId, emotes: [] });
  assert.equal((await run(r2, `/v1/7tv/set/${setId}`)).status, 502);
});

test('R2 failure returns category rather than private error text', async () => {
  const r2 = { async get() { throw new Error('SecretAccessKey=private-value'); } };
  const res = await run(r2, '/v1/7tv/channel/v0kky'); assert.equal(res.status, 503);
  assert.deepEqual(await res.json(), { error: 'gateway_unavailable', stage: 'cache', version: '0.5.3' });
});

test('health identifies version/capabilities and missing R2 binding', async () => {
  const { r2 } = setup(); const data = await (await run(r2, '/health')).json();
  assert.equal(data.version, '0.5.3'); assert.ok(data.capabilities.includes('sets'));
  assert.equal((await worker.fetch(request('/health'), {})).status, 503);
  assert.equal(await (await run(r2, '/health', 'HEAD')).text(), '');
});


test('GQL exact nickname lookup uses POST and never the removed REST search route', async () => {
  const { r2, calls } = setup('another_channel');
  const result = await run(r2, '/v1/7tv/channel/another_channel');
  assert.equal(result.status, 200);
  assert.equal(calls.length, 2);
  assert.equal(calls[0].url, 'https://7tv.io/v3/gql');
  assert.equal(calls[0].init.method, 'POST');
  const body = JSON.parse(calls[0].init.body);
  assert.equal(body.variables.name, 'another_channel');
  assert.ok(body.query.includes('limit: 20'));
  assert.equal(calls[1].url, 'https://7tv.io/v3/users/twitch/1234');
  assert.equal(result.headers.get('x-moonotes-version'), '0.5.3');
});

test('0.5.1 additional-channel path remains readable and GET migrates without deleting it', async () => {
  const { r2, saved, writes } = setup('legacy_channel');
  const oldKey = 'manifests/7tv/channels/legacy_channel.json';
  const old = JSON.stringify({ channel: 'legacy_channel', setId, emotes: [] });
  saved.set(oldKey, { data: old, date: new Date() });
  const jobs = [];
  const result = await run(r2, '/v1/7tv/channel/legacy_channel', 'GET', { waitUntil(p) { jobs.push(p); } });
  assert.equal(await result.text(), old);
  assert.equal(result.headers.get('x-moonotes-cache'), 'STALE');
  await Promise.all(jobs);
  assert.ok(saved.has(oldKey));
  assert.ok(saved.has('manifests/7tv/legacy_channel.json'));
  assert.deepEqual(writes, ['manifests/7tv/legacy_channel.json']);
});

test('legacy additional-channel HEAD neither migrates nor refreshes', async () => {
  const { r2, saved, writes, calls } = setup('legacy_channel');
  saved.set('manifests/7tv/channels/legacy_channel.json', { data: '{}', date: new Date(0) });
  const result = await run(r2, '/v1/7tv/channel/legacy_channel', 'HEAD', { waitUntil() { throw new Error('unexpected mutation'); } });
  assert.equal(result.status, 200); assert.equal(await result.text(), '');
  assert.equal(writes.length, 0); assert.equal(calls.length, 0);
});

test('upstream error reports exact HTTP and stage without arbitrary error details', async () => {
  const { r2 } = setup();
  globalThis.fetch = async () => new Response('private token=secret', { status: 403 });
  const result = await run(r2, '/v1/7tv/channel/v0kky');
  assert.equal(result.status, 502);
  assert.deepEqual(await result.json(), { error: 'upstream_unavailable', stage: 'search', upstreamStatus: 403, version: '0.5.3' });
});

test('connection mismatch and a channel without active set fail without R2 writes', async () => {
  const { r2, writes } = setup();
  const source = globalThis.fetch;
  for (const payload of [{ platform: 'TWITCH', id: '9999', username: 'v0kky', emote_set: { id: setId, emotes: [] } },
    { platform: 'TWITCH', id: '1234', username: 'someone_else', emote_set: { id: setId, emotes: [] } },
    { platform: 'TWITCH', id: '1234', username: 'v0kky', emote_set: null }]) {
    testing.reset();
    globalThis.fetch = async (url, init) => String(url).includes('/users/twitch/') ? Response.json(payload) : source(url, init);
    const result = await run(r2, '/v1/7tv/channel/v0kky');
    assert.ok(result.status >= 400); assert.equal((await result.json()).stage, 'connection');
  }
  assert.equal(writes.length, 0);
});

test('set switching follows current connection instead of taking a user global first set', async () => {
  const { r2 } = setup('switcher');
  const source = globalThis.fetch, otherSet = '01k00000000000000000000000';
  globalThis.fetch = async (url, init) => String(url).includes('/users/twitch/')
    ? Response.json({ platform: 'TWITCH', id: '1234', username: 'switcher', emote_set_id: otherSet,
        emote_set: { id: otherSet, name: 'New active set', emotes: [] } }) : source(url, init);
  const result = await run(r2, '/v1/7tv/channel/switcher');
  const body = await result.json();
  assert.equal(result.status, 200); assert.equal(body.setId, otherSet); assert.equal(body.count, 0);
});
