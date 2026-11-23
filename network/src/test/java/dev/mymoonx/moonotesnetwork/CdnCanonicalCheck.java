package dev.mymoonx.moonotesnetwork;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import javax.net.ssl.SSLException;

/** Regression for all uncached images failing after adding a second catalogue.
 * The real CDN returns 308 for lowercase IDs; redirects must stay disabled.
 */
public final class CdnCanonicalCheck {
    private static final String BASE = "https://moonotes-emotes.thefreelom.workers.dev";
    private static final byte[] WEBP = java.util.HexFormat.of().parseHex(
        "524946461e000000574542505650384c110000002f018000100750b25e14b9b48188e87f0000");
    private static final ImageDownloader.Observer QUIET = new ImageDownloader.Observer() {
        public void success(String source, long millis) {} public void fallback() {} public void failure(String category) {}
    };
    private static int checks;
    private static void expect(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    private static ImageDownloader.Response ok() { return new ImageDownloader.Response(200, "image/webp", WEBP, 0); }

    public static void main(String[] args) throws Exception {
        String id = "01f5z4b9700008kdxa2tjqbngw";
        URI original = URI.create("https://cdn.7tv.app/emote/" + id + "/2x.webp");
        var calls = new ArrayList<URI>();
        ImageDownloader download = new ImageDownloader(BASE, (uri, cancel, count) -> {
            calls.add(uri);
            // Exactly the 7TV rule observed for TEATIMEMEGAZOOMAAAAAA: no implicit redirects in the fixture.
            return uri.getPath().equals("/emote/" + id.toUpperCase(java.util.Locale.ROOT) + "/2x.webp")
                ? ok() : new ImageDownloader.Response(308, "", new byte[0], 0);
        }, QUIET, new SourceCircuit());
        expect(download.fetch(original, "direct", false, () -> false, count -> {}).source().equals("7TV"), "direct uncached ULID succeeds");
        expect(calls.size() == 1, "one request with no redirect hop");
        calls.clear();
        ImageDownloader blocked = new ImageDownloader(BASE, (uri, cancel, count) -> {
            calls.add(uri);
            if (uri.getHost().equals("cdn.7tv.app")) throw new SSLException("private TLS endpoint");
            expect(uri.getPath().equals("/v1/7tv/" + id + "/2x.webp"), "worker retains shared lowercase R2 key");
            return ok();
        }, QUIET, new SourceCircuit());
        expect(blocked.fetch(original, "auto", true, () -> false, count -> {}).source().equals("Cloudflare"), "TLS still falls back safely");
        expect(calls.size() == 2, "TLS failure is not retried before fallback");

        String[] bodies = {
            "{\"error\":\"upstream_redirect\",\"stage\":\"image\",\"upstreamStatus\":308,\"secret\":\"private.example\"}",
            "{\"error\":\"gateway_unavailable\",\"stage\":\"cache\",\"secret\":\"private.example\"}",
            "{\"error\":\"private.example token=secret\",\"stage\":\"private.example\"}",
            "<html>private.example token=secret</html>"
        };
        for (int i = 0; i < bodies.length; i++) {
            final int at = i;
            var failed = new ImageDownloader(BASE, (uri, cancel, count) -> new ImageDownloader.Response(502,
                "application/json", bodies[at].getBytes(StandardCharsets.UTF_8), 0), QUIET, new SourceCircuit());
            try { failed.fetch(original, "worker", false, () -> false, count -> {}); throw new AssertionError("failure expected"); }
            catch (ImageDownloader.SourcesUnavailable error) {
                expect(!error.worker().contains("private") && !error.worker().contains("secret"), "no arbitrary server text reaches GUI");
                if (i == 0) expect(error.worker().contains("308") && error.worker().contains("7TV"), "exact CDN redirect status preserved");
                if (i == 1) expect(error.worker().contains("R2"), "cache failure distinguished from CDN failure");
                if (i > 1) expect(error.worker().equals("HTTP 502"), "unknown or malformed error remains safe status");
            }
        }
        System.out.println("CdnCanonicalCheck: " + checks + " assertions passed");
    }
}
