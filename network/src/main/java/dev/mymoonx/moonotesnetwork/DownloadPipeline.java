package dev.mymoonx.moonotesnetwork;

import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** A coordinator-owned bounded window; consumed Future results are never retained. */
final class DownloadPipeline<R> implements AutoCloseable {
    private final int limit;
    private final ExecutorService workers;
    private final ExecutorCompletionService<R> completions;
    private final HashSet<Future<R>> pending = new HashSet<>();

    DownloadPipeline(int limit) {
        if (limit < 1 || limit > 6) throw new IllegalArgumentException("Download limit outside 1..6");
        this.limit = limit;
        workers = Executors.newFixedThreadPool(limit, task -> {
            Thread thread = new Thread(task, "Moonotes-Image-Download");
            thread.setDaemon(true); return thread;
        });
        completions = new ExecutorCompletionService<>(workers);
    }
    int pendingCount() { return pending.size(); }
    void submit(Callable<R> task) {
        if (pending.size() >= limit) throw new IllegalStateException("Download window is full");
        pending.add(completions.submit(task));
    }
    R poll(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException {
        Future<R> completed = completions.poll(timeout, unit);
        if (completed == null) return null;
        // FutureTask retains its byte[] result: removing it here is essential.
        pending.remove(completed);
        return completed.get();
    }
    @Override public void close() {
        for (Future<R> future : pending) future.cancel(true);
        pending.clear();
        workers.shutdownNow();
        try { workers.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException ignored) { Thread.interrupted(); }
        while (completions.poll() != null) { /* release queued result references */ }
    }
}
