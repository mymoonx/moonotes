/** Optional read-only live smoke check; never part of npm test, no R2 credentials. */
import { createHash } from 'node:crypto';
import { loadChannelManifest, fetchCdnImage } from './src/index.mjs';
const args = process.argv.slice(2);
if (args[0] === '--image') {
  if (args.length !== 2 || !/^(?:[0-7][0-9A-HJKMNP-TV-Z]{25}|[a-f0-9]{24})$/i.test(args[1])) {
    console.error('Usage: npm run check:live -- --image <7TV-emote-id>'); process.exit(2);
  }
  try {
    const bytes = await fetchCdnImage(args[1]);
    console.log(JSON.stringify({ id: args[1], format: 'WebP', size: '2x', bytes: bytes.byteLength,
      sha256: createHash('sha256').update(bytes).digest('hex') }, null, 2));
  } catch (error) {
    console.error(JSON.stringify({ error: error.code || 'unavailable', stage: error.stage || '',
      upstreamStatus: error.upstreamStatus || 0 })); process.exitCode = 1;
  }
  process.exit(process.exitCode || 0);
}
const login = String(args[0] || 'v0kky').toLowerCase();
if (args.length > 1 || !/^[a-z0-9_]{1,25}$/.test(login)) {
  console.error('Usage: npm run check:live -- <twitch-login>'); process.exit(2);
}
try {
  const manifest = await loadChannelManifest(login);
  console.log(JSON.stringify({ channel: login, setId: manifest.setId,
    names: manifest.emotes.length, files: new Set(manifest.emotes.map(e => e.id)).size }, null, 2));
} catch (error) {
  console.error(JSON.stringify({ error: error.code || 'unavailable', stage: error.stage || '',
    upstreamStatus: error.upstreamStatus || 0 })); process.exitCode = 1;
}
