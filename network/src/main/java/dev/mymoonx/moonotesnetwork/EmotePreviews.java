package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Reads only Streamotes' shared PNG cache. Disk IO and sprite preparation run
 * on one bounded worker; the client thread only uploads small prepared frames. */
final class EmotePreviews implements AutoCloseable {
    private static final int LIMIT = 64;
    private static final int THUMB = 27;
    private static final int DETAIL = 128;
    private static final int DETAIL_GPU_LIMIT = 12;
    // World surfaces use thumb-sized frames to avoid growing GPU memory with sign count.
    private static final int WORLD_CACHE_LIMIT = 8;
    private static final long WORLD_IDLE_RECYCLE_MS = 2_000;
    private static final long WORLD_STATIC_RETRY_MS = 10_000;
    private static final int WORLD_GPU_LIMIT_PER_EMOTE = 20;
    private static final int WORLD_MAX_FRAMES = 256;
    private static final long WORLD_MAX_PREPARED = 4L * 1024 * 1024;
    private static final long MAX_SOURCE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000L;
    private static final long MAX_PREPARED_BYTES = 20L * 1024 * 1024;
    private static final AtomicInteger NEXT_INSTANCE = new AtomicInteger();
    private final int instance = NEXT_INSTANCE.incrementAndGet();
    private final LinkedBlockingDeque<Runnable> queue = new LinkedBlockingDeque<>(32);
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            queue, runnable -> {
                Thread thread = new Thread(runnable, "Moonotes-Preview-" + instance);
                thread.setDaemon(true);
                return thread;
            });
    // All entries and GPU textures are owned exclusively by the client thread.
    private final LinkedHashMap<String, Thumbnail> thumbnails = new LinkedHashMap<>(32, .75f, true);
    private String modalId;
    private FutureTask<PreparedAnimation> modalFuture;
    private PreparedAnimation modal;
    private long modalStarted;
    private final LinkedHashMap<Integer, Identifier> modalTextures = new LinkedHashMap<>(16, .75f, true);
    // Client-thread LRU; animations are prepared by the existing bounded worker.
    private final LinkedHashMap<String, WorldAnimation> worldAnimations =
        new LinkedHashMap<>(16, .75f, true);
    private volatile boolean closed;

    EmotePreviews() {
        worker.prestartCoreThread();
    }

    void draw(GuiGraphicsExtractor graphics, String id, int x, int y, int box) {
        Identifier texture = texture(id);
        if (texture != null) {
            // Explicit source size is essential: the short overload crops when box != THUMB.
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0,
                    box, box, THUMB, THUMB, THUMB, THUMB);
        }
    }


    /** Render an animation on signs/books without changing the regular static GUI preview.
     * All frames use the same 27x27 dimensions and an LRU of bounded GPU textures.
     * Falls back to frame zero while its atlas and timings are decoded asynchronously.
     */
    Identifier animatedTexture(String id) {
        Identifier fallback = texture(id);
        if (closed || fallback == null || id == null || id.isBlank()) return fallback;
        long now = System.currentTimeMillis();
        WorldAnimation animation = worldAnimations.get(id);
        if (animation == null) {
            // Do not evict textures referenced by this render frame. When more
            // than eight distinct animations are visible, extra ones display
            // a safe static first frame until a slot has been idle 2 seconds.
            if (worldAnimations.size() >= WORLD_CACHE_LIMIT) {
                Iterator<WorldAnimation> candidates = worldAnimations.values().iterator();
                boolean freed = false;
                while (candidates.hasNext()) {
                    WorldAnimation old = candidates.next();
                    if (now - old.lastUsedMillis > WORLD_IDLE_RECYCLE_MS) {
                        candidates.remove();
                        cancel(old.future);
                        for (Identifier texture : old.textures.values()) release(texture);
                        old.textures.clear();
                        freed = true;
                        break;
                    }
                }
                if (!freed) return fallback;
            }
            FutureTask<PreparedAnimation> requested =
                enqueue(() -> prepareWorldAnimation(id), false);
            if (requested == null) return fallback;
            animation = new WorldAnimation(requested);
            worldAnimations.put(id, animation);
        } else if (animation.staticOnly && animation.future == null
                && now >= animation.retryAtMillis) {
            // Metadata can arrive slightly after the PNG has been cached.
            FutureTask<PreparedAnimation> requested =
                enqueue(() -> prepareWorldAnimation(id), false);
            if (requested != null) {
                animation.future = requested;
                animation.staticOnly = false;
            }
        }
        animation.lastUsedMillis = now;
        if (animation.future != null && animation.future.isDone()) {
            try {
                animation.prepared = animation.future.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | CancellationException ex) {
                // Keep frame zero for malformed or oversized animations.
            } finally {
                animation.future = null;
                animation.staticOnly = animation.prepared == null;
                animation.retryAtMillis = now + WORLD_STATIC_RETRY_MS;
            }
        }
        if (animation.prepared == null) return fallback;
        PreparedAnimation prepared = animation.prepared;
        int frame = PreviewFrames.frameAt(prepared.times(), System.currentTimeMillis());
        Identifier current = animation.textures.get(frame);
        if (current != null) return current;
        try {
            current = upload("world/" + id + "/" + frame, prepared.images()[frame]);
            animation.textures.put(frame, current);
            while (animation.textures.size() > WORLD_GPU_LIMIT_PER_EMOTE) {
                Iterator<Identifier> oldest = animation.textures.values().iterator();
                Identifier unused = oldest.next();
                oldest.remove();
                release(unused);
            }
            return current;
        } catch (IOException | RuntimeException error) {
            // An individual corrupt frame must never break the entire world render.
            return fallback;
        }
    }

    void drawAnimated(GuiGraphicsExtractor graphics, String id, int x, int y, int box) {
        Identifier image = animatedTexture(id);
        if (image != null) graphics.blit(RenderPipelines.GUI_TEXTURED, image, x, y, 0, 0,
            box, box, THUMB, THUMB, THUMB, THUMB);
    }

    /**
     * Returns a complete square texture, never an atlas fragment. World/text renderers
     * use the same bounded asynchronous cache as GUI previews and sample UV 0..1.
     * Call only from the client extraction thread; null keeps the original text visible.
     */
    Identifier texture(String id) {
        if (closed || id == null || id.isBlank()) return null;
        Thumbnail entry = thumbnails.get(id);
        if (entry == null) {
            FutureTask<byte[]> request = enqueue(() -> prepareThumbnail(id), false);
            if (request == null) return null;
            entry = new Thumbnail(request);
            thumbnails.put(id, entry);
            trimThumbnails();
        }
        if (entry.texture == null && entry.future == null && System.nanoTime() >= entry.retryAt) {
            entry.future = enqueue(() -> prepareThumbnail(id), false);
        }
        if (entry.texture == null && entry.future != null && entry.future.isDone()) {
            try {
                byte[] image = entry.future.get();
                if (image != null) entry.texture = upload("thumb/" + id, image);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | IOException | RuntimeException ex) {
                // A corrupt image or a cancelled off-screen request remains a placeholder.
            } finally {
                entry.future = null;
                entry.retryAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            }
        }
        return entry.texture;
    }

    /** True only after an asynchronous cache attempt finished without an image. */
    boolean missing(String id) {
        if (closed) return false;
        Thumbnail entry = thumbnails.get(id);
        return entry != null && entry.texture == null && entry.future == null;
    }

    int drawDetailed(GuiGraphicsExtractor graphics, String id, int x, int y, int width) {
        if (closed || id == null || id.isBlank()) return 0;
        if (!id.equals(modalId)) {
            closeModal();
            modalId = id;
            modalFuture = enqueue(() -> prepareAnimation(id), true);
        }
        if (modal == null && modalFuture != null && modalFuture.isDone()) {
            try {
                modal = modalFuture.get();
                modalStarted = System.nanoTime();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | CancellationException ex) {
                // Leave a stable placeholder instead of retrying a broken file every frame.
            } finally {
                modalFuture = null;
            }
        }
        if (modal == null) return 0;
        int frame = PreviewFrames.frameAt(modal.times,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - modalStarted));
        Identifier texture = modalTextures.get(frame);
        if (texture == null) {
            try {
                texture = upload("detail/" + id + "/" + frame, modal.images[frame]);
                modalTextures.put(frame, texture);
                if (modalTextures.size() > DETAIL_GPU_LIMIT) {
                    Iterator<Identifier> iterator = modalTextures.values().iterator();
                    Identifier evicted = iterator.next();
                    iterator.remove();
                    release(evicted);
                }
            } catch (IOException | RuntimeException ex) {
                for (Identifier loaded : modalTextures.values()) release(loaded);
                modalTextures.clear();
                modal = null;
                return 0;
            }
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0,
                width, width, DETAIL, DETAIL, DETAIL, DETAIL);
        return modal.times.length;
    }

    boolean detailLoading() {
        return modalFuture != null && !modalFuture.isDone();
    }

    boolean detailUnavailable() {
        return modalId != null && modalFuture == null && modal == null;
    }

    private <T> FutureTask<T> enqueue(Callable<T> preparation, boolean priority) {
        if (closed) return null;
        FutureTask<T> task = new FutureTask<>(preparation);
        if (!priority) return queue.offerLast(task) ? task : null;
        if (queue.offerFirst(task)) return task;
        // A user-opened preview takes priority over speculative off-screen thumbnails.
        Runnable oldest = queue.pollLast();
        if (oldest instanceof FutureTask<?> future) future.cancel(false);
        return queue.offerFirst(task) ? task : null;
    }

    private byte[] prepareThumbnail(String id) throws IOException {
        BufferedImage source = safeRead(CacheFiles.forId(id));
        if (source == null) return null;
        try {
            PreviewFrames.Metadata metadata = metadata(CacheFiles.forId(id), source);
            return encodeFrame(source, 0, 0, metadata.width(), metadata.height(), THUMB);
        } finally {
            source.flush();
        }
    }

    private PreparedAnimation prepareAnimation(String id) throws IOException {
        Path file = CacheFiles.forId(id);
        BufferedImage source = safeRead(file);
        if (source == null) return null;
        try {
            PreviewFrames.Metadata metadata = metadata(file, source);
            byte[][] frames = new byte[metadata.times().length][];
            long preparedBytes = 0;
            for (int frame = 0; frame < frames.length; frame++) {
                if (closed || Thread.currentThread().isInterrupted()) return null;
                int sx = (frame % metadata.columns()) * metadata.width();
                int sy = (frame / metadata.columns()) * metadata.height();
                frames[frame] = encodeFrame(source, sx, sy, metadata.width(), metadata.height(), DETAIL);
                preparedBytes += frames[frame].length;
                if (preparedBytes > MAX_PREPARED_BYTES) throw new IOException("Preview memory limit");
            }
            return new PreparedAnimation(frames, metadata.times());
        } finally {
            // The large decoded sprite never remains attached to the open dialog.
            source.flush();
        }
    }


    /**
     * Keeps high-frame-count GIF/WebP conversions from causing excessive allocations
     * when several signs display different animations at once.
     */
    private PreparedAnimation prepareWorldAnimation(String id) throws IOException {
        Path file = CacheFiles.forId(id);
        BufferedImage source = safeRead(file);
        if (source == null) return null;
        try {
            PreviewFrames.Metadata meta = metadata(file, source);
            int count = meta.times().length;
            if (count < 2 || count > WORLD_MAX_FRAMES) return null;
            byte[][] images = new byte[count][];
            long total = 0;
            for (int i = 0; i < count; i++) {
                if (closed || Thread.currentThread().isInterrupted()) return null;
                int sx = (i % meta.columns()) * meta.width();
                int sy = (i / meta.columns()) * meta.height();
                images[i] = encodeFrame(source, sx, sy, meta.width(), meta.height(), THUMB);
                total += images[i].length;
                if (total > WORLD_MAX_PREPARED) return null;
            }
            return new PreparedAnimation(images, meta.times());
        } finally { source.flush(); }
    }

    private static PreviewFrames.Metadata metadata(Path png, BufferedImage source) throws IOException {
        Path timing = CacheFiles.animationMetadata(png);
        List<String> lines = null;
        if (Files.exists(timing)) {
            if (!Files.isRegularFile(timing) || Files.size(timing) > 32 * 1024L)
                throw new IOException("Invalid animation metadata size");
            lines = Files.readAllLines(timing, StandardCharsets.UTF_8);
        }
        return PreviewFrames.metadata(source.getWidth(), source.getHeight(), lines);
    }

    /** Read dimensions before expanding a potentially malformed cache image. */
    private static BufferedImage safeRead(Path file) throws IOException {
        if (!CacheFiles.looksValid(file) || Files.size(file) > MAX_SOURCE_BYTES) return null;
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS
                        || width > 16384 || height > 16384) return null;
                if (Thread.currentThread().isInterrupted()) return null;
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static byte[] encodeFrame(BufferedImage source, int sx, int sy, int sw, int sh, int size)
            throws IOException {
        BufferedImage frame = PreviewFrames.fit(source, sx, sy, sw, sh, size);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(frame, "png", output)) throw new IOException("PNG encoder unavailable");
            return output.toByteArray();
        } finally {
            frame.flush();
        }
    }

    /** Must only be called by draw/drawDetailed on the client thread. */
    private Identifier upload(String key, byte[] image) throws IOException {
        NativeImage pixels = NativeImage.read(image);
        DynamicTexture texture = null;
        try {
            Identifier id = Identifier.fromNamespaceAndPath("moonotes_network",
                    "preview/" + instance + "/" + key.toLowerCase(Locale.ROOT));
            texture = new DynamicTexture(() -> "Moonotes preview", pixels);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            return id;
        } catch (RuntimeException | Error ex) {
            if (texture != null) texture.close();
            else pixels.close();
            throw ex;
        }
    }

    private void trimThumbnails() {
        while (thumbnails.size() > LIMIT) {
            Iterator<Thumbnail> iterator = thumbnails.values().iterator();
            Thumbnail evicted = iterator.next();
            iterator.remove();
            cancel(evicted.future);
            release(evicted.texture);
        }
    }


    private void trimWorldAnimations() {
        while (worldAnimations.size() > WORLD_CACHE_LIMIT) {
            Iterator<WorldAnimation> entries = worldAnimations.values().iterator();
            WorldAnimation evicted = entries.next();
            entries.remove();
            cancel(evicted.future);
            for (Identifier texture : evicted.textures.values()) release(texture);
            evicted.textures.clear();
        }
    }

    private void cancel(FutureTask<?> future) {
        if (future != null) {
            future.cancel(true);
            queue.remove(future);
        }
    }

    private static void release(Identifier id) {
        if (id != null) Minecraft.getInstance().getTextureManager().release(id);
    }

    void closeModal() {
        cancel(modalFuture);
        modalFuture = null;
        modalId = null;
        modal = null;
        for (Identifier texture : modalTextures.values()) release(texture);
        modalTextures.clear();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        closeModal();
        for (Thumbnail thumbnail : thumbnails.values()) {
            cancel(thumbnail.future);
            release(thumbnail.texture);
        }
        thumbnails.clear();
        for (WorldAnimation animation : worldAnimations.values()) {
            cancel(animation.future);
            for (Identifier texture : animation.textures.values()) release(texture);
        }
        worldAnimations.clear();
        worker.shutdownNow();
        queue.clear();
    }

    private static final class Thumbnail {
        FutureTask<byte[]> future;
        Identifier texture;
        long retryAt;
        Thumbnail(FutureTask<byte[]> future) { this.future = future; }
    }

    private static final class WorldAnimation {
        FutureTask<PreparedAnimation> future;
        PreparedAnimation prepared;
        boolean staticOnly;
        long lastUsedMillis;
        long retryAtMillis;
        final LinkedHashMap<Integer, Identifier> textures = new LinkedHashMap<>(16, .75f, true);
        WorldAnimation(FutureTask<PreparedAnimation> future) { this.future = future; }
    }

    private record PreparedAnimation(byte[][] images, int[] times) { }
}
