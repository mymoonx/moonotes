/** Optional administrator prefetch. The GUI can add catalogues without this.
 * node sync-r2.mjs [--channel v0kky | --set <7TV-set-id>]
 * The authenticated Wrangler upload is used only if the Worker cannot retrieve
 * a new manifest and the administrator's PC can reach 7TV directly.
 */
import { setTimeout as delay } from 'node:timers/promises';
import { writeFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { loadChannelManifest, loadSetManifest } from './src/index.mjs';

const args = process.argv.slice(2);
const kind = args[0] === '--set' ? 'set' : 'channel';
const id = String(args[1] || (args.length ? '' : 'v0kky')).toLowerCase();
const validId = /^(?:[0-9a-hjkmnp-tv-z]{26}|[a-f0-9]{16,40})$/;
if (args.length && (args.length !== 2 || !['--channel', '--set'].includes(args[0])) ||
    !(kind === 'channel' ? /^[a-z0-9_]{1,25}$/.test(id) : validId.test(id))) {
  console.error('Использование: node sync-r2.mjs --channel v0kky  ИЛИ  --set <ID набора 7TV>');
  process.exit(1);
}
const base = new URL(process.env.MOONOTES_WORKER || 'https://moonotes-emotes.thefreelom.workers.dev');
if (base.protocol !== 'https:' || base.username || base.password || base.search || base.hash ||
    base.pathname !== '/' || base.port) throw new Error('MOONOTES_WORKER должен быть корневым HTTPS-адресом Worker');
const BASE = base.origin;
const MANIFEST_URL = `${BASE}/v1/7tv/${kind}/${id}`;
const PREFIX = kind === 'channel' ? id : `set-${id}`;
const MANIFEST_FILE = `${PREFIX}-manifest.json`;
const REPORT_FILE = `${PREFIX}-sync-report.json`;
const KEY = kind === 'channel' ? `manifests/7tv/${id}.json` : `manifests/7tv/sets/${id}.json`;

async function request(url, method = 'GET', limit = 3 * 1024 * 1024) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 25000);
  let reader;
  try {
    const response = await fetch(url, { method, redirect: 'error', signal: controller.signal,
      headers: { 'user-agent': 'Moonotes-R2-Prefetch/0.5.3' } });
    if (method === 'HEAD') return { response, bytes: new Uint8Array() };
    if (Number(response.headers.get('content-length') || 0) > limit) throw new Error('Ответ слишком большой');
    reader = response.body?.getReader();
    if (!reader) throw new Error('Пустой ответ');
    const parts = []; let size = 0;
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > limit) throw new Error('Ответ слишком большой');
      parts.push(value);
    }
    const bytes = new Uint8Array(size); let offset = 0;
    for (const part of parts) { bytes.set(part, offset); offset += part.length; }
    return { response, bytes };
  } finally {
    clearTimeout(timeout); if (reader) reader.cancel().catch(() => {}); controller.abort();
  }
}

function validateManifest(value) {
  if (!value || !Array.isArray(value.emotes) || value.emotes.length > 2000) throw new Error('Некорректный каталог');
  for (const item of value.emotes) {
    if (typeof item.id !== 'string' || !validId.test(item.id) || typeof item.name !== 'string' ||
        !item.name.length || item.name.length > 80 || item.name.startsWith('/') || /[\s\p{Cc}\p{Cf}§]/u.test(item.name)) {
      throw new Error('Некорректная запись каталога');
    }
  }
  return value;
}

async function getManifest() {
  try {
    const { response, bytes } = await request(MANIFEST_URL, 'GET', 1024 * 1024);
    if (!response.ok) throw new Error(`Worker HTTP ${response.status}`);
    return validateManifest(JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes)));
  } catch (error) {
    console.log('Worker не вернул каталог:', error.message);
    console.log('Пробую 7TV с этого ПК. Если 7TV заблокирован, на этом шаге потребуется VPN.');
    const manifest = kind === 'channel' ? await loadChannelManifest(id) : await loadSetManifest(id);
    await writeFile(MANIFEST_FILE, JSON.stringify(manifest, null, 2));
    console.log('Сохраняю каталог в существующий R2 через авторизованный Wrangler…');
    const cli = process.platform === 'win32' ? 'npx.cmd' : 'npx';
    const job = spawnSync(cli, ['wrangler', 'r2', 'object', 'put', `moonotes-emotes/${KEY}`,
      '--file', MANIFEST_FILE, '--content-type', 'application/json', '--remote'],
      { stdio: 'inherit', shell: process.platform === 'win32' });
    if (job.status !== 0) throw new Error('Каталог сохранён локально, но загрузка в R2 не прошла. Проверь вход Wrangler.');
    return manifest;
  }
}

async function main() {
  console.log(`Moonotes R2 0.5.3 — ${kind === 'channel' ? 'канал' : 'набор'} ${id}`);
  console.log('Worker:', BASE);
  const manifest = await getManifest();
  await writeFile(MANIFEST_FILE, JSON.stringify(manifest, null, 2));
  // Different aliases may share the same image; transfer that file once.
  const list = [...new Map(manifest.emotes.map(item => [item.id, item])).values()];
  console.log(`Каталог: ${manifest.emotes.length} названий; уникальных файлов: ${list.length}.`);
  console.log('Готовые объекты R2 сохраняются. Скачиваются только отсутствующие.');
  let cursor = 0, done = 0, skipped = 0, uploaded = 0, failed = 0;
  const errors = [];
  async function download(item) {
    const url = `${BASE}/v1/7tv/${item.id}/2x.webp`;
    const head = await request(url, 'HEAD');
    if (head.response.ok) { skipped++; return; }
    if (head.response.status !== 404) throw new Error(`Проверка кеша: HTTP ${head.response.status}`);
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        const { response, bytes } = await request(url);
        if (!response.ok) {
          const retry = Number(response.headers.get('retry-after') || 0);
          if (retry > 3) throw Object.assign(new Error(`HTTP ${response.status}; повторить через ${retry} сек.`), { noRetry: true });
          throw new Error(`HTTP ${response.status}`);
        }
        if (!(response.headers.get('content-type') || '').startsWith('image/webp') || bytes.length < 20) throw new Error('Ответ не является WebP');
        uploaded++; return;
      } catch (error) {
        if (attempt === 2 || error.noRetry) throw error;
        await delay(500 * (attempt + 1));
      }
    }
  }
  async function worker() {
    while (cursor < list.length) {
      const item = list[cursor++];
      try { await download(item); }
      catch (error) { failed++; errors.push({ id: item.id, name: item.name, error: String(error.message || error).slice(0, 160) }); }
      done++;
      if (done % 20 === 0 || done === list.length) console.log(`${done}/${list.length} | уже было: ${skipped} | добавлено: ${uploaded} | ошибок: ${failed}`);
      await delay(75);
    }
  }
  await Promise.all(Array.from({ length: Math.min(3, list.length) }, worker));
  await writeFile(REPORT_FILE, JSON.stringify({ kind, id, total: list.length, skipped, uploaded, failed, errors }, null, 2));
  console.log(`Готово: ${uploaded} новых, ${skipped} уже в R2, ${failed} ошибок. Отчёт: ${REPORT_FILE}`);
  if (failed) process.exitCode = 1;
}
main().catch(error => { console.error('Ошибка:', error.message); process.exitCode = 1; });
