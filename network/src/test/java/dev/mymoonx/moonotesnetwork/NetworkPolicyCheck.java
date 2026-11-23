package dev.mymoonx.moonotesnetwork;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Exercises source policy and privacy without contacting either live service. */
public final class NetworkPolicyCheck {
    private static final String WORKER = "https://moonotes-emotes.thefreelom.workers.dev";
    private static final URI DIRECT = URI.create("https://cdn.7tv.app/emote/01f0hmz1zg0004v54c00a616cb/2x.webp");
    private static final byte[] HEADER = java.util.HexFormat.of().parseHex("524946461e000000574542505650384c110000002f018000100750b25e14b9b48188e87f0000");
    private static final ImageDownloader.Observer QUIET = new ImageDownloader.Observer() {
        public void success(String source, long millis) {} public void fallback() {} public void failure(String category) {}
    };
    private static int checks;
    private static void expect(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    private static ImageDownloader.Response ok() { return new ImageDownloader.Response(200, "image/webp", HEADER, 0); }

    public static void main(String[] args) throws Exception {
        var calls = new ArrayList<URI>();
        AtomicLong now = new AtomicLong();
        SourceCircuit circuit = new SourceCircuit(now::get);
        ImageDownloader blocked = new ImageDownloader(WORKER, (uri, cancel, bytes) -> {
            calls.add(uri);
            if (uri.getHost().equals("cdn.7tv.app")) throw new HttpTimeoutException("timeout");
            return ok();
        }, QUIET, circuit);
        expect(blocked.fetch(DIRECT, "auto", true, () -> false, count -> {}).source().equals("Cloudflare"), "timeout falls back");
        expect(calls.size() == 2, "blocked direct is not retried before fallback");
        calls.clear();
        blocked.fetch(DIRECT, "auto", true, () -> false, count -> {});
        expect(calls.size() == 1 && calls.get(0).getHost().contains("workers.dev"), "cooldown skips failed origin on next emote");
        now.set(61_000_000_000L); calls.clear();
        blocked.fetch(DIRECT, "auto", true, () -> false, count -> {});
        expect(calls.size() == 2, "direct is tried again after cooldown");

        calls.clear();
        ImageDownloader missing = new ImageDownloader(WORKER, (uri, cancel, bytes) -> {
            calls.add(uri);
            return uri.getHost().equals("cdn.7tv.app") ? new ImageDownloader.Response(404, "text/plain", new byte[0], 0) : ok();
        }, QUIET, new SourceCircuit(now::get));
        missing.fetch(DIRECT, "auto", true, () -> false, count -> {});
        missing.fetch(DIRECT, "auto", true, () -> false, count -> {});
        expect(calls.size() == 4, "404 is neither retried nor an origin-wide cooldown");
        calls.clear();
        try { missing.fetch(DIRECT, "direct", true, () -> false, count -> {}); throw new AssertionError("forced direct should fail"); }
        catch (IOException expected) { expect(calls.size() == 1 && calls.get(0).equals(ProxyUrlResolver.canonicalCdn(DIRECT)), "forced direct uses canonical CDN without worker"); }
        calls.clear();
        missing.fetch(DIRECT, "worker", true, () -> false, count -> {});
        expect(calls.size() == 1 && !calls.get(0).equals(DIRECT), "forced worker uses worker only");
        calls.clear();
        try { missing.fetch(URI.create("https://cdn.7tv.app.evil.test/emote/x/2x.webp"), "auto", true, () -> false, count -> {}); throw new AssertionError("URL rejected"); }
        catch (IOException expected) { expect(calls.isEmpty(), "invalid host never reaches transport"); }
        try { missing.fetch(DIRECT, "auto", true, () -> true, count -> {}); throw new AssertionError("cancelled"); }
        catch (InterruptedException expected) { expect(calls.isEmpty(), "cancellation does not trigger fallback"); }

        var retries = new AtomicLong();
        ImageDownloader busy = new ImageDownloader(WORKER, (uri, cancel, bytes) -> retries.incrementAndGet() == 1
            ? new ImageDownloader.Response(503, "text/plain", new byte[0], 0) : ok(), QUIET, new SourceCircuit());
        busy.fetch(DIRECT, "worker", true, () -> false, count -> {});
        expect(retries.get() == 2, "transient 503 gets one retry");
        try { ImageDownloader.validateImage("text/html", HEADER); throw new AssertionError("HTML accepted"); }
        catch (IOException expected) { expect(true, "HTML rejected"); }
        try { ImageDownloader.validateImage("image/webp", new byte[ImageDownloader.MAX_IMAGE_BYTES + 1]); throw new AssertionError("oversize accepted"); }
        catch (IOException expected) { expect(DiagnosticsReport.errorKind(expected).equals("size_limit"), "size limit category"); }
        String report = DiagnosticsReport.build("0.3.7-dev", "26.3", "25.0.1", "Windows 11", "Авто", "Встроенный каталог",
            704, 10, 2, 0, 1, 1, 1, 10, 10, "7TV", "https://private.example/name/IP", "C:\\Users\\private", 1, 2, false, true);
        expect(!report.contains("private") && !report.contains("https://") && report.contains("0.3.7-dev"), "report includes no free-form errors or paths");
        expect(DiagnosticsReport.errorKind(new IOException("wrapper", new java.net.UnknownHostException("secret.host"))).equals("dns"), "wrapped DNS gets safe category");

        AtomicLong clock = new AtomicLong();
        TransferRate rate = new TransferRate(clock::get);
        rate.add(1000); clock.set(1_000_000_000L);
        expect(rate.bytesPerSecond() == 1000.0, "rolling speed counts bytes over active window");
        clock.set(4_000_000_000L); expect(rate.bytesPerSecond() == 0, "idle speed becomes zero instead of lifetime average");
        rate.reset(); expect(rate.bytesPerSecond() == 0, "resume resets old samples");
        System.out.println("Network policy checks passed (" + checks + " checks)");
    }
}
