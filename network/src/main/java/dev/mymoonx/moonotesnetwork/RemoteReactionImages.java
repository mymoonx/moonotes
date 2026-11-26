package dev.mymoonx.moonotesnetwork;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Fetch only small, explicitly received reaction images via the existing
 * bounded 7TV source pipeline, never arbitrary peer-provided URLs or chat.
 * Cache writes use Streamotes' native PNG format and existing validation.
 */
final class RemoteReactionImages {
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(
        0, 1, 20, TimeUnit.SECONDS, new ArrayBlockingQueue<>(3), job -> {
            Thread thread = new Thread(job, "Moonotes-Remote-Reactions");
            thread.setDaemon(true);
            return thread;
        });
    private static final LinkedHashMap<String, Long> REQUESTED = new LinkedHashMap<>();
    private static final ImageDownloader.Observer OBSERVER = new ImageDownloader.Observer() {
        public void success(String source, long millis) { }
        public void fallback() { }
        public void failure(String category) { }
    };
    private static final ImageDownloader DOWNLOADER = new ImageDownloader(
        "https://moonotes-emotes.thefreelom.workers.dev", OBSERVER);
    private RemoteReactionImages() { }

    static synchronized void request(String id) {
        if (id == null || !id.matches("[A-Za-z0-9]{16,40}")) return;
        if (CacheFiles.looksValid(CacheFiles.forId(id))) return;
        long now = System.currentTimeMillis();
        Long previous = REQUESTED.get(id);
        if (previous != null && now - previous < 60_000) return;
        if (REQUESTED.size() >= 32) {
            var eldest = REQUESTED.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
        REQUESTED.put(id, now);
        try {
            WORKER.execute(() -> {
                try {
                    if (CacheFiles.looksValid(CacheFiles.forId(id))) return;
                    var source = URI.create("https://cdn.7tv.app/emote/" + id + "/2x.webp");
                    var image = DOWNLOADER.fetch(source, "auto", false, () -> false, n -> {});
                    StreamotesCacheWriter.save(id, image.data());
                } catch (Exception ignored) {
                    // Reaction stays visible as a text fallback, and can retry later.
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) { }
    }
}
