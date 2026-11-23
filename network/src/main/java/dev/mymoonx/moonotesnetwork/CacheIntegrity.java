package dev.mymoonx.moonotesnetwork;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;

/** Bounded validation of the shared PNG atlas and its animation metadata, off the render thread. */
public final class CacheIntegrity {
    static final int MAX_PNG = 64 * 1024 * 1024;
    static final long MAX_PIXELS = 16L * 1024 * 1024;
    static final int MAX_METADATA = 64 * 1024;
    public record Loaded(BufferedImage image, int frameWidth, int frameHeight, int[] times) { }
    private CacheIntegrity() { }

    public static Loaded read(Path png) throws IOException {
        if (!Files.isRegularFile(png, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing cache image");
        byte[] data = boundedRead(png, MAX_PNG);
        int[] dimensions = validatePng(data);
        Path sidecar = png.resolveSibling(png.getFileName() + ".txt");
        List<String> lines = null;
        if (Files.exists(sidecar, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(sidecar, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe metadata file");
            lines = new String(boundedRead(sidecar, MAX_METADATA), StandardCharsets.UTF_8).lines().toList();
        }
        PreviewFrames.Metadata metadata = PreviewFrames.metadata(dimensions[0], dimensions[1], lines);
        if (metadata.width() > 2048 || metadata.height() > 2048)
            throw new IOException("Cache frame dimensions exceed limit");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) throw new IOException("PNG decode failed");
        if (image.getWidth() != dimensions[0] || image.getHeight() != dimensions[1]) {
            image.flush();
            throw new IOException("PNG dimensions mismatch");
        }
        return new Loaded(image, metadata.width(), metadata.height(), metadata.times());
    }

    static byte[] boundedRead(Path path, int limit) throws IOException {
        if (Files.size(path) > limit) throw new IOException("Cache file exceeds limit");
        try (var in = Files.newInputStream(path)) {
            byte[] bytes = in.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("Cache file exceeds limit");
            return bytes;
        }
    }

    /** Checks lengths and every chunk CRC before ImageIO can allocate a decoded bitmap. */
    static int[] validatePng(byte[] data) throws IOException {
        byte[] signature = {(byte)137,80,78,71,13,10,26,10};
        if (data.length < 45 || data.length > MAX_PNG) throw new IOException("Invalid PNG length");
        for (int i=0;i<8;i++) if (data[i] != signature[i]) throw new IOException("Invalid PNG signature");
        ByteBuffer bytes = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int offset=8, width=0, height=0, chunks=0;
        boolean pixels=false, end=false;
        while (offset < data.length) {
            if (++chunks > 100_000 || data.length-offset < 12) throw new IOException("Invalid PNG chunks");
            int length=bytes.getInt(offset), type=bytes.getInt(offset+4);
            if (length < 0 || (long)offset+12+length > data.length) throw new IOException("Truncated PNG chunk");
            CRC32 crc=new CRC32(); crc.update(data,offset+4,length+4);
            if ((int)crc.getValue()!=bytes.getInt(offset+8+length)) throw new IOException("PNG checksum mismatch");
            if (chunks==1) {
                if (type!=0x49484452 || length!=13) throw new IOException("Missing PNG header");
                width=bytes.getInt(offset+8); height=bytes.getInt(offset+12);
                if(width<1 || height<1 || width>32768 || height>32768 || (long)width*height>MAX_PIXELS)
                    throw new IOException("PNG dimensions exceed limit");
            } else if(type==0x49484452) throw new IOException("Duplicate PNG header");
            if(type==0x49444154) pixels=true;
            offset+=12+length;
            if(type==0x49454e44) {
                if(length!=0 || !pixels || offset!=data.length) throw new IOException("Invalid PNG end");
                end=true;break;
            }
        }
        if(!end)throw new IOException("Missing PNG end");
        return new int[]{width,height};
    }
}
