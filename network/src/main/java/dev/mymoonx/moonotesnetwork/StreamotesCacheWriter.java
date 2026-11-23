package dev.mymoonx.moonotesnetwork;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Decodes WebP using Streamotes' own decoder and writes its native cache format:
 * emoticons/cache/images/7tv-<id>.png [+ .png.txt for animation].
 * Never stores a second WebP copy.
 */
public final class StreamotesCacheWriter {
    private StreamotesCacheWriter() { }

    public static boolean save(String id, byte[] webp) throws IOException {
        Path png = CacheFiles.forId(id);
        if (CacheFiles.looksValid(png)) return false;
        WebpSafety.validate(webp); // Bound dimensions/frames before allocating decoder data.

        StreamotesDecoder decoder = new StreamotesDecoder(id);
        Path temporaryPng = null;
        Path temporaryMeta = null;
        try (decoder) {
          try {
            if (!decoder.load(webp)) {
                throw new IOException("Streamotes could not decode WebP for " + id);
            }
            Files.createDirectories(png.getParent());
            temporaryPng = Files.createTempFile(png.getParent(), "moonotes-", ".png");
            decoder.writeImage(temporaryPng.toFile());
            if (Files.size(temporaryPng) > 64L * 1024 * 1024)
                throw new IOException("Decoded sprite PNG is too large");
            if (!StreamotesCacheFormat.hasPngSignature(temporaryPng)) {
                throw new IOException("PNG encoding failed for " + id);
            }

            Path meta = CacheFiles.animationMetadata(png);
            int[] frameTimes = decoder.getFrameTimes();
            boolean declaredAnimated = CacheFiles.expectsAnimation(id);
            // Some 7TV animated sources decode to one frame in the installed
            // Streamotes decoder. Previously we saved the PNG, but not the
            // required .png.txt, so the same item stayed "missing" forever.
            if (declaredAnimated && (frameTimes == null || frameTimes.length == 0))
                frameTimes = new int[] { 1000 };
            if (frameTimes != null && frameTimes.length >= (declaredAnimated ? 1 : 2)) {
                temporaryMeta = Files.createTempFile(png.getParent(), "moonotes-", ".txt");
                Files.writeString(temporaryMeta,
                    StreamotesCacheFormat.animationMetadata(decoder.getWidth(), decoder.getHeight(), frameTimes),
                    StandardCharsets.UTF_8);
            }

            // Streamotes or another prefetch action may have populated it while we decoded.
            if (CacheFiles.looksValid(png)) return false;
            if (temporaryMeta != null) {
                move(temporaryMeta, meta);
                temporaryMeta = null;
            } else {
                Files.deleteIfExists(meta); // static image: prevent stale animation metadata
            }
            move(temporaryPng, png); // publish PNG last, after its animation metadata
            temporaryPng = null;
            // Never count a freshly written PNG as downloaded unless Streamotes'
            // shared cache will actually recognise it on the next scan.
            if (!CacheFiles.looksValid(png))
                throw new IOException("Written cache image or animation metadata failed validation");
            return true;
          } finally {
            try { if (temporaryPng != null) Files.deleteIfExists(temporaryPng); }
            finally { if (temporaryMeta != null) Files.deleteIfExists(temporaryMeta); }
          }
          // The resource closes even if deleting either temporary file fails.
        }
    }

    /**
     * Streamotes is a runtime dependency, not a bundled library and not a
     * compile-time jar. Look up its public decoder only when Minecraft runs.
     */
    private static final class StreamotesDecoder implements AutoCloseable {
        private final Object emote;
        private final Method loadImage, writeImage, getFrameTimes, getWidth, getHeight, close;

        StreamotesDecoder(String id) throws IOException {
            try {
                Class<?> emoteClass = Class.forName("xeed.mc.streamotes.emoticon.Emoticon");
                Class<?> loaderClass = Class.forName("xeed.mc.streamotes.api.IEmoticonLoader");
                Class<?> internals = Class.forName("xeed.mc.streamotes.InternalMethods");
                Object loader = Proxy.newProxyInstance(loaderClass.getClassLoader(), new Class<?>[]{loaderClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("toString")) return "Moonotes prefetch loader";
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("equals")) return proxy == args[0];
                        return null;
                    });
                Constructor<?> ctor = emoteClass.getConstructor(String.class, String.class, boolean.class, int.class, loaderClass);
                loadImage = internals.getMethod("loadImage", emoteClass, InputStream.class);
                writeImage = emoteClass.getMethod("writeImage", java.io.File.class);
                getFrameTimes = emoteClass.getMethod("getFrameTimes");
                getWidth = emoteClass.getMethod("getWidth");
                getHeight = emoteClass.getMethod("getHeight");
                close = emoteClass.getMethod("close");
                emote = ctor.newInstance("moonotes", id, false, 1, loader);
            } catch (ReflectiveOperationException e) {
                throw new IOException("Installed Streamotes decoder is unavailable or incompatible", e);
            }
        }

        private Object call(Method method, Object... args) throws IOException {
            try { return method.invoke(emote, args); }
            catch (IllegalAccessException | InvocationTargetException e) {
                throw new IOException("Streamotes decoder failed: " + method.getName(), e);
            }
        }

        boolean load(byte[] webp) throws IOException {
            try (var input = new ByteArrayInputStream(webp)) {
                // InternalMethods.loadImage is STATIC and expects (Emoticon, InputStream).
                // Passing only the stream caused: wrong number of arguments: 1 expected: 2.
                try {
                    return Boolean.TRUE.equals(loadImage.invoke(null, emote, input));
                } catch (IllegalAccessException e) {
                    throw new IOException("Cannot access Streamotes image decoder", e);
                } catch (InvocationTargetException e) {
                    throw new IOException("Streamotes image decoder failed", e.getCause());
                }
            }
        }

        void writeImage(java.io.File file) throws IOException { call(writeImage, file); }
        int[] getFrameTimes() throws IOException { return (int[]) call(getFrameTimes); }
        int getWidth() throws IOException { return (Integer) call(getWidth); }
        int getHeight() throws IOException { return (Integer) call(getHeight); }

        @Override public void close() throws IOException { call(close); }
    }

    private static void move(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }
}
