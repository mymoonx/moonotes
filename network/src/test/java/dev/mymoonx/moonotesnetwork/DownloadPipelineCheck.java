package dev.mymoonx.moonotesnetwork;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Verify the real executor window, result release, and cancellation. */
public final class DownloadPipelineCheck {
    public static void main(String[] args) throws Exception {
        int checks = 0;
        CountDownLatch start = new CountDownLatch(1);
        try (var pipeline = new DownloadPipeline<byte[]>(2)) {
            pipeline.submit(() -> { start.await(); return new byte[1024]; });
            pipeline.submit(() -> { start.await(); return new byte[1024]; });
            try { pipeline.submit(() -> new byte[1024]); throw new AssertionError("unbounded submission accepted"); }
            catch (IllegalStateException expected) { checks++; }
            start.countDown();
            if (pipeline.poll(2, TimeUnit.SECONDS) == null || pipeline.pendingCount() != 1)
                throw new AssertionError("consumed Future retained");
            checks++;
            pipeline.submit(() -> new byte[1024]);
            if (pipeline.pendingCount() != 2) throw new AssertionError("window was not replenished");
            checks++;
            for (int i = 0; i < 2; i++) if (pipeline.poll(2, TimeUnit.SECONDS) == null) throw new AssertionError("missing result");
            if (pipeline.pendingCount() != 0) throw new AssertionError("completed batch retains results");
            checks++;
        }
        var pipeline = new DownloadPipeline<byte[]>(1);
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        pipeline.submit(() -> {
            entered.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException stop) { interrupted.set(true); throw stop; }
            return new byte[1];
        });
        if (!entered.await(2, TimeUnit.SECONDS)) throw new AssertionError("worker did not start");
        pipeline.close();
        if (!interrupted.get() || pipeline.pendingCount() != 0) throw new AssertionError("cancellation did not release pending work");
        checks++;
        System.out.println("Download pipeline checks passed (" + checks + " checks)");
    }
}
