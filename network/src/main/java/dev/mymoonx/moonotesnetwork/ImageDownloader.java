package dev.mymoonx.moonotesnetwork;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/** One source policy shared by bulk prefetch and ordinary Streamotes image loads. */
final class ImageDownloader {
    static final int MAX_IMAGE_BYTES = 3 * 1024 * 1024;
    record Response(int status, String contentType, byte[] data, long retryAfterSeconds) {}
    record Image(byte[] data, String source, long millis) {}
    /** Exact per-source outcome, with no URL, token, IP address or arbitrary server body. */
    static final class SourcesUnavailable extends IOException {
        private final String direct, worker;
        SourcesUnavailable(String direct, String worker, IOException cause) {
            super("Both configured image sources unavailable", cause);
            this.direct = direct; this.worker = worker;
        }
        String direct() { return direct; }
        String worker() { return worker; }
    }
    static String safeOutcome(IOException failure) {
        if (failure instanceof HttpFailure http) return "HTTP " + http.status + http.detail;
        return switch (DiagnosticsReport.errorKind(failure)) {
            case "timeout" -> "тайм-аут";
            case "dns" -> "DNS";
            case "tls" -> "TLS";
            case "connection" -> "нет соединения";
            case "invalid_image" -> "неверное изображение";
            case "size_limit" -> "файл слишком большой";
            default -> "ошибка обработки";
        };
    }

    interface Transport {
        Response get(URI uri, BooleanSupplier cancelled, LongConsumer received) throws IOException, InterruptedException;
    }
    interface Observer {
        void success(String source, long millis);
        void fallback();
        void failure(String category);
    }
    private final String workerBase;
    private final Transport transport;
    private final Observer observer;
    private final SourceCircuit circuit;
    // The ordinary Streamotes path and bulk prefetch share the same upper bound.
    private final Semaphore requests = new Semaphore(6, true);

    ImageDownloader(String workerBase, Observer observer) {
        this(workerBase, (uri, cancelled, received) -> {
            var response = BoundedHttp.get(uri, "image/webp", MAX_IMAGE_BYTES, Duration.ofSeconds(15), cancelled, received);
            long retryAfter = 0;
            try { retryAfter = Long.parseLong(response.headers().firstValue("retry-after").orElse("0")); }
            catch (NumberFormatException ignored) { }
            return new Response(response.statusCode(), response.headers().firstValue("content-type").orElse(""), response.body(), retryAfter);
        }, observer, new SourceCircuit());
    }
    ImageDownloader(String workerBase, Transport transport, Observer observer, SourceCircuit circuit) {
        this.workerBase = workerBase;
        this.transport = transport;
        this.observer = observer;
        this.circuit = circuit;
    }
    void resetCircuit() { circuit.reset(); }
    long fallbackRemainingSeconds() { return circuit.remainingSeconds(); }

    Image fetch(URI original, String sourceMode, boolean retry, BooleanSupplier cancelled, LongConsumer received)
            throws IOException, InterruptedException {
        URI worker = ProxyUrlResolver.resolve(original, workerBase);
        URI direct = ProxyUrlResolver.canonicalCdn(original);
        if (worker == null || direct == null) throw new IOException("Invalid 7TV image URL");
        boolean auto = !"direct".equals(sourceMode) && !"worker".equals(sourceMode);
        boolean tryDirect = "direct".equals(sourceMode) || (auto && circuit.shouldTryDirect());
        URI[] sources = tryDirect ? (auto ? new URI[] {direct, worker} : new URI[] {direct}) : new URI[] {worker};
        if (auto && !tryDirect) observer.fallback();
        IOException lastFailure = null;
        String directOutcome = "не проверялся";
        String workerOutcome = "не проверялся";
        if (auto && !tryDirect) directOutcome = "пауза после ошибки сети";
        else if ("worker".equals(sourceMode)) directOutcome = "выключен в настройках";
        if ("direct".equals(sourceMode)) workerOutcome = "выключен в настройках";
        for (int sourceIndex = 0; sourceIndex < sources.length; sourceIndex++) {
            URI uri = sources[sourceIndex];
            boolean isDirect = uri.equals(direct);
            for (int attempt = 0; attempt < (retry ? 2 : 1); attempt++) {
                checkCancelled(cancelled);
                long started = System.nanoTime();
                try {
                    Response response;
                    while (!requests.tryAcquire(150, TimeUnit.MILLISECONDS)) checkCancelled(cancelled);
                    try {
                        checkCancelled(cancelled);
                        response = transport.get(uri, cancelled, received);
                    } finally { requests.release(); }
                    if (response.status() != 200) throw new HttpFailure(response.status(), response.retryAfterSeconds(),
                        isDirect ? "" : safeWorkerDetail(response));
                    validateImage(response.contentType(), response.data());
                    long millis = Math.max(1, (System.nanoTime() - started) / 1_000_000L);
                    String source = isDirect ? "7TV" : "Cloudflare";
                    if (isDirect) { directOutcome = "OK"; circuit.reset(); }
                    else workerOutcome = "OK";
                    observer.success(source, millis);
                    return new Image(response.data(), source, millis);
                } catch (IOException failure) {
                    lastFailure = failure;
                    if (isDirect) directOutcome = safeOutcome(failure);
                    else workerOutcome = safeOutcome(failure);
                    observer.failure(DiagnosticsReport.errorKind(failure));
                    boolean transportFailure = isTransportFailure(failure);
                    if (isDirect && transportFailure) circuit.failedTransport();
                    // In auto mode, an unavailable direct origin should fall back immediately.
                    if (!retry || attempt > 0 || (auto && isDirect && transportFailure) || !retryable(failure)) break;
                    // Do not retry earlier than a long server-requested backoff.
                    if (failure instanceof HttpFailure http && http.retryAfterSeconds > 2) break;
                    long delay = failure instanceof HttpFailure http && http.retryAfterSeconds > 0
                        ? Math.min(2_000L, http.retryAfterSeconds * 1_000L) : 350L;
                    waitBeforeRetry(delay, cancelled);
                }
            }
            if (sourceIndex + 1 < sources.length) observer.fallback();
        }
        throw new SourcesUnavailable(directOutcome, workerOutcome, lastFailure);
    }

