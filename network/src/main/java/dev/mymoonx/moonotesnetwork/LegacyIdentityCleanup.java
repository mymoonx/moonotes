package dev.mymoonx.moonotesnetwork;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.io.IOException;

/**
 * One-time privacy cleanup when upgrading from cancelled Twitch/paint alphas.
 * ONLY exact files created by Moonotes' abandoned experimental features are
 * deleted. Never touch Streamotes files, collections or the emote cache.
 */
final class LegacyIdentityCleanup {
    private static final String[] OBSOLETE = {
        "moonotes-twitch-link.json",
        "moonotes-paint-settings.json",
        "moonotes-paint.json",
        "moonotes-paints.json",
        "moonotes-plus-paint-cache.json"
    };

    private LegacyIdentityCleanup() { }

    static int clean(Path configDir) {
        if (configDir == null) return 0;
        int removed = 0;
        for (String filename : OBSOLETE) {
            Path file = configDir.resolve(filename);
            try {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    && Files.size(file) <= 1024L * 1024
                    && Files.deleteIfExists(file)) removed++;
            } catch (IOException | SecurityException ignored) {
                // Failure to remove a legacy file must never block game startup.
            }
        }
        return removed;
    }

    static void run() {
        int deleted = clean(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
        if (deleted > 0)
            System.out.println("[Moonotes] Removed " + deleted + " obsolete Twitch/paint settings file(s)");
    }
}
