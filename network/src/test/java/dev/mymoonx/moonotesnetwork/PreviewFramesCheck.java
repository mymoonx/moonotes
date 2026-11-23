package dev.mymoonx.moonotesnetwork;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Real raster fixtures cover the former emote cropping/aspect-ratio regression. */
public final class PreviewFramesCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        BufferedImage wide = fourCorners(120, 40);
        BufferedImage fitted = PreviewFrames.fit(wide, 0, 0, 120, 40, 128);
        bounds(fitted, 0, 42, 127, 84, "wide image keeps the complete frame and transparent padding");
        check(fitted.getRGB(0, 42) == 0xFFFF0000 && fitted.getRGB(127, 42) == 0xFF00FF00
                        && fitted.getRGB(0, 84) == 0xFF0000FF && fitted.getRGB(127, 84) == 0xFFFFFF00,
                "all four source corners survive fitting");

        BufferedImage tall = fourCorners(20, 120);
        BufferedImage thumbnail = PreviewFrames.fit(tall, 0, 0, 20, 120, 27);
        bounds(thumbnail, 11, 0, 15, 26, "portrait thumbnail preserves aspect ratio");

        BufferedImage narrow = new BufferedImage(1000, 1, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 1000; x++) narrow.setRGB(x, 0, 0xFF00FF00);
        bounds(PreviewFrames.fit(narrow, 0, 0, 1000, 1, 27), 0, 13, 26, 13,
                "very wide frames remain at least one pixel high");

        BufferedImage atlas = new BufferedImage(20, 40, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 20; x++)
            atlas.setRGB(x, y, x < 10 && y >= 20 ? 0xFF4455AA : 0xFFFF0000);
        BufferedImage frame = PreviewFrames.fit(atlas, 0, 20, 10, 20, 128);
        check(frame.getRGB(64, 64) == 0xFF4455AA && frame.getRGB(32, 0) == 0xFF4455AA
                        && frame.getRGB(95, 127) == 0xFF4455AA,
                "selected sprite frame excludes neighboring atlas cells");
        var metadata = PreviewFrames.metadata(20, 40, List.of("10", "20", "40", "50", "60", "0"));
        check(metadata.columns() == 2 && metadata.times().length == 3,
                "Streamotes sentinel is excluded from animation frame count");
        check(PreviewFrames.frameAt(metadata.times(), 39) == 0
                        && PreviewFrames.frameAt(metadata.times(), 40) == 1
                        && PreviewFrames.frameAt(metadata.times(), 90) == 2
                        && PreviewFrames.frameAt(metadata.times(), 150) == 0,
                "animation uses recorded durations and loops at the correct boundary");
        check(PreviewFrames.metadata(300, 90, null).width() == 300,
                "static image without timing file uses its entire width");
        reject(() -> PreviewFrames.metadata(21, 40, List.of("10", "20", "40", "50", "0")),
                "partial atlas cells are rejected");
        reject(() -> PreviewFrames.metadata(20, 40, List.of("10", "20", "40", "40", "40", "40", "40", "0")),
                "frame count may not exceed the decoded atlas");
        reject(() -> PreviewFrames.metadata(20, 40, List.of("10", "20", "-1", "40", "0")),
                "negative durations are rejected");
        List<String> longAnimation = new ArrayList<>(List.of("10", "20"));
        for (int i = 0; i < 300; i++) longAnimation.add("40");
        longAnimation.add("0");
        check(PreviewFrames.metadata(200, 400, longAnimation).times().length == 300,
                "long legitimate animations still show thumbnails and previews");
        List<String> oversized = new ArrayList<>(List.of("10", "20"));
        for (int i = 0; i < PreviewFrames.MAX_FRAMES + 1; i++) oversized.add("40");
        oversized.add("0");
        reject(() -> PreviewFrames.metadata(200, 400, oversized), "animation preparation is bounded");
        reject(() -> PreviewFrames.fit(atlas, 15, 0, 10, 20, 128), "out-of-bounds frame cannot sample another image");
        System.out.println("PreviewFramesCheck: " + checks + " checks passed");
    }

    private static BufferedImage fourCorners(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            image.setRGB(x, y, y < height / 2
                    ? x < width / 2 ? 0xFFFF0000 : 0xFF00FF00
                    : x < width / 2 ? 0xFF0000FF : 0xFFFFFF00);
        return image;
    }

    private static void bounds(BufferedImage image, int left, int top, int right, int bottom, String label) {
        int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if ((image.getRGB(x, y) >>> 24) != 0) {
                minX = Math.min(minX, x); minY = Math.min(minY, y);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            }
        }
        check(minX == left && minY == top && maxX == right && maxY == bottom, label);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    private static void reject(Throwing action, String label) throws Exception {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (IOException expected) {
            checks++;
        }
    }

    @FunctionalInterface private interface Throwing { void run() throws Exception; }
}
