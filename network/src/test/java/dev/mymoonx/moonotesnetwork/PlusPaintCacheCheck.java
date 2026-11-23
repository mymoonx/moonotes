package dev.mymoonx.moonotesnetwork;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Offline regression tests; no Twitch account or PLUS service is contacted. */
public final class PlusPaintCacheCheck {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("moonotes-plus-test-");
        Path file = dir.resolve("plus-cache.json");
        PlusPaintCache cache = new PlusPaintCache(file);
        final long now = 1_800_000_000_000L;
        final String id = "123456789";
        final String gradient = """
            {"name":"Fixture","gradients":[{"function":"LINEAR_GRADIENT",
             "stops":[{"at":0,"color":"#112233"},{"at":1,"color":"#99AABB"}]}]}
            """;

        require(cache.get(id, now) == null, "empty cache");
        fail(() -> cache.acceptFromOfficialApi("not-id", gradient, now));
        fail(() -> cache.acceptFromOfficialApi(id, "[]", now));
        fail(() -> cache.acceptFromOfficialApi(id, "x".repeat(65537), now));
        fail(() -> cache.acceptFromOfficialApi(id, """
            {"gradients":[{"function":"URL","image_url":"https://example.com/image.webp"}]}
            """, now));

        cache.acceptFromOfficialApi(id, gradient, now);
        require(cache.get(id, now) != null, "saved paint");
        require(cache.fresh(id, now + 3600000), "fresh paint");
        require(!cache.fresh(id, now + 7L * 3600000), "refresh needed");
        require(cache.get(id, now + 25L * 3600000) == null, "expired");
        require(cache.get("99999", now) == null, "no cross-account leak");
        require(cache.get(id, now - 1) == null, "future timestamp");

        PlusPaintCache restored = new PlusPaintCache(file);
        require(restored.get(id, now + 1000) != null, "read persisted cache");
        restored.clear();
        require(!Files.exists(file), "clear deletes stored Twitch data");
        require(restored.get(id, now) == null, "clear memory");

        Files.writeString(file, "{\"provider\":\"chatterino-plus\",\"twitchId\":\"evil\"}");
        PlusPaintCache corrupted = new PlusPaintCache(file);
        require(corrupted.get(id, now) == null, "invalid payload ignored");

        System.out.println("PlusPaintCacheCheck: PASS");
    }
    @FunctionalInterface interface Task { void run() throws Exception; }
    private static void fail(Task task) throws Exception {
        try { task.run(); }
        catch (IOException expected) { return; }
        throw new AssertionError("Expected input rejection");
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
