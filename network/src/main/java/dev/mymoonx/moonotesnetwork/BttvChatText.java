package dev.mymoonx.moonotesnetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

/**
 * Presentation-only BTTV modifier decoration for server chat and drafts.
 * Removes the modifier prefix in the *displayed* text, keeps the original
 * message exactly unchanged, and uses the real registered Streamotes alias.
 *
 * It also handles styled message fragments from Paper chat plugins. Styles
 * retain their click/insertion styles everywhere except the replaced emote
 * token, where Streamotes requires its own glyph marker. The original
 * server message and all other clickable text remain untouched.
 */
public final class BttvChatText {
    private static final int MAX_TEXT = 4096;
    private record Span(int start, int end, Style style) { }
    private BttvChatText() { }

    public static FormattedText decorate(FormattedText source) {
        return decorate(source, StreamotesBridge::registeredEmoteName);
    }

    static FormattedText decorate(FormattedText source, Predicate<String> registered) {
        if (source == null || registered == null) return source;
        var spans = new ArrayList<Span>();
        var text = new StringBuilder();
        final boolean[] invalid = {false};
        source.visit((style, piece) -> {
            if (invalid[0] || piece == null || piece.isEmpty()) return Optional.empty();
            if ((long) text.length() + piece.length() > MAX_TEXT) {
                invalid[0] = true;
                return Optional.empty();
            }
            int start = text.length();
            text.append(piece);
            spans.add(new Span(start, text.length(), style));
            return Optional.empty();
        }, Style.EMPTY);
        if (invalid[0] || text.length() < 4 || text.indexOf("! ") < 0) return source;

        List<BttvModifierSyntax.Match> pairs =
            BttvModifierSyntax.find(text.toString(), registered);
        if (pairs.isEmpty()) return source;

        var display = new ArrayList<FormattedText>();
        int cursor = 0, applied = 0;
        for (var pair : pairs) {
            if (pair.start() < cursor || !safe(spans, pair.start(), pair.end())) continue;
            Style style = at(spans, pair.emoteStart());
            if (style == null) continue;
            int precedingEnd = pair.start();
            if (pair.modifier() == BttvModifierSyntax.Modifier.ZERO_SPACE
                    && precedingEnd > cursor && Character.isWhitespace(text.charAt(precedingEnd - 1))) {
                // z! only removes the immediately preceding visual separator.
                // Do not erase the original message, change emote width, or
                // turn z! into an unrelated 7TV zero-width overlay.
                precedingEnd--;
            }
            append(display, text, spans, cursor, precedingEnd);
            Style marked = style.withClickEvent(new ClickEvent.CopyToClipboard(pair.emote()));
            if (pair.modifier() != BttvModifierSyntax.Modifier.ZERO_SPACE)
                marked = marked.withInsertion(BttvModifierSyntax.marker(pair.modifier()));
            display.add(FormattedText.of(pair.emote(), marked));
            if (pair.modifier() == BttvModifierSyntax.Modifier.WIDE)
                display.add(FormattedText.of(widePadding(pair.emote()), Style.EMPTY));
            cursor = pair.end();
            applied++;
        }
        if (applied == 0) return source;
        BttvRenderProbe.chat();
        append(display, text, spans, cursor, text.length());
        return FormattedText.composite(display);
    }

    private static String widePadding(String name) {
        float width = StreamotesBridge.chatEmoteWidth(name);
        if (width <= 0) width = 16;
        Minecraft mc = Minecraft.getInstance();
        int space = mc != null && mc.font != null ? Math.max(1, mc.font.width(" ")) : 4;
        return " ".repeat(Math.clamp((int) Math.ceil(width / space), 1, 14));
    }

    private static boolean safe(List<Span> spans, int start, int end) {
        for (Span span : spans) {
            if (span.start() >= end || span.end() <= start) continue;
            // Paper plugins can apply a click/insertion style to an entire
            // chat message, including the visible text. The original styles
            // stay intact outside the emote token. Only the recognized emote
            // glyph needs Streamotes' CopyToClipboard(name) marker (and our
            // render insertion tag), just as native Streamotes does.
            if (span.style().isObfuscated()) return false;
        }
        return true;
    }

    private static Style at(List<Span> spans, int index) {
        for (Span span : spans)
            if (span.start() <= index && index < span.end()) return span.style();
        return null;
    }

    private static void append(List<FormattedText> out, StringBuilder text,
            List<Span> spans, int start, int end) {
        for (Span span : spans) {
            int from = Math.max(start, span.start());
            int to = Math.min(end, span.end());
            if (from < to)
                out.add(FormattedText.of(text.substring(from, to), span.style()));
        }
    }
}
