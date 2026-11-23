package dev.mymoonx.moonotesnetwork;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Streamotes PNG + animation sidecar metadata compatibility. No Fabric dependencies. */
public final class StreamotesCacheFormat {
    private static final byte[] PNG_SIGNATURE = {(byte)137, 80, 78, 71, 13, 10, 26, 10};

    private StreamotesCacheFormat() { }

    public static boolean hasPngSignature(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            byte[] first = input.readNBytes(PNG_SIGNATURE.length);
            if (first.length != PNG_SIGNATURE.length) return false;
            for (int i = 0; i < first.length; i++) {
                if (first[i] != PNG_SIGNATURE[i]) return false;
            }
            return true;
        }
    }

    public static String animationMetadata(int width, int height, int[] frameTimes) {
        if (width < 1 || height < 1 || frameTimes == null || frameTimes.length < 1) {
            throw new IllegalArgumentException("Invalid animation dimensions/timings");
        }
        StringBuilder out = new StringBuilder();
        out.append(width).append('\n').append(height).append('\n');
        for (int duration : frameTimes) out.append(duration > 0 ? duration : 100).append('\n');
        // One-frame metadata is valid for emotes flagged animated by 7TV but decoded
        // to one displayable frame. Streamotes requires a trailing sentinel.
        // Streamotes 1.2.16 cache reader uses Arrays.copyOfRange(data, 2, data.length - 1).
        // Extra trailing element preserves *all* animation frames when it reads this file.
        out.append("0\n");
        return out.toString();
    }
}
