package dev.mymoonx.moonotesnetwork;

/** Perspective sizing for a half-block reaction billboard, in GUI pixels. */
final class ReactionProjection {
    static final double WORLD_SIZE = 0.5;
    static final double MAX_DISTANCE = 32;
    private static final int MAX_GUI_SIZE = 40;
    record Placement(int x, int y, int size) { }

    private ReactionProjection() { }

    /**
     * clipW is camera-space depth, not distance from the local player's entity.
     * This distinction matters in third person and with a detached camera.
     * verticalScale is the length of the projection-view matrix's Y row.
     */
    static Placement project(double clipX, double clipY, double clipW,
            double verticalScale, double cameraDistanceSquared, int width, int height) {
        if (width <= 0 || height <= 0 || !Double.isFinite(clipX) || !Double.isFinite(clipY)
                || !Double.isFinite(clipW) || clipW <= 0.05
                || !Double.isFinite(verticalScale) || verticalScale <= 0
                || !Double.isFinite(cameraDistanceSquared) || cameraDistanceSquared < 0
                || cameraDistanceSquared > MAX_DISTANCE * MAX_DISTANCE) return null;

        double pixels = height * 0.5 * verticalScale * WORLD_SIZE / clipW;
        // Never enlarge a distant emote to a fixed minimum size. Tiny ones disappear.
        if (!Double.isFinite(pixels) || pixels < 2) return null;
        int size = (int) Math.round(Math.min(MAX_GUI_SIZE, pixels));
        double centerX = width * (0.5 + clipX / clipW * 0.5);
        double centerY = height * (0.5 - clipY / clipW * 0.5);
        if (centerX + size / 2.0 < 0 || centerX - size / 2.0 > width
                || centerY + size / 2.0 < 0 || centerY - size / 2.0 > height) return null;
        return new Placement((int) Math.round(centerX - size / 2.0),
            (int) Math.round(centerY - size / 2.0), size);
    }
}