    static void validateImage(String type, byte[] data) throws IOException {
        String mime = type == null ? "" : type.toLowerCase(Locale.ROOT).split(";", 2)[0].strip();
        if (!mime.isEmpty() && !mime.equals("image/webp") && !mime.equals("application/octet-stream"))
            throw new IOException("Unexpected Content-Type");
        if (data == null || data.length > MAX_IMAGE_BYTES) throw new IOException("Image size limit exceeded");
        if (data.length < 12 || data[0] != 'R' || data[1] != 'I' || data[2] != 'F' || data[3] != 'F'
                || data[8] != 'W' || data[9] != 'E' || data[10] != 'B' || data[11] != 'P')
            throw new IOException("Invalid WebP signature");
        WebpSafety.validate(data);
    }

    /** Only bounded, known machine codes become UI text; never echo a response body. */
    private static String safeWorkerDetail(Response response) {
        if (response.data() == null || response.data().length > 8192 || response.contentType() == null
                || !response.contentType().toLowerCase(Locale.ROOT).startsWith("application/json")) return "";
        try {
            String body = new String(response.data(), StandardCharsets.UTF_8);
            // The Worker error contract is flat; reject deep/unrelated JSON before parsing.
            if (body.chars().filter(c -> c == '{' || c == '[').limit(17).count() > 16) return "";
            var json = JsonParser.parseString(body).getAsJsonObject();
            String code = json.has("error") ? json.get("error").getAsString() : "";
            String stage = json.has("stage") ? json.get("stage").getAsString() : "";
            int status = json.has("upstreamStatus") ? json.get("upstreamStatus").getAsInt() : 0;
            String detail = switch (code) {
                case "upstream_redirect" -> "7TV: перенаправление";
                case "upstream_unavailable" -> "7TV недоступен";
                case "upstream_unreachable" -> "нет соединения Worker с 7TV";
                case "upstream_timeout" -> "7TV: тайм-аут";
                case "upstream_rate_limited" -> "7TV: лимит запросов";
                case "not_found_on_7tv" -> "нет изображения в 7TV";
                case "image_invalid_type", "image_invalid_webp" -> "7TV: неверное изображение";
                case "upstream_body_too_large" -> "7TV: файл слишком большой";
                case "image_busy", "image_retry_later" -> "повторить позже";
                case "r2_not_bound" -> "R2 не подключён к Worker";
                case "gateway_unavailable" -> stage.equals("cache") ? "R2 недоступен" : "Worker недоступен";
                default -> "";
            };
            if (detail.isEmpty()) return "";
            if (status >= 100 && status <= 599 && (code.equals("upstream_redirect")
                    || code.equals("upstream_unavailable") || code.equals("upstream_rate_limited")))
                detail += ": HTTP " + status;
            return " (" + detail + ")";
        } catch (RuntimeException ignored) { return ""; }
    }
    private static boolean isTransportFailure(IOException failure) {
        String kind = DiagnosticsReport.errorKind(failure);
        return kind.equals("timeout") || kind.equals("dns") || kind.equals("tls") || kind.equals("connection");
    }
    private static boolean retryable(IOException failure) {
        if (failure instanceof HttpFailure http) return http.status == 429 || (http.status >= 500 && http.status < 600);
        return isTransportFailure(failure);
    }
    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedException("Download cancelled");
    }
    private static void waitBeforeRetry(long delay, BooleanSupplier cancelled) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, delay));
        while (System.nanoTime() < deadline) {
            checkCancelled(cancelled);
            Thread.sleep(Math.min(100L, Math.max(1L, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))));
        }
    }
    private static final class HttpFailure extends IOException {
        final int status;
        final long retryAfterSeconds;
        final String detail;
        HttpFailure(int status, long retryAfterSeconds, String detail) {
            super("HTTP " + status);
            this.status = status;
            this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
            this.detail = detail;
        }
    }
}
