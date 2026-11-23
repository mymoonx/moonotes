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
    private volatile boolean closed;

    EmotePreviews() {
        worker.prestartCoreThread();
    }

    void draw(GuiGraphicsExtractor graphics, String id, int x, int y, int box) {
        if (closed || id == null || id.isBlank()) return;
        Thumbnail entry = thumbnails.get(id);
        if (entry == null) {
            FutureTask<byte[]> request = enqueue(() -> prepareThumbnail(id), false);
            if (request == null) return;
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
        if (entry.texture != null) {
            // Explicit source size is essential: the short overload crops when box != THUMB.
            graphics.blit(RenderPipelines.GUI_TEXTURED, entry.texture, x, y, 0, 0,
                    box, box, THUMB, THUMB, THUMB, THUMB);
        }
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
        worker.shutdownNow();
        queue.clear();
    }

    private static final class Thumbnail {
        FutureTask<byte[]> future;
        Identifier texture;
        long retryAt;
        Thumbnail(FutureTask<byte[]> future) { this.future = future; }
    }

    private record PreparedAnimation(byte[][] images, int[] times) { }
}
