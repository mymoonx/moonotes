package dev.mymoonx.moonotesnetwork;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ephemeral, local-only BTTV render probes. Counters contain no emote names,
 * chat messages, player identifiers or server addresses.
 *
 * Their purpose is to locate a missing runtime hook: the parser can pass
 * offline regression checks even if a Minecraft render/mixin injection did
 * not actually execute.
 */
public final class BttvRenderProbe {
    private static final int MAX = 1_000_000;
    private static final AtomicInteger INPUT = new AtomicInteger();
    private static final AtomicInteger CHAT = new AtomicInteger();
    private static final AtomicInteger GLYPH_MARKER = new AtomicInteger();
    private static final AtomicInteger GLYPH_WRAP = new AtomicInteger();
    private static final AtomicInteger DRAW = new AtomicInteger();

    private BttvRenderProbe() { }

    private static void bump(AtomicInteger counter) {
        counter.getAndUpdate(n -> n < MAX ? n + 1 : n);
    }
    public static void input() { bump(INPUT); }
    public static void chat() { bump(CHAT); }
    public static void marker() { bump(GLYPH_MARKER); }
    public static void wrapped() { bump(GLYPH_WRAP); }
    public static void drawn() { bump(DRAW); }

    public static String report() {
        return "BTTV input/chat/marker/wrap/draw: "
            + INPUT.get() + "/" + CHAT.get() + "/" + GLYPH_MARKER.get()
            + "/" + GLYPH_WRAP.get() + "/" + DRAW.get()
            + "\nNo chat contents are collected.";
    }
}
