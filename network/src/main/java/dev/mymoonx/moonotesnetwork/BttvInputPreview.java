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
        if (modifiers.isEmpty()) return null;

        List<Token> tokens = new ArrayList<>();
        Matcher words = WORD.matcher(draft);
        while (words.find() && tokens.size() < 64) {
            int wordStart = words.start(), wordEnd = words.end();
            String word = words.group();
            // A valid modifier is syntax, even when someone also registered
            // an emote named "w!" or "h!".
            boolean isModifier = modifiers.stream().anyMatch(pair ->
                wordStart == pair.start() && wordEnd <= pair.emoteStart());
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
                segments.add(FormattedCharSequence.forward(
                    draft.substring(cursor, token.start()), Style.EMPTY));
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
            segments.add(FormattedCharSequence.forward(draft.substring(cursor, last), Style.EMPTY));
        return segments.isEmpty() ? null : FormattedCharSequence.composite(segments);
    }
}
