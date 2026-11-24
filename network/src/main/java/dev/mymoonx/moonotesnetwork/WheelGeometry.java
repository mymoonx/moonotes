package dev.mymoonx.moonotesnetwork;

import java.util.ArrayList;
import java.util.List;

/** Precomputed pixel spans for a genuine eight-segment radial menu. */
final class WheelGeometry {
    static final int SLOTS = 8;
    record Span(int from, int to, int y, int slot, int edge) { }
    private WheelGeometry() {}

    static int slot(double dx, double dy, int inner, int outer) {
        double squared = dx * dx + dy * dy;
        if (squared < (double)inner * inner || squared >= (double)outer * outer) return -1;
        double angle = Math.atan2(dx, -dy); // zero at twelve o'clock, clockwise
        return Math.floorMod((int)Math.floor((angle + Math.PI / 8) / (Math.PI / 4)), SLOTS);
    }

    static List<Span> spans(int inner, int outer) {
        if (inner < 1 || outer <= inner || outer > 256)
            throw new IllegalArgumentException("Bad wheel radii");
        List<Span> output = new ArrayList<>();
        for (int dy = -outer; dy < outer; dy++) {
            int start = -outer;
            int current = -2, edge = 0;
            for (int x = -outer; x <= outer; x++) {
                int value = x == outer ? -2 : slot(x, dy, inner, outer);
                int border = 0;
                if (value >= 0) {
                    int r2 = x*x + dy*dy;
                    if (r2 < (inner+2)*(inner+2)) border = 1;
                    if (r2 >= (outer-3)*(outer-3)) border = 2;
                    double normalized = Math.atan2(x, -dy) + Math.PI/8;
                    double frac = normalized / (Math.PI/4) - Math.floor(normalized/(Math.PI/4));
                    if (frac < .012 || frac > .988) border = 3;
                }
                if (x > start && (value != current || border != edge)) {
                    if (current >= 0) output.add(new Span(start, x, dy, current, edge));
                    start = x; current = value; edge = border;
                } else if (x == start) {
                    current = value; edge = border;
                }
            }
        }
        return List.copyOf(output);
    }
}
