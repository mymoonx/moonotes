package dev.mymoonx.moonotesnetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Render-only token selection for the message argument of explicitly allowed
 * commands. It never rewrites the actual command, nickname or recipient.
 * Offset denotes the first UTF-16 character of the visible EditBox segment.
 */
final class PrivateMessagePreviewTokens {
    static final int MAX_TEXT = 256;
    private static final int MAX_MATCHES = 32;
    private static final Pattern WORD = Pattern.compile("[^\\s:]{2,}|:?[^\\s:]+:?");
    record Span(int start, int end, String name) { }

    private PrivateMessagePreviewTokens() { }

    static List<Span> find(String draft, String visible, int offset, int body,
            Predicate<String> known) {
        if (draft == null || visible == null || known == null || body < 0
                || !draft.startsWith("/") || draft.length() > MAX_TEXT || visible.isEmpty()
                || offset < 0 || offset + visible.length() > draft.length()
                || !draft.regionMatches(offset, visible, 0, visible.length()))
            return List.of();

        List<Span> selected = new ArrayList<>();
        Matcher matcher = WORD.matcher(visible);
        while (matcher.find() && selected.size() < MAX_MATCHES) {
            int globalStart = offset + matcher.start();
            int globalEnd = offset + matcher.end();
            if (globalStart < body || globalStart > 0
                    && !Character.isWhitespace(draft.codePointBefore(globalStart)))
                continue;
            if (globalEnd < draft.length()
                    && !Character.isWhitespace(draft.codePointAt(globalEnd)))
                continue;
            String typed = matcher.group();
            String name = typed;
            if (!known.test(name)) {
                if (name.length() <= 2 || name.charAt(0) != ':'
                        || name.charAt(name.length() - 1) != ':') continue;
                name = name.substring(1, name.length() - 1);
                if (!known.test(name)) continue;
            }
            selected.add(new Span(matcher.start(), matcher.end(), name));
        }
        return List.copyOf(selected);
    }
}
