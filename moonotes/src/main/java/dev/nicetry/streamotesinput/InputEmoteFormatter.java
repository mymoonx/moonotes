package dev.nicetry.streamotesinput;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formats visible chat input while preserving the original plain-text buffer.
 * Streamotes' own font mixin interprets CopyToClipboard(name) as an emote glyph.
 * Reflection avoids shipping, shading or copying Streamotes classes.
 */
public final class InputEmoteFormatter {
    private static final Pattern WORD = Pattern.compile("[^\\s:]{2,}|:?[^\\s:]+:?");
    private static Method fromName;
    private static boolean lookupAttempted;
    private static Method configuredPrefix;
    private static boolean prefixLookupAttempted;

    private InputEmoteFormatter() {}

    public static FormattedCharSequence format(String value, Integer ignoredOffset) {
        if (value == null || value.isEmpty()) return FormattedCharSequence.EMPTY;

        Matcher matcher = WORD.matcher(value);
        List<FormattedCharSequence> parts = new ArrayList<>();
        int end = 0;
        while (matcher.find()) {
            String typed = matcher.group();
            // Only match exact names in this first prototype. :name: and punctuation
            // need a more advanced cursor/selection-aware rich-text field.
            if (typed.startsWith(":") || typed.endsWith(":")) continue;
            String emoteName = typed;
            int nameStart = matcher.start();
            if (!knownEmote(typed)) {
                String global = globalPrefix();
                if (global.isEmpty() || !typed.startsWith(global)
                        || typed.length() <= global.length()) continue;
                String afterPrefix = typed.substring(global.length());
                if (!knownEmote(afterPrefix)) continue;
                emoteName = afterPrefix;
                nameStart += global.length();
            }

            if (nameStart > end) {
                parts.add(FormattedCharSequence.forward(value.substring(end, nameStart), Style.EMPTY));
            }
            Style emoteStyle = Style.EMPTY.withClickEvent(new ClickEvent.CopyToClipboard(emoteName));
            parts.add(FormattedCharSequence.forward(emoteName, emoteStyle));
            end = matcher.end();
        }
        if (end < value.length()) {
            parts.add(FormattedCharSequence.forward(value.substring(end), Style.EMPTY));
        }
        return parts.size() == 1 ? parts.get(0) : FormattedCharSequence.composite(parts);
    }

    /**
     * Network is optional. If it is installed, use its per-world chat prefix;
     * otherwise the legacy global prefix defaults to !.
     */
    private static String globalPrefix() {
        try {
            if (!prefixLookupAttempted) {
                prefixLookupAttempted = true;
                Class<?> bridge = Class.forName("dev.mymoonx.moonotesnetwork.StreamotesBridge");
                configuredPrefix = bridge.getMethod("activeChatPrefix");
            }
            if (configuredPrefix != null) {
                Object value = configuredPrefix.invoke(null);
                if (value instanceof String prefix && prefix.length() >= 1 && prefix.length() <= 4)
                    return prefix;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) { }
        return "!";
    }

    private static boolean knownEmote(String name) {
        try {
            if (!lookupAttempted) {
                lookupAttempted = true;
                Class<?> registry = Class.forName("xeed.mc.streamotes.emoticon.EmoticonRegistry");
                fromName = registry.getMethod("fromName", String.class);
            }
            return fromName != null && fromName.invoke(null, name) != null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
