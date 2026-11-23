package dev.mymoonx.moonotesnetwork;

import java.util.Locale;

/** Local, copy-only diagnostics. No network or filesystem writes. */
public final class DiagnosticsReport {
    private DiagnosticsReport() {}

    public static String errorKind(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            String kind = errorKind(current.getClass().getSimpleName() + " " + current.getMessage());
            if (!kind.equals("other") && !kind.equals("none")) return kind;
        }
        return error == null ? "none" : "other";
    }

    public static String errorKind(String detail) {
        if (detail == null || detail.isBlank()) return "none";
        String s = detail.toLowerCase(Locale.ROOT);
        if (s.equals("none") || s.equals("timeout") || s.equals("dns") || s.equals("tls")
            || s.equals("connection") || s.equals("http_4xx") || s.equals("http_5xx")
            || s.equals("invalid_image") || s.equals("size_limit") || s.equals("other")) return s;
        if (s.contains("timeout") || s.contains("timed out")) return "timeout";
        if (s.contains("unknownhost") || s.contains("dns") || s.contains("resolv")) return "dns";
        if (s.contains("ssl") || s.contains("certif") || s.contains("tls")) return "tls";
        if (s.contains("connection") || s.contains("connect") || s.contains("reset")) return "connection";
        if (s.contains("http 4")) return "http_4xx";
        if (s.contains("http 5")) return "http_5xx";
        if (s.contains("size") || s.contains("large") || s.contains("exceeds")) return "size_limit";
        if (s.contains("content-type") || s.contains("signature") || s.contains("webp")) return "invalid_image";
        return "other";
    }

    public static String build(String version, String minecraft, String javaVersion, String os,
            String sourceMode, String catalogSource, int catalogCount, int cacheCount,
            int progress, int failures, long directOk, long workerOk, long fallbacks,
            long directLatency, long workerLatency, String lastSource, String catalogError,
            String imageError, int clicks, int drags, boolean previewOpen, boolean enabled) {
        if (!enabled) return "Moonotes local diagnostics disabled";
        return "Moonotes Network local report\n"
            + "Network version: " + version(version) + "\n"
            + "Minecraft version: " + fixed(minecraft) + "\n"
            + "Java major: " + javaMajor(javaVersion) + "\n"
            + "OS family: " + osFamily(os) + "\n"
            + "Image source: " + fixed(sourceMode) + "\n"
            + "Catalog source: " + catalogKind(catalogSource) + "\n"
            + "Catalog/cache: " + catalogCount + "/" + cacheCount + "\n"
            + "Progress/failures: " + progress + "/" + failures + "\n"
            + "Direct/Cloudflare successes: " + directOk + "/" + workerOk + "\n"
            + "Fallbacks: " + fallbacks + "\n"
            + "Last latency ms (direct/Cloudflare): " + directLatency + "/" + workerLatency + "\n"
            + "Last image source: " + fixed(lastSource) + "\n"
            + "Catalog error class: " + errorKind(catalogError) + "\n"
            + "Image error class: " + errorKind(imageError) + "\n"
            + "UI mouse clicks/drags: " + clicks + "/" + drags + "\n"
            + "UI preview open: " + previewOpen + "\n"
            + "No automatic upload. Review before sharing.\n";
    }
    private static String fixed(String s) {
        if (s == null) return "unknown";
        return switch (s) {
            case "26.3", "Авто", "7TV", "Cloudflare", "нет" -> s;
            default -> "unknown";
        };
    }
    private static String version(String value) {
        return value != null && value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){1,3}(-[a-z0-9]{1,16})?") ? value : "unknown";
    }
    private static String catalogKind(String source) {
        if (source == null) return "unknown";
        if (source.contains("Cloudflare")) return "cloudflare";
        if (source.contains("Встроенн")) return "bundled";
        if (source.contains("Локальн") || source.contains("Резервн")) return "local";
        return "other";
    }
    private static String javaMajor(String v) {
        if (v == null) return "unknown";
        String major = v.split("[.\\-+]", 2)[0];
        return major.matches("[0-9]{1,2}") ? major : "unknown";
    }
    private static String osFamily(String name) {
        if (name == null) return "other";
        String value = name.toLowerCase(Locale.ROOT);
        if (value.contains("windows")) return "Windows";
        if (value.contains("mac")) return "macOS";
        if (value.contains("linux")) return "Linux";
        return "other";
    }
}
