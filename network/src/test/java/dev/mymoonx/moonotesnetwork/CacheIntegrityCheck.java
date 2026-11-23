package dev.mymoonx.moonotesnetwork;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.util.zip.CRC32;

public final class CacheIntegrityCheck {
    public static void main(String[] args) throws Exception {
        var directory=Files.createTempDirectory("moonotes-integrity-check-");
        var png=directory.resolve("sheet.png");
        BufferedImage source=new BufferedImage(4,4,BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0,0,0xff00ff00);ImageIO.write(source,"PNG",png.toFile());source.flush();
        byte[] valid=Files.readAllBytes(png);
        var image=CacheIntegrity.read(png);require(image.frameWidth()==4 && image.times().length==1,"static dimensions");image.image().flush();
        var meta=png.resolveSibling("sheet.png.txt");
        Files.writeString(meta,"2\n2\n100\n120\n140\n0\n");
        image=CacheIntegrity.read(png);require(image.frameWidth()==2 && image.frameHeight()==2 && image.times().length==3,"atlas metadata");image.image().flush();
        Files.writeString(meta,"2\n2\n100\n120\n");
        image=CacheIntegrity.read(png);require(image.times().length==2,"legacy no-sentinel metadata");image.image().flush();
        Files.writeString(meta,"3\n2\n100\n120\n0\n");reject(()->CacheIntegrity.read(png),"bad frame bounds");
        Files.writeString(meta,"2\n2\n100\n120\n130\n140\n150\n0\n");reject(()->CacheIntegrity.read(png),"too many frames for atlas");
        Files.writeString(meta,"x".repeat(CacheIntegrity.MAX_METADATA+1));reject(()->CacheIntegrity.read(png),"large metadata");
        Files.delete(meta);
        byte[] bad=valid.clone();bad[bad.length-5]^=1;Files.write(png,bad);reject(()->CacheIntegrity.read(png),"bad CRC");
        Files.write(png,java.util.Arrays.copyOf(valid,valid.length-3));reject(()->CacheIntegrity.read(png),"truncation");
        bad=valid.clone();ByteBuffer.wrap(bad).putInt(16,32768).putInt(20,32768);
        CRC32 crc=new CRC32();crc.update(bad,12,17);ByteBuffer.wrap(bad).putInt(29,(int)crc.getValue());
        Files.write(png,bad);reject(()->CacheIntegrity.read(png),"decompression allocation limit");
        Files.write(png,new byte[16]);reject(()->CacheIntegrity.read(png),"fake PNG");
        Files.write(png,valid);
        var link=directory.resolve("linked.png");
        boolean symlinkTested=false;
        try {
            Files.createSymbolicLink(link,png);
            symlinkTested=true;
            reject(()->CacheIntegrity.read(link),"symlink");
        } catch (UnsupportedOperationException ex) {
            System.out.println("CacheIntegrityCheck: symlink case SKIPPED (filesystem does not support symlinks)");
        } catch (FileSystemException ex) {
            // Windows often denies symlink creation without Developer Mode or admin rights.
            // This is a limitation of the test environment, not a cache security failure.
            if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows")) throw ex;
            System.out.println("CacheIntegrityCheck: symlink case SKIPPED (Windows denied link creation; no elevation required)");
        } finally {
            Files.deleteIfExists(link);
        }
        Files.delete(png);
        Files.delete(directory);
        System.out.println("CacheIntegrityCheck: " + (symlinkTested ? "11/11" : "10/11")
                + " cases passed" + (symlinkTested ? "" : "; 1 platform-restricted case skipped")
                + " (PNG CRC, bounds, atlas metadata, limits, symlink protection)");
    }
    interface Checked { void run() throws Exception; }
    private static void reject(Checked action,String label)throws Exception{try{action.run();throw new AssertionError(label);}catch(IOException expected){}}
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
}
