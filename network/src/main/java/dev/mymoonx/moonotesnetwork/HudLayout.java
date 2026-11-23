package dev.mymoonx.moonotesnetwork;

/** Pure layout shared by live HUD and its editor; all values use scaled GUI coordinates. */
final class HudLayout {
    static final int MARGIN = 8;
    static final int WIDTH = 218;
    static final int PROGRESS_HEIGHT = 34;
    static final int GAP = 5;

    record Placement(boolean custom, int corner, double x, double y) {
        Placement {
            corner = corner >= 0 && corner <= 3 ? corner : 1;
            x = finiteFraction(x, 1);
            y = finiteFraction(y, 0.12);
        }
        static Placement corner(int corner) { return new Placement(false, corner, 1, 0.12); }
        static Placement defaults() { return corner(1); }
    }
    record Cards(MoonotesLayout.Rect progress, MoonotesLayout.Rect notice) {
        MoonotesLayout.Rect primary() { return progress != null ? progress : notice; }
    }

    private HudLayout() { }

    static Cards cards(int screenWidth, int screenHeight, Placement placement,
                       boolean progressVisible, int noticeHeight) {
        if (screenWidth < 100 || screenHeight < 80) return new Cards(null, null);
        int width = Math.min(WIDTH, screenWidth - MARGIN * 2);
        int maxBottom = Math.max(MARGIN + PROGRESS_HEIGHT, screenHeight - 42);
        int nh = Math.max(0, Math.min(noticeHeight, maxBottom - MARGIN));
        int primaryHeight = progressVisible ? PROGRESS_HEIGHT : nh;
        if (primaryHeight <= 0) return new Cards(null, null);
        int maxX = Math.max(MARGIN, screenWidth - MARGIN - width);
        int maxY = Math.max(MARGIN, maxBottom - primaryHeight);
        boolean bottom = placement.custom ? placement.y >= 0.5 : placement.corner >= 2;
        boolean left = placement.corner == 0 || placement.corner == 2;
        int x = placement.custom ? interpolate(MARGIN, maxX, placement.x) : left ? MARGIN : maxX;
        // The default upper anchor leaves room for the usual status-effect icons.
        int y = placement.custom ? interpolate(MARGIN, maxY, placement.y)
            : bottom ? maxY : Math.min(58, maxY);
        MoonotesLayout.Rect primary = new MoonotesLayout.Rect(x, y, width, primaryHeight);
        if (!progressVisible) return new Cards(null, primary);
        if (nh == 0) return new Cards(primary, null);

        // Progress owns the anchor. A toast appears beside it, never in a reserved empty slot.
        int below = y + PROGRESS_HEIGHT + GAP;
        int above = y - nh - GAP;
        int noticeY;
        if (bottom && above >= MARGIN) noticeY = above;
        else if (!bottom && below + nh <= maxBottom) noticeY = below;
        else if (above >= MARGIN) noticeY = above;
        else if (below + nh <= maxBottom) noticeY = below;
        else if (PROGRESS_HEIGHT + GAP + nh <= maxBottom - MARGIN) {
            y = bottom ? maxBottom - PROGRESS_HEIGHT : MARGIN;
            primary = new MoonotesLayout.Rect(x, y, width, PROGRESS_HEIGHT);
            noticeY = bottom ? y - nh - GAP : y + PROGRESS_HEIGHT + GAP;
        } else {
            // At an unusually small GUI size the progress remains readable; history keeps the notice.
            return new Cards(primary, null);
        }
        return new Cards(primary, new MoonotesLayout.Rect(x, noticeY, width, nh));
    }

    static Placement moved(int screenWidth, int screenHeight, double x, double y) {
        int width = Math.min(WIDTH, Math.max(1, screenWidth - MARGIN * 2));
        int rangeX = Math.max(0, screenWidth - MARGIN * 2 - width);
        int rangeY = Math.max(0, screenHeight - 42 - PROGRESS_HEIGHT - MARGIN);
        double nx = rangeX == 0 ? 0.5 : finiteFraction((x - MARGIN) / rangeX, 1);
        double ny = rangeY == 0 ? 0 : finiteFraction((y - MARGIN) / rangeY, 0.12);
        int corner = (ny >= 0.5 ? 2 : 0) + (nx >= 0.5 ? 1 : 0);
        return new Placement(true, corner, nx, ny);
    }

    static double finiteFraction(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : fallback;
    }
    private static int interpolate(int min, int max, double fraction) {
        return min + (int) Math.round(Math.max(0, max - min) * fraction);
    }
}
