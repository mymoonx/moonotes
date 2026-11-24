package dev.mymoonx.moonotesnetwork;

/** Reserves separate rows for controls, the ring, names and action hints. */
record WheelLayout(int centerX, int centerY, int inner, int outer, int icon, int nameY) {
    static WheelLayout of(int width, int height) {
        int top = 40, bottom = height - 68;
        int radius = Math.max(12, Math.min(140, Math.min((width - 24) / 2, (bottom - top) / 2)));
        int inner = Math.max(8, (int) (radius * .43));
        int icon = Math.max(8, Math.min(34, radius - inner - 8));
        return new WheelLayout(width / 2, (top + bottom) / 2, inner, radius, icon, height - 60);
    }

    int middle() { return (inner + outer) / 2; }
    int iconX(int slot) {
        return centerX + (int) Math.round(middle() * Math.sin(slot * Math.PI / 4)) - icon / 2;
    }
    int iconY(int slot) {
        return centerY - (int) Math.round(middle() * Math.cos(slot * Math.PI / 4)) - icon / 2;
    }
    int hit(double x, double y) { return WheelGeometry.slot(x - centerX, y - centerY, inner, outer); }
}
