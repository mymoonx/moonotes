package dev.mymoonx.moonotesnetwork;

import java.util.ArrayDeque;
import java.util.function.LongSupplier;

/** Rolling network throughput. Decoding time is not mislabeled as PNG write speed. */
final class TransferRate {
    private static final long WINDOW = 3_000_000_000L;
    private record Sample(long at, long bytes) {}
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private final LongSupplier clock;
    private long started;

    TransferRate() { this(System::nanoTime); }
    TransferRate(LongSupplier clock) { this.clock = clock; reset(); }
    synchronized void reset() { samples.clear(); started = clock.getAsLong(); }
    synchronized void add(long count) {
        if (count <= 0) return;
        long now = clock.getAsLong();
        prune(now);
        if (!samples.isEmpty() && now - samples.peekLast().at() < 100_000_000L) {
            Sample previous = samples.removeLast();
            samples.addLast(new Sample(previous.at(), previous.bytes() + count));
        } else samples.addLast(new Sample(now, count));
    }
    synchronized double bytesPerSecond() {
        long now = clock.getAsLong();
        prune(now);
        long bytes = 0;
        for (Sample sample : samples) bytes += sample.bytes();
        long duration = Math.max(250_000_000L, Math.min(WINDOW, now - started));
        return bytes * 1_000_000_000.0 / duration;
    }
    private void prune(long now) {
        while (!samples.isEmpty() && now - samples.peekFirst().at() >= WINDOW) samples.removeFirst();
    }
}
