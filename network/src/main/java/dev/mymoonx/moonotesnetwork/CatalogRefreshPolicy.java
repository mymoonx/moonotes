package dev.mymoonx.moonotesnetwork;

import java.util.concurrent.TimeUnit;

/** Rate limit for optional background metadata refresh. No images are downloaded here. */
final class CatalogRefreshPolicy {
    static final long INTERVAL_MS=TimeUnit.HOURS.toMillis(24);
    private CatalogRefreshPolicy() { }
    static boolean due(long lastAttempt,long now) {
        return lastAttempt <= 0 || lastAttempt > now + TimeUnit.MINUTES.toMillis(1)
            || now - lastAttempt >= INTERVAL_MS;
    }
}
