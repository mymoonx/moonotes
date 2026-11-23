package dev.mymoonx.moonotesnetwork;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Local display-only Streamotes glyph markers; does not mutate saved sign,
 * book or item data and never sends chat. Only plain, known registered names
 * are decorated; formatted/clickable components remain untouched.
 */
final class WorldEmoteText {
    private static final Pattern TOKEN = Pattern.compile("(?<![A-Za-z0-9_])(:?[A-Za-z0-9_]{2,70}:?)(?![A-Za-z0-9_])");
    private static volatile Method registryFind;
    private static volatile Method width;
    private static volatile Method request;
    private static volatile long lastLookupFailure;
    private WorldEmoteText() {}

    static Component decorate(Component original) { return decorate(original, false); }

    /** 3D signs use dark dye colours: keep the image itself white, not black. */
    static Component decorate(Component original, boolean brightenIcons) {
        if (original == null || !original.getSiblings().isEmpty()
                || original.getStyle().getClickEvent() != null) return original;
        String value = original.getString();
        if (value.isEmpty() || value.length() > 256) return original;
        // Fast path; no reflection for ordinary text without any known names.
        Matcher matcher = TOKEN.matcher(value);
        int start = 0, count = 0;
        MutableComponent out = Component.empty();
        Style base = original.getStyle();
        while (matcher.find() && count < 16) {
            String raw = matcher.group();
            String name = raw.startsWith(":") && raw.endsWith(":") && raw.length() > 2
                    ? raw.substring(1, raw.length() - 1) : raw;
            if (!known(name)) continue;
            if (matcher.start() > start)
                out.append(Component.literal(value.substring(start, matcher.start())).withStyle(base));
            Style glyph = base.withClickEvent(new ClickEvent.CopyToClipboard(name));
            if (brightenIcons) glyph = glyph.withColor(0xFFFFFF);
            out.append(Component.literal(name).withStyle(glyph));
            start = matcher.end();
            count++;
        }
        if (count == 0) return original;
        if (start < value.length())
            out.append(Component.literal(value.substring(start)).withStyle(base));
        return out;
    }

    private static boolean known(String name) {
        if (name == null || name.isEmpty()) return false;
        try {
            if (registryFind == null) {
                long now = System.currentTimeMillis();
                if (now - lastLookupFailure < 3000) return false;
                synchronized (WorldEmoteText.class) {
                    if (registryFind == null) {
                        try {
                            Class<?> registry = Class.forName("xeed.mc.streamotes.emoticon.EmoticonRegistry");
                            Class<?> emote = Class.forName("xeed.mc.streamotes.emoticon.Emoticon");
                            registryFind = registry.getMethod("fromName", String.class);
                            width = emote.getMethod("getWidth");
                            request = emote.getMethod("requestTexture");
                        } catch (ReflectiveOperationException | LinkageError error) {
                            lastLookupFailure = now;
                            return false;
                        }
                    }
                }
            }
            Object entry = registryFind.invoke(null, name);
            if (entry == null) return false;
            // Streamotes can know a name before its image is decoded.
            // Styling a width=0 glyph collapses sign lines into black specks.
            if (((Number)width.invoke(entry)).intValue() <= 0) {
                request.invoke(entry); // async, cached and deduplicated by Streamotes
                return false;          // vanilla text remains visible until ready
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            return false;
        }
    }
}
