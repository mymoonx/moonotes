package dev.mymoonx.moonotesnetwork;

import java.nio.file.Files;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Standalone checks; run with java 21+; not JUnit. */
public final class StreamotesCacheFormatCheck {
    public static void main(String[] args) throws Exception {
        var file = Files.createTempFile("moonotes-test", ".png");
        try {
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", file.toFile());
            if (!StreamotesCacheFormat.hasPngSignature(file)) throw new AssertionError("png signature");
            var meta = StreamotesCacheFormat.animationMetadata(112, 64, new int[]{75, 110, 170});
            int[] values = meta.lines().filter(s -> !s.isBlank()).mapToInt(Integer::parseInt).toArray();
            if (values.length != 6) throw new AssertionError("metadata format length");
            int[] times = java.util.Arrays.copyOfRange(values, 2, values.length - 1);
            if (!java.util.Arrays.equals(times, new int[]{75, 110, 170})) throw new AssertionError("last animation frame lost");
            if (values[0] != 112 || values[1] != 64 || values[5] != 0) throw new AssertionError("dimensions/sentinel");
            // A declared animated emote may decode to a single frame. Its
            // sidecar must be readable rather than leaving the PNG "missing".
            String single=StreamotesCacheFormat.animationMetadata(32,32,new int[]{1000});
            int[] one=single.lines().mapToInt(Integer::parseInt).toArray();
            if (one.length!=4 || one[0]!=32 || one[1]!=32 || one[2]!=1000 || one[3]!=0)
                throw new AssertionError("single-frame sidecar format");
            var parsed=PreviewFrames.metadata(32,32,single.lines().toList());
            if(parsed.times().length!=1 || parsed.times()[0]!=1000)
                throw new AssertionError("single-frame sidecar must be readable");
            System.out.println("Streamotes cache compatibility: PASS");
        } finally { Files.deleteIfExists(file); }
    }
}
