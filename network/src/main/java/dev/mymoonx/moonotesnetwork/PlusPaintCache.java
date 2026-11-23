package dev.mymoonx.moonotesnetwork;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Single-profile cache for normalized Chatterino+ PLUS paint data.
 *
 * This class NEVER fetches Chatterino+ data itself: the source adapter must
 * first be built from the owner's documented, permitted API contract.
 * It must not be fed a user-selected paint and presented as a PLUS entitlement.
 */
final class PlusPaintCache {
    private static final Gson JSON = new Gson();
    private static final int MAX_FILE = 96 * 1024;
    private static final long FRESH_FOR_MS = 6 * 60 * 60 * 1000L;
    private static final long KEEP_FOR_MS = 24 * 60 * 60 * 1000L;

    private static final class Holder {
        static final PlusPaintCache LOCAL = new PlusPaintCache(
            net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("moonotes-plus-paint-cache.json"));
    }
    static PlusPaintCache local() { return Holder.LOCAL; }

    private static final class Entry {
        String provider;
        String twitchId;
        String paintJson;
        long fetchedAt;
    }

    private final Path path;
    private Entry entry;
    private PaintStyle paint;

    PlusPaintCache(Path path) {
        this.path = path;
        read();
    }

    /**
     * The network adapter may call this only AFTER verifying the Twitch ID and
     * parsing a permitted, documented PLUS response. No such adapter ships yet.
     */
    synchronized void acceptFromOfficialApi(String twitchId, String normalizedJson, long now)
            throws IOException {
        if (!validTwitchId(twitchId) || now <= 0)
            throw new IOException("Invalid provider identity or time");
        if (normalizedJson == null
                || normalizedJson.getBytes(StandardCharsets.UTF_8).length > 65536)
            throw new IOException("Paint data exceeds 64 KiB");
        final PaintStyle parsed;
        try { parsed = PaintStyle.parse(normalizedJson); }
        catch (IllegalArgumentException e) { throw new IOException("Unsupported paint data", e); }
        Entry fresh = new Entry();
        fresh.provider = "chatterino-plus";
        fresh.twitchId = twitchId;
        fresh.paintJson = normalizedJson;
        fresh.fetchedAt = now;
        byte[] bytes = JSON.toJson(fresh).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE) throw new IOException("Cache too large");
        write(bytes);
        entry = fresh;
        paint = parsed;
    }

    synchronized PaintStyle get(String twitchId, long now) {
        if (paint == null || entry == null || !validTwitchId(twitchId)
                || !twitchId.equals(entry.twitchId) || now < entry.fetchedAt
                || now - entry.fetchedAt > KEEP_FOR_MS) return null;
        return paint;
    }

    synchronized boolean fresh(String twitchId, long now) {
        return get(twitchId, now) != null && now - entry.fetchedAt <= FRESH_FOR_MS;
    }

    synchronized void clear() {
        entry = null;
        paint = null;
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    private void read() {
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(path) > MAX_FILE) return;
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length > MAX_FILE) return;
            Entry saved = JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Entry.class);
            if (saved == null || !"chatterino-plus".equals(saved.provider)
                    || !validTwitchId(saved.twitchId)
                    || saved.fetchedAt <= 0 || saved.paintJson == null
                    || saved.paintJson.getBytes(StandardCharsets.UTF_8).length > 65536) return;
            PaintStyle parsed = PaintStyle.parse(saved.paintJson);
            entry = saved;
            paint = parsed;
        } catch (IOException | RuntimeException ignored) {
            // Corrupted/untrusted cache is ignored rather than affecting Minecraft.
        }
    }

    private void write(byte[] bytes) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(),
            "moonotes-plus-", ".tmp");
        try {
            Files.write(temp, bytes);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    private static boolean validTwitchId(String id) {
        return id != null && id.matches("[0-9]{1,24}");
    }
}
