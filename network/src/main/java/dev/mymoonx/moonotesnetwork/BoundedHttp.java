package dev.mymoonx.moonotesnetwork;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/** Bounded responses: the deadline includes the body, not just response headers. */
final class BoundedHttp {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NEVER).build();

    private BoundedHttp() {}

    static HttpResponse<byte[]> get(URI uri, String accept, int limit, Duration timeout,
            BooleanSupplier cancelled, LongConsumer received) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
            .header("Accept", accept).GET().build();
        return send(request, limit, timeout, cancelled, received);
    }

    static HttpResponse<byte[]> postJson(URI uri, String body, int limit, Duration timeout,
            BooleanSupplier cancelled, LongConsumer received) throws IOException, InterruptedException {
        if (body == null || body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 8192)
            throw new IOException("Request size limit exceeded");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
            .header("Accept", "application/json").header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8)).build();
        return send(request, limit, timeout, cancelled, received);
    }

    private static HttpResponse<byte[]> send(HttpRequest request, int limit, Duration timeout,
            BooleanSupplier cancelled, LongConsumer received) throws IOException, InterruptedException {
        CompletableFuture<HttpResponse<byte[]>> pending = HTTP.sendAsync(request,
            info -> new LimitedBody(limit, received));
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (true) {
                if (cancelled.getAsBoolean()) throw new InterruptedException("Download cancelled");
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new HttpTimeoutException("Response body timeout");
                try {
                    return pending.get(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(150)), TimeUnit.NANOSECONDS);
                } catch (TimeoutException waitAgain) {
                    // A short poll also makes cancellation work while a server stalls mid-body.
                } catch (ExecutionException failure) {
                    Throwable cause = failure.getCause();
                    while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null)
                        cause = cause.getCause();
                    if (cause instanceof IOException io) throw io;
                    if (cause instanceof InterruptedException interrupted) throw interrupted;
                    throw new IOException("HTTP request failed", cause);
                }
            }
        } finally {
            if (!pending.isDone()) pending.cancel(true);
        }
    }

    /** Reject oversized bodies before allocating them, including chunked responses. */
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final LongConsumer received;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        LimitedBody(int limit, LongConsumer received) {
            if (limit < 1) throw new IllegalArgumentException("Positive body limit required");
            this.limit = limit;
            this.received = received;
        }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) { subscription.cancel(); return; }
            this.subscription = subscription;
            subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) return;
            for (ByteBuffer buffer : buffers) {
                int length = buffer.remaining();
                received.accept(length);
                if (length > limit - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("Response size limit exceeded"));
                    return;
                }
                byte[] part = new byte[length];
                buffer.get(part);
                bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable failure) { result.completeExceptionally(failure); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
