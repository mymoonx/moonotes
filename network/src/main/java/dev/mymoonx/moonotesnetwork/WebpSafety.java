package dev.mymoonx.moonotesnetwork;

import java.io.IOException;

/**
 * Bounded container/header preflight before invoking the runtime image decoder.
 * This does not validate entropy-coded pixel data or replace a maintained decoder.
 * Format: https://developers.google.com/speed/webp/docs/riff_container
 */
final class WebpSafety {
    static final int MAX_DIMENSION = 2048;
    static final int MAX_FRAMES = 1024;
    static final long MAX_ATLAS_PIXELS = 16_000_000L;
    private static final int MAX_CHUNKS = 8192;
    private static final int VP8X = code("VP8X"), VP8 = code("VP8 "), VP8L = code("VP8L");
    private static final int ANIM = code("ANIM"), ANMF = code("ANMF"), ALPH = code("ALPH");
    record Info(int width, int height, int frames, long atlasPixels) { }
    private record Dimensions(int width, int height) { }
    private record Chunk(int type, int data, int size, int end, int next) { }
    private WebpSafety() { }

    static Info validate(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 20 || bytes.length > 3 * 1024 * 1024)
            throw new IOException("WebP size limit or truncated header");
        if (fourcc(bytes, 0) != code("RIFF") || fourcc(bytes, 8) != code("WEBP")) throw invalid("signature");
        long declared = u32(bytes, 4) + 8L;
        if (declared != bytes.length || (declared & 1L) != 0) throw invalid("RIFF length");
        int width = 0, height = 0, frames = 0, stillImages = 0;
        boolean extended = false, animated = false, animationHeader = false;
        Chunk alpha = null;
        int[] chunkCount = {0};
        for (int offset = 12; offset < bytes.length;) {
            Chunk chunk = chunk(bytes, offset, bytes.length, chunkCount);
            if (chunk.type == VP8X) {
                if (extended || offset != 12 || chunk.size < 10) throw invalid("VP8X layout");
                extended = true;
                animated = (bytes[chunk.data] & 2) != 0;
                width = u24(bytes, chunk.data + 4) + 1;
                height = u24(bytes, chunk.data + 7) + 1;
                checkDimensions(width, height);
            } else if (chunk.type == ANIM) {
                if (!extended || !animated || animationHeader || frames != 0 || chunk.size != 6)
                    throw invalid("ANIM layout");
                animationHeader = true;
            } else if (chunk.type == ANMF) {
                if (!animated || !animationHeader || stillImages != 0 || chunk.size < 16) throw invalid("ANMF layout");
                if (++frames > MAX_FRAMES) throw new IOException("WebP frame count size limit exceeded");
                int x = u24(bytes, chunk.data) * 2, y = u24(bytes, chunk.data + 3) * 2;
                int frameWidth = u24(bytes, chunk.data + 6) + 1;
                int frameHeight = u24(bytes, chunk.data + 9) + 1;
                checkDimensions(frameWidth, frameHeight);
                if ((long) x + frameWidth > width || (long) y + frameHeight > height) throw invalid("frame outside canvas");
                validateFrame(bytes, chunk.data + 16, chunk.end, frameWidth, frameHeight, chunkCount);
                checkAtlas(width, height, frames);
            } else if (chunk.type == ALPH) {
                if (animated || alpha != null || stillImages != 0) throw invalid("ALPH layout");
                alpha = chunk;
            } else if (chunk.type == VP8 || chunk.type == VP8L) {
                if (animated || ++stillImages != 1) throw invalid("still image layout");
                Dimensions dimensions = bitstreamDimensions(bytes, chunk);
                if (extended && (width != dimensions.width || height != dimensions.height)) throw invalid("canvas/bitstream dimensions differ");
                width = dimensions.width; height = dimensions.height;
                validateAlpha(bytes, alpha, chunk.type, width, height);
            }
            offset = chunk.next;
        }
        if (animated) {
            if (!animationHeader || frames == 0 || stillImages != 0) throw invalid("missing animation frames");
        } else {
            if (stillImages != 1 || frames != 0) throw invalid("missing still image");
            frames = 1;
        }
        return new Info(width, height, frames, checkAtlas(width, height, frames));
    }

    private static void validateFrame(byte[] bytes, int start, int end, int width, int height, int[] count) throws IOException {
        int images = 0;
        Chunk alpha = null;
        for (int offset = start; offset < end;) {
            Chunk chunk = chunk(bytes, offset, end, count);
            if (chunk.type == ALPH) {
                if (alpha != null || images != 0) throw invalid("frame ALPH layout");
                alpha = chunk;
            } else if (chunk.type == VP8 || chunk.type == VP8L) {
                if (++images != 1) throw invalid("multiple frame bitstreams");
                Dimensions dimensions = bitstreamDimensions(bytes, chunk);
                if (dimensions.width != width || dimensions.height != height) throw invalid("frame/bitstream dimensions differ");
                validateAlpha(bytes, alpha, chunk.type, width, height);
            } else if (chunk.type == VP8X || chunk.type == ANIM || chunk.type == ANMF) {
                throw invalid("nested animation container");
            }
            offset = chunk.next;
        }
        if (images != 1) throw invalid("missing frame bitstream");
    }

    private static Dimensions bitstreamDimensions(byte[] bytes, Chunk chunk) throws IOException {
        int width, height, p = chunk.data;
        if (chunk.type == VP8) {
            if (chunk.size < 10 || (bytes[p] & 1) != 0 || (bytes[p + 3] & 255) != 0x9d
                    || bytes[p + 4] != 1 || bytes[p + 5] != 0x2a) throw invalid("VP8 frame header");
            width = u16(bytes, p + 6) & 0x3fff;
            height = u16(bytes, p + 8) & 0x3fff;
        } else {
            if (chunk.size < 5 || (bytes[p] & 255) != 0x2f) throw invalid("VP8L frame header");
            long bits = u32(bytes, p + 1);
            if ((bits >>> 29) != 0) throw invalid("VP8L version");
            width = (int) (bits & 0x3fff) + 1;
            height = (int) ((bits >>> 14) & 0x3fff) + 1;
        }
        checkDimensions(width, height);
        return new Dimensions(width, height);
    }

    private static void validateAlpha(byte[] bytes, Chunk alpha, int imageType, int width, int height) throws IOException {
        if (alpha == null) return;
        if (imageType == VP8L || alpha.size < 1) throw invalid("alpha bitstream layout");
        int compression = bytes[alpha.data] & 3;
        if (compression > 1) throw invalid("alpha compression");
        if (compression == 0 && alpha.size - 1L != (long) width * height) throw invalid("raw alpha length");
    }
    private static Chunk chunk(byte[] bytes, int start, int limit, int[] count) throws IOException {
        if (++count[0] > MAX_CHUNKS) throw new IOException("WebP chunk count size limit exceeded");
        if (limit - start < 8) throw invalid("truncated chunk header");
        long size = u32(bytes, start + 4);
        long end = start + 8L + size, next = end + (size & 1L);
        if (end > limit || next > limit) throw invalid("chunk length");
        if ((size & 1L) != 0 && bytes[(int) end] != 0) throw invalid("chunk padding");
        return new Chunk(fourcc(bytes, start), start + 8, (int) size, (int) end, (int) next);
    }
    private static void checkDimensions(int width, int height) throws IOException {
        if (width < 1 || height < 1) throw invalid("zero dimensions");
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) throw new IOException("WebP dimension size limit exceeded");
    }
    private static long checkAtlas(int width, int height, int frames) throws IOException {
        checkDimensions(width, height);
        int columns = 1;
        while (columns * columns < frames) columns++;
        int rows = (frames + columns - 1) / columns;
        long pixels = (long) width * height * columns * rows;
        if (pixels > MAX_ATLAS_PIXELS) throw new IOException("WebP decoded atlas size limit exceeded");
        return pixels;
    }
    private static int u16(byte[] bytes, int p) { return (bytes[p] & 255) | ((bytes[p + 1] & 255) << 8); }
    private static int u24(byte[] bytes, int p) { return u16(bytes, p) | ((bytes[p + 2] & 255) << 16); }
    private static long u32(byte[] bytes, int p) { return Integer.toUnsignedLong(fourcc(bytes, p)); }
    private static int fourcc(byte[] bytes, int p) { return u24(bytes, p) | ((bytes[p + 3] & 255) << 24); }
    private static int code(String s) { return s.charAt(0) | (s.charAt(1) << 8) | (s.charAt(2) << 16) | (s.charAt(3) << 24); }
    private static IOException invalid(String reason) { return new IOException("Invalid WebP " + reason); }
}
