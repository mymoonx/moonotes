package dev.mymoonx.moonotesnetwork;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.regex.Pattern;

/** Uses Streamotes' ORIGINAL cache, not a second Moonotes image directory. */
public final class CacheFiles {
    private static final Pattern ID = Pattern.compile("(?:[0-9A-HJKMNP-TV-Z]{26}|[a-f0-9]{16,40})", Pattern.CASE_INSENSITIVE);
    private static final Path ROOT = FabricLoader.getInstance().getGameDir().resolve("emoticons/cache/images");
    // Emote IDs identify immutable image content. A useful offline cache does not expire by age.
    private record Stamp(long size, FileTime modified, long metadataSize, FileTime metadataModified) { }
    private static final ConcurrentHashMap<Path, Stamp> INVALID = new ConcurrentHashMap<>();
    private static final java.util.Set<String> ANIMATED=ConcurrentHashMap.newKeySet();

    private CacheFiles() { }

    public static Path forId(String rawId) {
        if (rawId == null || !ID.matcher(rawId).matches()) throw new IllegalArgumentException("Invalid 7TV ID");
        return ROOT.resolve("7tv-" + rawId.toLowerCase(Locale.ROOT) + ".png");
    }

    public static Path animationMetadata(Path png) {
        return png.resolveSibling(png.getFileName().toString() + ".txt");
    }

    public static boolean looksValid(Path png) {
        try {
            if (!Files.isRegularFile(png, LinkOption.NOFOLLOW_LINKS)) return false;
            long length=Files.size(png);
            if (length < 45 || length > CacheIntegrity.MAX_PNG) return false;
            String file=png.getFileName().toString();
            if(file.startsWith("7tv-") && file.endsWith(".png")
                    && expectsAnimation(file.substring(4,file.length()-4))
                    && !Files.isRegularFile(animationMetadata(png),LinkOption.NOFOLLOW_LINKS))return false;
            Stamp bad=INVALID.get(png);
            if (bad!=null) {
                if (bad.equals(stamp(png))) return false;
                INVALID.remove(png,bad);
            }
            return StreamotesCacheFormat.hasPngSignature(png);
        } catch (IOException ex) { return false; }
    }

    /** A failed integrity check makes only this unchanged file eligible for atomic replacement. */
    public static void markInvalid(String id) {
        Path png=forId(id);
        try { INVALID.put(png,stamp(png)); } catch(IOException ignored) { }
    }

    public static void expectAnimated(String id,boolean animated){
        if(animated){forId(id);ANIMATED.add(id.toLowerCase(Locale.ROOT));}
    }
    public static boolean expectsAnimation(String id){return ANIMATED.contains(id.toLowerCase(Locale.ROOT));}
    public static void markValid(String id){INVALID.remove(forId(id));}

    private static Stamp stamp(Path png) throws IOException {
        Path metadata=animationMetadata(png);
        boolean exists=Files.isRegularFile(metadata,LinkOption.NOFOLLOW_LINKS);
        return new Stamp(Files.size(png),Files.getLastModifiedTime(png,LinkOption.NOFOLLOW_LINKS),
            exists?Files.size(metadata):-1,exists?Files.getLastModifiedTime(metadata,LinkOption.NOFOLLOW_LINKS):FileTime.fromMillis(0));
    }
}
