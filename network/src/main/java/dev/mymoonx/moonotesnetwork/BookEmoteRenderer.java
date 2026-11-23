package dev.mymoonx.moonotesnetwork;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Cache-backed page images, independent of Streamotes' font/glyph state. */
public final class BookEmoteRenderer {
    private static EmotePreviews previews;
    private BookEmoteRenderer() { }

    private static EmotePreviews previews() {
        if (previews == null) previews = new EmotePreviews();
        return previews;
    }

    public static void disconnect() {
        if (previews != null) previews.close();
        previews = null;
    }

    public static void drawPageLine(GuiGraphicsExtractor graphics, ActiveTextCollector collector,
                                    int x, int y, FormattedCharSequence original, PageContext page) {
        if (!ExtrasSettings.local().bookText()) {
            collector.accept(x, y, original);
            return;
        }
        StyledLine line = StyledLine.read(original);
        List<EmoteTextTokens.Match> matches = page == null ? List.of() : page.matches(line.text());
        if (matches.isEmpty()) {
            collector.accept(x, y, original);
            return;
        }
        Font font = Minecraft.getInstance().font;
        int end = 0;
        for (EmoteTextTokens.Match match : matches) {
            // Server-authored links retain their appearance and hit area.
            if (line.clickable(match.start(), match.end())) continue;
            if (previews().texture(match.id()) == null) {
                if (previews().missing(match.id())) WorldImageRequests.request(match);
                continue;
            }
            if (end < match.start()) collector.accept(x + font.width(line.slice(0, end)), y,
                    line.slice(end, match.start()));
            int before = font.width(line.slice(0, match.start()));
            int reserved = font.width(line.slice(match.start(), match.end()));
            drawImage(graphics, match.id(), x + before, y, reserved, font.lineHeight);
            end = match.end();
        }
        if (end == 0) collector.accept(x, y, original);
        else if (end < line.text().length()) collector.accept(x + font.width(line.slice(0, end)), y,
                line.slice(end, line.text().length()));
    }

    public static boolean drawEditablePart(GuiGraphicsExtractor graphics, Font font, String fullText,
                                            int offset, String part, int x, int y, int color, boolean shadow) {
        if (!ExtrasSettings.local().bookText() || offset < 0
                || offset + part.length() > fullText.length()
                || !fullText.regionMatches(offset, part, 0, part.length())) return false;
        List<EmoteTextTokens.Match> matches = editableMatches(fullText, offset, offset + part.length());
        int end = 0;
        for (EmoteTextTokens.Match match : matches) {
            if (previews().texture(match.id()) == null) {
                if (previews().missing(match.id())) WorldImageRequests.request(match);
                continue;
            }
            int start = match.start() - offset, finish = match.end() - offset;
            if (end < start) graphics.text(font, part.substring(end, start),
                    x + font.width(part.substring(0, end)), y, color, shadow);
            int before = font.width(part.substring(0, start));
            int reserved = font.width(part.substring(start, finish));
            drawImage(graphics, match.id(), x + before, y, reserved, font.lineHeight);
            end = finish;
        }
        if (end == 0) return false;
        if (end < part.length()) graphics.text(font, part.substring(end),
                x + font.width(part.substring(0, end)), y, color, shadow);
        return true;
    }

    static List<EmoteTextTokens.Match> editableMatches(String fullText, int start, int end) {
        if (fullText == null || start < 0 || end < start || end > fullText.length()) return List.of();
        return EmoteTextTokens.find(fullText).stream()
                .filter(match -> match.start() >= start && match.end() <= end).toList();
    }

    /** Map wrapped lines back to full tokens, never interpreting fragments as aliases. */
    public static final class PageContext {
        private final String text;
        private final List<EmoteTextTokens.Match> tokens;
        private int cursor;
        private boolean aligned = true;

        public PageContext(String text) {
            this.text = text == null ? "" : text;
            tokens = EmoteTextTokens.find(text);
        }

        List<EmoteTextTokens.Match> matches(String line) {
            if (!aligned || line == null) return List.of();
            if (line.isEmpty()) {
                if (cursor < text.length() && text.charAt(cursor) == '\n') cursor++;
                return List.of();
            }
            int start = text.indexOf(line, cursor);
            if (start < 0 || !text.substring(cursor, start).isBlank()) {
                // Bidi-reordered or otherwise transformed lines retain vanilla text.
                aligned = false;
                return List.of();
            }
            cursor = start + line.length();
            int end = cursor;
            return tokens.stream().filter(token -> token.start() >= start && token.end() <= end)
                    .map(token -> new EmoteTextTokens.Match(token.start() - start,
                            token.end() - start, token.id(), token.name())).toList();
        }
    }

    private static void drawImage(GuiGraphicsExtractor graphics, String id, int x, int y,
                                   int reservedWidth, int lineHeight) {
        int side = Math.max(1, Math.min(lineHeight, reservedWidth));
        previews().draw(graphics, id, x + (reservedWidth - side) / 2,
                y + (lineHeight - side) / 2, side);
    }

    /** Uses UTF-16 offsets like book editing, retaining code points and styles. */
    record Glyph(int offset, int codePoint, Style style) { }
    record StyledLine(String text, List<Glyph> glyphs) {
        static StyledLine read(FormattedCharSequence value) {
            StringBuilder text = new StringBuilder();
            List<Glyph> glyphs = new ArrayList<>();
            value.accept((index, style, cp) -> {
                if (text.length() >= 4096) return false;
                glyphs.add(new Glyph(text.length(), cp, style));
                text.appendCodePoint(cp);
                return true;
            });
            return new StyledLine(text.toString(), List.copyOf(glyphs));
        }
        boolean clickable(int start, int end) {
            return glyphs.stream().anyMatch(glyph -> glyph.offset() >= start && glyph.offset() < end
                    && glyph.style().getClickEvent() != null);
        }
        FormattedCharSequence slice(int start, int end) {
            return sink -> {
                for (Glyph glyph : glyphs) {
                    if (glyph.offset() >= end) break;
                    if (glyph.offset() >= start && !sink.accept(glyph.offset() - start,
                            glyph.style(), glyph.codePoint())) return false;
                }
                return true;
            };
        }
    }
}
