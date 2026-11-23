package dev.mymoonx.moonotesnetwork;

/**
 * Small, deterministic, allocation-free colour interpolation core for paint
 * previews. No network or Twitch/Chatterino+ dependency is required.
 *
 * More advanced PLUS layer data can be mapped into this abstraction after the
 * documented API contract and authorisation have been verified.
 */
final class PaintGradient {
    private PaintGradient() {}

    static int color(int[] stops, double position) {
        if (stops == null || stops.length < 1 || stops.length > 16)
            throw new IllegalArgumentException("1..16 gradient stops required");
        if (stops.length == 1) return (stops[0] | 0xff000000);
        if (!Double.isFinite(position)) position = 0;
        double x = Math.max(0.0, Math.min(1.0, position)) * (stops.length - 1);
        int index = Math.min(stops.length - 2, (int)Math.floor(x));
        double t = x - index;
        int a = stops[index], b = stops[index + 1];
        int red = lerp((a >>> 16) & 0xff, (b >>> 16) & 0xff, t);
        int green = lerp((a >>> 8) & 0xff, (b >>> 8) & 0xff, t);
        int blue = lerp(a & 0xff, b & 0xff, t);
        return 0xff000000 | red << 16 | green << 8 | blue;
    }
    private static int lerp(int a, int b, double t) {
        return Math.max(0, Math.min(255, (int)Math.round(a + (b - a)*t)));
    }
    static int animated(int[] stops, double position, long milliseconds, boolean animation) {
        if (!animation) return color(stops, position);
        // 4-second ping-pong sweep, deterministic and independent of FPS.
        long frame = Math.floorMod(milliseconds, 4000);
        double phase = frame < 2000 ? frame / 2000.0 : 2 - frame / 2000.0;
        return color(stops, Math.max(0, Math.min(1, position * 0.72 + phase * 0.28)));
    }
}
