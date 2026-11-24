package dev.mymoonx.moonotesnetwork;

import java.util.List;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** The renderer must not change source text, links, cursor spans or wrapped-word boundaries. */
public final class BookEmoteLayoutCheck {
    public static void main(String[] args) {
        String id = "01F5Z4B9700008KDXA2TJQBNGW";
        ChatPalette.publish(List.of(
                new ChatPalette.Emote(id + ":42", id, "42", "test"),
                new ChatPalette.Emote(id + ":67", id, "67", "test")));
        String page = "Привет 42\n67 конец";
        require(BookEmoteRenderer.editableMatches(page, 0, 9).size() == 1, "full token before caret");
        require(BookEmoteRenderer.editableMatches(page, 0, 8).isEmpty(), "partial prefix stays text");
        require(BookEmoteRenderer.editableMatches(page, 8, 9).isEmpty(), "partial suffix stays text");
        require(BookEmoteRenderer.editableMatches("4242", 0, 2).isEmpty(), "no alias from part of word");
        require(BookEmoteRenderer.editableMatches(":42:", 0, 4).size() == 1, "colon token retains source span");
        require(BookEmoteRenderer.editableMatches(":42:", 1, 3).isEmpty(), "colon editing is not a second alias");

        var flow = new BookEmoteRenderer.PageContext("Вступление\n\n42  67 конец");
        require(flow.matches("Вступление").isEmpty(), "normal first line");
        require(flow.matches("").isEmpty(), "blank line");
        var first = flow.matches("42");
        require(first.size() == 1 && first.getFirst().start() == 0 && first.getFirst().end() == 2,
                "wrapped token maps to local offsets");
        require(flow.matches("67 конец").size() == 1, "whitespace at wrapping is retained logically");

        var broken = new BookEmoteRenderer.PageContext("VeryLongWord42");
        require(broken.matches("VeryLongWord").isEmpty(), "word prefix not an alias");
        require(broken.matches("42").isEmpty(), "wrapped word suffix not an alias");
        var bidi = new BookEmoteRenderer.PageContext("אבג 42");
        require(bidi.matches("42 גבא").isEmpty(), "transformed text retains vanilla rendering");

        Style red = Style.EMPTY.withColor(0xFF3333);
        Style linked = Style.EMPTY.withClickEvent(new ClickEvent.ChangePage(2));
        FormattedCharSequence source = FormattedCharSequence.composite(List.of(
                FormattedCharSequence.forward("😀 Привет ", red),
                FormattedCharSequence.forward("42", linked)));
        var styled = BookEmoteRenderer.StyledLine.read(source);
        require(styled.text().equals("😀 Привет 42"), "surrogate pairs and Cyrillic preserved");
        require(styled.clickable(10, 12), "server book link preserved");
        StringBuilder restored = new StringBuilder();
        styled.slice(0, styled.text().length()).accept((i, style, cp) -> {
            restored.appendCodePoint(cp);
            require(i < 10 ? style.equals(red) : style.equals(linked), "style preserved per code point");
            return true;
        });
        require(restored.toString().equals(styled.text()), "display slicing leaves source text unchanged");
        ChatPalette.publish(List.of());
        System.out.println("BookEmoteLayoutCheck: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
