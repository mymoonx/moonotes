package dev.mymoonx.moonotesnetwork;

import java.lang.reflect.Method;

/**
 * Streamotes uses one FIFO image-loader thread. BTTV registers its layout
 * modifiers as special names, but requesting their images can time out and
 * block all other emote images waiting in the same queue.
 *
 * Only suppress the four BTTV layout-control pseudo-images; never suppress
 * a normal 7TV/Twitch/FFZ emote, including a similarly named emote there.
 * No remote request, temporary image, or additional cache is created.
 */
public final class BttvImageRequestGuard {
    private BttvImageRequestGuard() { }

    static boolean reservedName(String name) {
        return "w!".equals(name) || "h!".equals(name)
            || "v!".equals(name) || "z!".equals(name);
    }

    static boolean skip(String provider, String name) {
        return provider != null && provider.equalsIgnoreCase("bttv")
            && reservedName(name);
    }

    /** Called only by the shared Streamotes image-load mixin. */
    public static boolean skip(String provider, Object emote) {
        if (provider == null || !provider.equalsIgnoreCase("bttv") || emote == null)
            return false;
        try {
            Method name = emote.getClass().getMethod("getName");
            Object value = name.invoke(emote);
            return value instanceof String id && reservedName(id);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Never break the rest of Streamotes if its API changes.
            return false;
        }
    }
}
