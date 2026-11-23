package dev.mymoonx.moonotesnetwork;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;

/** Tiny real libwebp fixtures plus hostile container/header mutations. */
public final class WebpSafetyCheck {
    private static final byte[] LOSSLESS = hex("524946461e000000574542505650384c110000002f018000100750b25e14b9b48188e87f0000");
    private static final byte[] LOSSY = hex("524946463c000000574542505650382030000000d001009d012a0200030001402625a00274ba01f80003b000fef06cafff5c07e701f9c07f32dffe760d8976bd60000000");
    private static final byte[] ANIMATION = hex("524946468800000057454250565038580a00000012000000010000020000414e494d06000000000000000000414e4d462a000000000000000000010000020000500000025650384c110000002f018000100750b25e14b9b48188e87f0000414e4d462a0000000000000000000100000200005a0000025650384c110000002f0180001007508522f7a2b48188e87f0000");
    private static int checks;
    public static void main(String[] args) throws Exception {
        good(LOSSLESS, 2, 3, 1); good(LOSSY, 2, 3, 1); good(ANIMATION, 2, 3, 2);
        reject(null, "null");
        reject(new byte[19], "short RIFF");
        byte[] mutation = LOSSLESS.clone(); mutation[4]--; reject(mutation, "wrong RIFF length");
        mutation = LOSSLESS.clone(); mutation[16] = (byte) 255; reject(mutation, "chunk exceeds body");
        mutation = LOSSLESS.clone(); mutation[mutation.length - 1] = 1; reject(mutation, "nonzero padding");
        mutation = LOSSLESS.clone(); mutation[24] |= (byte) 0xe0; reject(mutation, "VP8L version");
        mutation = LOSSY.clone(); mutation[26] = 0; mutation[27] = 0; reject(mutation, "VP8 zero width");
        mutation = LOSSY.clone(); mutation[23] = 0; reject(mutation, "VP8 start code");
        reject(riff(vp8l(2049, 1)), "oversized simple bitstream");
        reject(riff(vp8x(2049, 1, false), vp8l(1, 1)), "oversized canvas");
        reject(riff(vp8x(2, 2, false), vp8l(2048, 2048)), "small canvas hiding large static bitstream");
        reject(animated(2, 2, frame(2, 2, 0, 0, vp8l(2048, 2048))), "small frame hiding large nested bitstream");
        reject(animated(2, 2, frame(2, 2, 1, 0, vp8l(2, 2))), "frame rectangle beyond canvas");
        reject(riff(vp8x(1, 1, true), chunk("ANIM", new byte[6])), "zero animation frames");
        reject(riff(vp8x(1, 1, false), vp8x(1, 1, false), vp8l(1, 1)), "duplicate extension");
        reject(riff(vp8l(1, 1), vp8l(1, 1)), "duplicate still images");
        byte[][] many = new byte[1024][];
        Arrays.fill(many, frame(1, 1, 0, 0, vp8l(1, 1)));
        good(animated(1, 1, many), 1, 1, 1024);
        byte[][] excessive = Arrays.copyOf(many, 1025); excessive[1024] = many[0];
        reject(animated(1, 1, excessive), "frame count cap");
        byte[][] atlas = new byte[4][];
        Arrays.fill(atlas, frame(2048, 2048, 0, 0, vp8l(2048, 2048)));
        reject(animated(2048, 2048, atlas), "expanded atlas cap");
        good(riff(chunk("JUNK", new byte[] {1}), Arrays.copyOfRange(LOSSLESS, 12, LOSSLESS.length)), 2, 3, 1);
        // A malformed nested chunk cannot borrow bytes from the following ANMF.
        byte[] inner = vp8l(1, 1); inner[4] = 100;
        reject(animated(1, 1, frame(1, 1, 0, 0, inner), many[0]), "nested chunk bounds");
        for (String arg : args) {
            var info = WebpSafety.validate(Files.readAllBytes(Path.of(arg)));
            System.out.println("Sample " + Path.of(arg).getFileName() + ": " + info);
        }
        System.out.println("WebP preflight checks passed (" + checks + " checks)");
    }
    private static void good(byte[] data, int w, int h, int frames) throws IOException {
        var info = WebpSafety.validate(data);
        if (info.width() != w || info.height() != h || info.frames() != frames) throw new AssertionError(info);
        checks++;
    }
    private static void reject(byte[] data, String reason) throws IOException {
        try { WebpSafety.validate(data); throw new AssertionError("Accepted " + reason); }
        catch (IOException expected) { checks++; }
    }
    private static byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    private static byte[] riff(byte[]... chunks) {
        byte[] payload = join(chunks), result = new byte[payload.length + 12];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, result, 0, 4);
        put(result, 4, result.length - 8, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, result, 8, 4);
        System.arraycopy(payload, 0, result, 12, payload.length); return result;
    }
    private static byte[] chunk(String name, byte[] data) {
        byte[] result = new byte[8 + data.length + (data.length & 1)];
        System.arraycopy(name.getBytes(StandardCharsets.US_ASCII), 0, result, 0, 4);
        put(result, 4, data.length, 4); System.arraycopy(data, 0, result, 8, data.length); return result;
    }
    private static byte[] vp8x(int w, int h, boolean animated) {
        byte[] data = new byte[10]; data[0] = animated ? (byte) 2 : 0;
        put(data, 4, w - 1, 3); put(data, 7, h - 1, 3); return chunk("VP8X", data);
    }
    private static byte[] vp8l(int w, int h) {
        byte[] data = new byte[5]; data[0] = 0x2f;
        put(data, 1, (w - 1) | ((h - 1) << 14), 4); return chunk("VP8L", data);
    }
    private static byte[] frame(int w, int h, int x, int y, byte[] bitstream) {
        byte[] header = new byte[16];
        put(header, 0, x, 3); put(header, 3, y, 3); put(header, 6, w - 1, 3); put(header, 9, h - 1, 3);
        return chunk("ANMF", join(header, bitstream));
    }
    private static byte[] animated(int w, int h, byte[]... frames) {
        return riff(vp8x(w, h, true), chunk("ANIM", new byte[6]), join(frames));
    }
    private static byte[] join(byte[]... parts) {
        var out = new ByteArrayOutputStream(); for (byte[] part : parts) out.writeBytes(part); return out.toByteArray();
    }
    private static void put(byte[] bytes, int offset, int value, int count) {
        for (int i = 0; i < count; i++) bytes[offset + i] = (byte) (value >>> (8 * i));
    }
}
