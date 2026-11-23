package dev.mymoonx.moonotesnetwork;

import java.net.URI;
import java.math.BigInteger;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates Streamotes 7TV image URLs and maps them to our fixed R2 Worker. */
public final class ProxyUrlResolver {
    // New 7TV IDs are 26-character ULIDs; older IDs can be hexadecimal.
    private static final Pattern IMAGE_PATH = Pattern.compile(
        "^/emote/([0-9A-HJKMNP-TV-Z]{26}|[a-f0-9]{16,40})/(1x|2x|3x|4x)\\.webp$",
        Pattern.CASE_INSENSITIVE
    );

    private ProxyUrlResolver() {}

    /** The CDN redirects lowercase ULIDs and legacy ObjectIds to a canonical ULID.
     * Build that fixed-origin URL directly; do not relax the HTTP redirect policy.
     * Cache/R2 keys deliberately keep the original lowercase ID for compatibility.
     */
    public static URI canonicalCdn(URI original) {
        Matcher match = imagePath(original);
        if (match == null) return null;
        String id = canonicalImageId(match.group(1));
        return URI.create("https://cdn.7tv.app/emote/" + id + "/" + match.group(2).toLowerCase(Locale.ROOT) + ".webp");
    }

    static String canonicalImageId(String id) {
        if (id.length() == 26 && id.matches("(?i)[0-7][0-9A-HJKMNP-TV-Z]{25}")) return id.toUpperCase(Locale.ROOT);
        if (id.matches("(?i)[a-f0-9]{24}")) {
            // SevenTV shared/src/database/id.rs::from_object_id:
            // timestamp seconds -> milliseconds in the high 48 bits, last 8 bytes unchanged.
            BigInteger value = new BigInteger(id.substring(0, 8), 16).multiply(BigInteger.valueOf(1000))
                .shiftLeft(80).or(new BigInteger(id.substring(8), 16));
            String alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
            char[] encoded = new char[26];
            for (int i = encoded.length - 1; i >= 0; i--) {
                encoded[i] = alphabet.charAt(value.and(BigInteger.valueOf(31)).intValue());
                value = value.shiftRight(5);
            }
            return new String(encoded);
        }
        return id.toLowerCase(Locale.ROOT);
    }

    private static Matcher imagePath(URI original) {
        if (original == null || !"https".equalsIgnoreCase(original.getScheme())
                || !"cdn.7tv.app".equalsIgnoreCase(original.getHost())
                || original.getRawUserInfo() != null
                || (original.getPort() != -1 && original.getPort() != 443)
                || original.getRawPath() == null || !original.getRawPath().equals(original.getPath())
                || original.getRawQuery() != null || original.getRawFragment() != null) return null;
        Matcher match = IMAGE_PATH.matcher(original.getPath());
        return match.matches() ? match : null;
    }

    public static URI resolve(URI original, String workerBaseUrl) {
        if (original == null || workerBaseUrl == null || workerBaseUrl.isBlank()) return null;
        Matcher match = imagePath(original);
        if (match == null) return null;

        try {
            URI base = URI.create(workerBaseUrl);
            if (!"https".equalsIgnoreCase(base.getScheme())
                    || base.getHost() == null
                    || (base.getPort() != -1 && base.getPort() != 443)
                    || base.getRawUserInfo() != null
                    || base.getRawQuery() != null
                    || base.getRawFragment() != null
                    || (base.getRawPath() != null && !base.getRawPath().isEmpty()
                        && !"/".equals(base.getRawPath()))) return null;

            String id = match.group(1).toLowerCase(Locale.ROOT);
            String size = match.group(2).toLowerCase(Locale.ROOT);
            String worker = base.toString().replaceAll("/+$", "")
                    + "/v1/7tv/" + id + "/" + size + ".webp";
            return URI.create(worker);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
