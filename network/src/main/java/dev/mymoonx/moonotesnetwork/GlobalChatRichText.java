package dev.mymoonx.moonotesnetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

/**
 * Presentation-only repair for messages whose global prefix and emote name
 * span separate styled components (for example, a server chat plugin).
 * Never modifies chat packets, stored Components or their plain text.
 */
public final class GlobalChatRichText {
    private static final int MAX_TEXT = 4096;
    private record Span(int start, int end, Style style) { }

    private GlobalChatRichText() { }

    public static FormattedText decorate(FormattedText original, String prefix,
            Predicate<String> registered) {
        if (original == null || prefix == null || prefix.isEmpty() || registered == null)
            return original;
        var styles = new ArrayList<Span>();
        var full = new StringBuilder();
        final boolean[] tooLong = {false};
        original.visit((style, part) -> {
            if (part == null || part.isEmpty()) return Optional.empty();
            if ((long)full.length() + part.length() > MAX_TEXT) {
                tooLong[0] = true;
                return Optional.empty();
            }
            int start = full.length();
            full.append(part);
            styles.add(new Span(start, full.length(), style));
            return Optional.empty();
        }, Style.EMPTY);
        if (tooLong[0] || full.isEmpty() || full.indexOf(prefix) < 0)
            return original;

        var matches = GlobalChatPrefix.matches(full.toString(), prefix, registered);
        if (matches.isEmpty()) return original;
        var parts = new ArrayList<FormattedText>();
        int cursor = 0;
        boolean changed = false;
        for (var match : matches) {
            if (match.prefixStart() < cursor
                    || !safe(styles, match.prefixStart(), match.end())) continue;
            originalRange(parts, full, styles, cursor, match.contentStart());
            Style emoteStyle = styleAt(styles, match.contentStart());
            if (emoteStyle == null) continue;
            parts.add(FormattedText.of(match.name(),
                emoteStyle.withClickEvent(new ClickEvent.CopyToClipboard(match.name()))));
            cursor = match.end();
            changed = true;
        }
        if (!changed) return original;
        originalRange(parts, full, styles, cursor, full.length());
        return FormattedText.composite(parts);
    }

    private static boolean safe(List<Span> spans, int start, int end) {
        for (Span span : spans) {
            if (span.end() <= start || span.start() >= end) continue;
            if (span.style().getClickEvent() != null || span.style().isObfuscated())
                return false;
        }
        return true;
    }

    private static Style styleAt(List<Span> spans, int at) {
        for (Span span : spans)
            if (span.start() <= at && at < span.end()) return span.style();
        return null;
    }

    private static void originalRange(List<FormattedText> output, StringBuilder full,
            List<Span> spans, int start, int end) {
        for (Span span : spans) {
            int a = Math.max(start, span.start());
            int b = Math.min(end, span.end());
            if (a < b) output.add(FormattedText.of(
                full.substring(a, b), span.style()));
        }
    }
}
