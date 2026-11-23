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
    private static volatile boolean attempted;
    private WorldEmoteText() {}

    static Component decorate(Component original) {
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
            out.append(Component.literal(name).withStyle(base.withClickEvent(new ClickEvent.CopyToClipboard(name))));
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
            if (!attempted) {
                synchronized (WorldEmoteText.class) {
                    if (!attempted) {
                        attempted = true;
                        registryFind = Class.forName("xeed.mc.streamotes.emoticon.EmoticonRegistry")
                            .getMethod("fromName", String.class);
                    }
                }
            }
            Method lookup = registryFind;
            return lookup != null && lookup.invoke(null, name) != null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            return false;
        }
    }
}
