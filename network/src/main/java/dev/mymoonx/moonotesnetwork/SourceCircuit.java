package dev.mymoonx.moonotesnetwork;

import java.util.function.LongSupplier;

/** Avoid waiting for the same unavailable origin once for every emote. */
final class SourceCircuit {
    private static final long COOLDOWN = 60_000_000_000L;
    private final LongSupplier clock;
    private long unavailableUntil;
    SourceCircuit() { this(System::nanoTime); }
    SourceCircuit(LongSupplier clock) { this.clock = clock; }
    synchronized void failedTransport() { unavailableUntil = clock.getAsLong() + COOLDOWN; }
    synchronized void reset() { unavailableUntil = 0; }
    synchronized boolean shouldTryDirect() { return unavailableUntil == 0 || clock.getAsLong() >= unavailableUntil; }
    synchronized long remainingSeconds() { return unavailableUntil == 0 ? 0 : Math.max(0, (unavailableUntil - clock.getAsLong() + 999_999_999L) / 1_000_000_000L); }
}
