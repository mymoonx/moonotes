package dev.mymoonx.moonotesnetwork;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/** Pure image/layout logic, shared by the asynchronous preview loader and checks. */
final class PreviewFrames {
    static final int MAX_FRAMES = 1024;

    private PreviewFrames() { }

    record Metadata(int width, int height, int columns, int[] times) { }

    static Metadata metadata(int sheetWidth, int sheetHeight, List<String> lines) throws IOException {
        if (sheetWidth < 1 || sheetHeight < 1) throw new IOException("Invalid sprite dimensions");
        if (lines == null) return new Metadata(sheetWidth, sheetHeight, 1, new int[] { 1000 });
        if (lines.size() < 4 || lines.size() > MAX_FRAMES + 3)
            throw new IOException("Invalid animation metadata length");
        try {
            int width = Integer.parseInt(lines.get(0).strip());
            int height = Integer.parseInt(lines.get(1).strip());
            if (width < 1 || height < 1 || width > 2048 || height > 2048
                    || sheetWidth % width != 0 || sheetHeight % height != 0)
                throw new IOException("Invalid animation frame dimensions");
            int end = lines.size();
            if (lines.get(end - 1).strip().equals("0")) end--;
            int frames = end - 2;
            if (frames < 1 || frames > MAX_FRAMES
                    || (long) (sheetWidth / width) * (sheetHeight / height) < frames)
                throw new IOException("Animation frames exceed sprite bounds");
            int[] times = new int[frames];
            for (int i = 0; i < frames; i++) {
                int time = Integer.parseInt(lines.get(i + 2).strip());
                if (time < 0) throw new IOException("Invalid animation duration");
                times[i] = Math.max(30, Math.min(10_000, time));
            }
            return new Metadata(width, height, sheetWidth / width, times);
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid animation metadata", ex);
        }
    }

    static BufferedImage fit(BufferedImage source, int sx, int sy, int sw, int sh, int target)
            throws IOException {
        if (target < 1 || target > 256 || sw < 1 || sh < 1 || sx < 0 || sy < 0
                || (long) sx + sw > source.getWidth() || (long) sy + sh > source.getHeight())
            throw new IOException("Invalid sprite frame bounds");
        BufferedImage square = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = square.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale = Math.min(target / (double) sw, target / (double) sh);
            int width = Math.min(target, Math.max(1, (int) Math.round(sw * scale)));
            int height = Math.min(target, Math.max(1, (int) Math.round(sh * scale)));
            int x = (target - width) / 2, y = (target - height) / 2;
            graphics.drawImage(source, x, y, x + width, y + height, sx, sy, sx + sw, sy + sh, null);
        } finally {
            graphics.dispose();
        }
        return square;
    }

    static int frameAt(int[] times, long elapsedMillis) {
        long total = 0;
        for (int time : times) total += time;
        if (times.length < 2 || total <= 0) return 0;
        long offset = Math.floorMod(elapsedMillis, total);
        for (int i = 0; i < times.length; i++) {
            if (offset < times[i]) return i;
            offset -= times[i];
        }
        return 0;
    }
}
