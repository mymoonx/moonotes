package dev.mymoonx.moonotesnetwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * BTTV syntax inside an editable chat field is styled in place: removing
 * "w! " from the display would desynchronise Minecraft's cursor, selection
 * and scrolling from the original chat buffer.
 *
 * The final-stage Network formatter uses the original draft, so preview works
 * even when the optional core chat formatter has not been updated.
 */
public final class BttvInputPreview {
    private static final Pattern WORD = Pattern.compile("[^\\s:]{2,}|:?[^\\s:]+:?");
    // Recognize standalone BTTV operators even before the next emote is
    // fully typed. This prevents Streamotes from trying to download "w!"
    // as an image and keeps chat autocomplete and the caret aligned.
    private static final Pattern PREFIX = Pattern.compile("(?<!\\S)!?([whvz]!)(?=\\s|$)");
    private static final Style ACTIVE_PREFIX = Style.EMPTY.withColor(0x69C8D1);
    private static final Style PENDING_PREFIX = Style.EMPTY.withColor(0x989CA5);
    private record Prefix(int start, int end, boolean active) { }

    private static final int MAX_DRAFT = 256;
    private record Token(int start, int end, String name, BttvModifierSyntax.Modifier modifier) { }

    private BttvInputPreview() { }

    /** Optional hook for moonotes core; null leaves its classic formatter alone. */
    public static FormattedCharSequence format(String text, Integer ignoredOffset) {
        return formatVisible(text, text, 0, StreamotesBridge::registeredEmoteName);
    }

    /** Final EditBox formatter: only the currently visible original substring. */
    static FormattedCharSequence formatVisible(String draft, String visible, int offset) {
        return formatVisible(draft, visible, offset, StreamotesBridge::registeredEmoteName);
    }

    static FormattedCharSequence formatVisible(String draft, String visible, int offset,
            Predicate<String> registered) {
        if (draft == null || visible == null || registered == null || draft.isEmpty()
                || draft.length() > MAX_DRAFT || draft.stripLeading().startsWith("/")
                || offset < 0 || offset > draft.length() || (long)offset + visible.length() > draft.length()
                || !draft.regionMatches(offset, visible, 0, visible.length()))
            return null;

        List<BttvModifierSyntax.Match> modifiers = BttvModifierSyntax.find(draft, registered);
        List<Prefix> prefixes = new ArrayList<>();
        Matcher prefixMatcher = PREFIX.matcher(draft);
        while (prefixMatcher.find() && prefixes.size() < 32) {
            int start = prefixMatcher.start(1), end = prefixMatcher.end(1);
            boolean active = modifiers.stream().anyMatch(pair ->
                pair.start() == start && pair.emoteStart() > end);
            prefixes.add(new Prefix(start, end, active));
        }
        if (prefixes.isEmpty()) return null;
        if (!modifiers.isEmpty()) BttvRenderProbe.input();

        List<Token> tokens = new ArrayList<>();
        Matcher words = WORD.matcher(draft);
        while (words.find() && tokens.size() < 64) {
            int wordStart = words.start(), wordEnd = words.end();
            String word = words.group();
            // A valid modifier is syntax, even when someone also registered
            // an emote named "w!" or "h!".
            boolean isModifier = prefixes.stream().anyMatch(prefix ->
                (prefix.start() == wordStart && prefix.end() == wordEnd)
                || (wordStart >= 0 && wordStart < draft.length()
                    && draft.charAt(wordStart) == '!'
                    && prefix.start() == wordStart + 1 && prefix.end() == wordEnd));
            if (isModifier) continue;

            BttvModifierSyntax.Modifier effect = null;
            String name = word;
            int start = wordStart;
            for (var pair : modifiers) {
                if (pair.emoteStart() == wordStart && pair.end() == wordEnd) {
                    name = pair.emote();
                    effect = pair.modifier();
                    break;
                }
            }

            if (!registered.test(name)) {
                String prefix = StreamotesBridge.activeChatPrefix();
                if (prefix == null || prefix.isEmpty() || !name.startsWith(prefix)
                        || name.length() <= prefix.length()) continue;
                String unprefixed = name.substring(prefix.length());
                if (!registered.test(unprefixed)) continue;
                start += prefix.length();
                name = unprefixed;
            }
            tokens.add(new Token(start, wordEnd, name, effect));
        }
        tokens.sort(Comparator.comparingInt(Token::start));

        List<FormattedCharSequence> segments = new ArrayList<>();
        int cursor = offset;
        int last = offset + visible.length();
        for (Token token : tokens) {
            if (token.start() < cursor || token.end() > last) continue;
            if (token.start() > cursor)
                appendLiteral(segments, draft, cursor, token.start(), prefixes);
            Style style = Style.EMPTY.withClickEvent(new ClickEvent.CopyToClipboard(token.name()));
            if (token.modifier() != null
                    && token.modifier() != BttvModifierSyntax.Modifier.ZERO_SPACE)
                style = style.withInsertion(BttvModifierSyntax.marker(token.modifier()));
            // The displayed spelling stays exactly as typed; the special
            // Streamotes glyph style is applied only to the emote span.
            segments.add(FormattedCharSequence.forward(
                draft.substring(token.start(), token.end()), style));
            cursor = token.end();
        }
        if (cursor < last)
            appendLiteral(segments, draft, cursor, last, prefixes);
        return segments.isEmpty() ? null : FormattedCharSequence.composite(segments);
    }

    /** Only visual styling; no characters are removed from the draft. */
    private static void appendLiteral(List<FormattedCharSequence> out, String draft,
            int start, int end, List<Prefix> prefixes) {
        int position = start;
        for (Prefix prefix : prefixes) {
            if (prefix.start() < position || prefix.end() > end) continue;
            if (position < prefix.start())
                out.add(FormattedCharSequence.forward(
                    draft.substring(position, prefix.start()), Style.EMPTY));
            out.add(FormattedCharSequence.forward(
                draft.substring(prefix.start(), prefix.end()),
                prefix.active() ? ACTIVE_PREFIX : PENDING_PREFIX));
            position = prefix.end();
        }
        if (position < end)
            out.add(FormattedCharSequence.forward(draft.substring(position, end), Style.EMPTY));
    }
}
