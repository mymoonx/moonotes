package dev.mymoonx.moonotesnetwork;

import java.net.URI;

/** Can be run with plain javac/java, independent of Fabric. */
public final class ProxyUrlResolverCheck {
    private static void expect(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
    public static void main(String[] args) {
        final String base = "https://moonotes-emotes.thefreelom.workers.dev";
        String newId = "01F6MZGCNG000255K4X1K7NTHR";
        URI resolved = ProxyUrlResolver.resolve(
            URI.create("https://cdn.7tv.app/emote/" + newId + "/2x.webp"), base);
        expect(resolved != null && resolved.toString().equals(base + "/v1/7tv/" + newId.toLowerCase() + "/2x.webp"), "ULID mapping");
        expect(ProxyUrlResolver.resolve(URI.create("https://cdn.7tv.app/emote/0123456789abcdef12345678/1x.webp"), base) != null, "legacy ID");
        expect(ProxyUrlResolver.resolve(URI.create("https://evil.example/emote/" + newId + "/2x.webp"), base) == null, "reject other host");
        expect(ProxyUrlResolver.resolve(URI.create("http://cdn.7tv.app/emote/" + newId + "/2x.webp"), base) == null, "reject HTTP");
        expect(ProxyUrlResolver.resolve(URI.create("https://cdn.7tv.app/emote/" + newId + "/8x.webp"), base) == null, "reject unsupported size");
        expect(ProxyUrlResolver.resolve(URI.create("https://cdn.7tv.app/emote/" + newId + "/2x.webp?x=y"), base) == null, "reject query");
        expect(ProxyUrlResolver.resolve(URI.create("https://cdn.7tv.app/emote/" + newId + "/2x.webp"), "https://host.example/other") == null, "reject custom path");
        expect(ProxyUrlResolver.resolve(URI.create("https://user@cdn.7tv.app/emote/" + newId + "/2x.webp"), base) == null, "reject userinfo");
        expect(ProxyUrlResolver.resolve(URI.create("https://cdn.7tv.app:8443/emote/" + newId + "/2x.webp"), base) == null, "reject custom port");
        expect(ProxyUrlResolver.resolve(URI.create("https://cdn.7tv.app/emote/" + newId + "/%32x.webp"), base) == null, "reject encoded path");
        expect(ProxyUrlResolver.resolve(URI.create("https://cdn.7tv.app"), base) == null, "reject missing path");
        expect(ProxyUrlResolver.canonicalCdn(URI.create("https://cdn.7tv.app/emote/" + newId.toLowerCase() + "/2x.webp"))
            .toString().equals("https://cdn.7tv.app/emote/" + newId + "/2x.webp"), "lowercase ULID bypasses CDN 308");
        URI old = URI.create("https://cdn.7tv.app/emote/60a304efac2bcb20ef20fa89/2x.webp");
        expect(ProxyUrlResolver.canonicalCdn(old).toString()
            .equals("https://cdn.7tv.app/emote/01F5YCPHCR000ARAYB43QJ1YM9/2x.webp"), "ObjectId uses published 7TV ULID mapping");
        expect(ProxyUrlResolver.resolve(old, base).getPath().equals("/v1/7tv/60a304efac2bcb20ef20fa89/2x.webp"), "legacy R2 key remains unchanged");
        expect(ProxyUrlResolver.canonicalImageId("60b9e0e9b55f4fa0ed9e2785").equals("01F7B1D3H8000BAQTFM3PSW9W5"), "second live legacy mapping");
        expect(ProxyUrlResolver.canonicalCdn(URI.create("https://evil.example/emote/" + newId + "/2x.webp")) == null, "canonicalization never expands allowlist");
        expect(ProxyUrlResolver.canonicalCdn(URI.create("https://cdn.7tv.app/emote/" + newId + "/2x.webp?x=y")) == null, "canonical URL refuses query");
        System.out.println("Moonotes Network URL checks passed (17 checks)");
    }
}
