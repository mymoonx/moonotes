package dev.mymoonx.moonotesnetwork;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Requests only missing images actually used by enabled sign/book renderers. */
final class WorldImageRequests {
    private static final Budget BUDGET = new Budget();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(2), runnable -> {
            Thread thread = new Thread(runnable, "Moonotes-World-Images");
            thread.setDaemon(true);
            return thread;
        });
    // Reflection and upstream registry access run only on WORKER, never during extraction.
    private static Api api;
    private static long apiRetryAt;
    private WorldImageRequests() { }

    static void request(EmoteTextTokens.Match match) {
        if (match == null || match.id() == null || !match.id().matches("[A-Za-z0-9]{16,40}")
                || match.name() == null || !match.name().matches("[A-Za-z0-9_]{1,70}")) return;
        String id = match.id().toLowerCase(Locale.ROOT);
        if (!BUDGET.acquire(id, System.nanoTime())) return;
        long generation = GENERATION.get();
        try { WORKER.execute(() -> requestRegistered(match.name(), id, generation)); }
        catch (RejectedExecutionException ignored) {
            // Keep the rate-limit reservation; never fall back to I/O on the render thread.
        }
    }

    private static void requestRegistered(String name, String id, long generation) {
        if (generation != GENERATION.get()) return;
        try {
            if (api == null) {
                if (apiRetryAt != 0 && System.nanoTime() - apiRetryAt < 0) return;
                api = new Api();
            }
            synchronized (api.registryLock) {
                if (generation != GENERATION.get()) return;
                Object emote = api.fromName.invoke(null, name);
                // A native provider can own the same textual alias for another image.
                if (emote == null || !RegistrationPlan.sameImage(api.loadData.invoke(emote), id)) return;
                // Streamotes queues its existing image loader; it owns cache/network policy.
                api.requestTexture.invoke(emote);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            apiRetryAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            api = null;
        }
    }

    static void reset() {
        GENERATION.incrementAndGet();
        WORKER.getQueue().clear();
        BUDGET.reset();
    }

    private static final class Api {
        final Method fromName, loadData, requestTexture;
        final Object registryLock;
        Api() throws ReflectiveOperationException {
            Class<?> registry = Class.forName("xeed.mc.streamotes.emoticon.EmoticonRegistry");
            Class<?> emote = Class.forName("xeed.mc.streamotes.emoticon.Emoticon");
            fromName = registry.getMethod("fromName", String.class);
            loadData = emote.getMethod("getLoadData");
            requestTexture = emote.getMethod("requestTexture");
            Field lock = registry.getDeclaredField("loadingLock");
            lock.setAccessible(true);
            registryLock = lock.get(null);
        }
    }

    /** Rolling rate window and retry timestamps have a strict memory bound. */
    static final class Budget {
        private static final long SECOND = TimeUnit.SECONDS.toNanos(1);
        private static final long RETRY = TimeUnit.SECONDS.toNanos(5);
        private static final int MAX_TRACKED = 64;
        private final LinkedHashMap<String, Long> last = new LinkedHashMap<>();
        private final ArrayDeque<Long> recent = new ArrayDeque<>(2);

        synchronized boolean acquire(String id, long now) {
            Long previous = last.get(id);
            if (previous != null && now - previous < RETRY) return false;
            while (!recent.isEmpty() && now - recent.peekFirst() >= SECOND) recent.removeFirst();
            if (recent.size() >= 2) return false;
            if (previous == null && last.size() >= MAX_TRACKED) {
                var oldest = last.entrySet().iterator();
                if (!oldest.hasNext() || now - oldest.next().getValue() < RETRY) return false;
                oldest.remove();
            }
            last.remove(id);
            last.put(id, now);
            recent.addLast(now);
            return true;
        }
        synchronized int tracked() { return last.size(); }
        synchronized void reset() { last.clear(); recent.clear(); }
    }
}
