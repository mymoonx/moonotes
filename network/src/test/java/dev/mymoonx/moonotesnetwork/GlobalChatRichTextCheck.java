package dev.mymoonx.moonotesnetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

/**
 * Tests actual formatted chat content, not only a single plain-text regex:
 * server plugins often style global prefix and message in separate siblings.
 */
public final class GlobalChatRichTextCheck {
    private static int passed;
    private record Chunk(String value, Style style) { }
    private static void check(boolean ok, String reason) {
        passed++;
        if (!ok) throw new AssertionError(reason);
    }
    private static List<Chunk> chunks(FormattedText text) {
        var out = new ArrayList<Chunk>();
        text.visit((style, value) -> {
            out.add(new Chunk(value, style));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return out;
    }
    private static String text(FormattedText value) {
        StringBuilder result = new StringBuilder();
        for (Chunk c : chunks(value)) result.append(c.value());
        return result.toString();
    }
    private static boolean marked(FormattedText text, String name) {
        return chunks(text).stream().anyMatch(c -> c.value().equals(name)
            && c.style().getClickEvent() instanceof ClickEvent.CopyToClipboard evt
            && evt.value().equals(name));
    }
    public static void main(String[] args) {
        Map<String, Boolean> names = Map.of("WW", true, "42", true,
            "!vanish", true, "vanish", true, "!setgame", true, "setgame", true);
        var split = Component.literal("[G] ").withStyle(Style.EMPTY.withColor(0x60A0CF))
            .append(Component.literal("!").withStyle(Style.EMPTY.withColor(0xFFFFFF)))
            .append(Component.literal("WW").withStyle(Style.EMPTY.withColor(0xABCDEF)));
        FormattedText decorated = GlobalChatRichText.decorate(split, "!", names::containsKey);
        check(text(decorated).equals("[G] !WW"), "no network or visible text changes");
        check(marked(decorated, "WW"), "WW becomes Streamotes glyph when ! and WW have different styles");
        check(chunks(decorated).stream().anyMatch(c -> c.value().equals("!")
            && c.style().getClickEvent() == null), "the global prefix stays ordinary punctuation");

        var regular = Component.literal("<Steve> !WW");
        var normal = GlobalChatRichText.decorate(regular, "!", names::containsKey);
        check(text(normal).equals("<Steve> !WW") && marked(normal, "WW"), "simple chat emote");

        var literal = Component.literal("!vanish !setgame");
        check(GlobalChatRichText.decorate(literal, "!", names::containsKey) == literal,
            "exact !emotes win and are handled normally by Streamotes");
        check(text(GlobalChatRichText.decorate(Component.literal("! WW"), "!", names::containsKey))
            .equals("! WW"), "space-separated form is unchanged");

        var link = Component.literal("!WW").withStyle(
            Style.EMPTY.withClickEvent(new ClickEvent.CopyToClipboard("original")));
        check(GlobalChatRichText.decorate(link, "!", names::containsKey) == link,
            "clickable server text must not be replaced");
        var special = Component.literal("!:42:");
        check(GlobalChatRichText.decorate(special, "!", names::containsKey) == special,
            "colon wrapped global names remain native without a new parsing rule");
        var limit = Component.literal("!WW".repeat(1500));
        check(GlobalChatRichText.decorate(limit, "!", names::containsKey) == limit,
            "oversized inputs are bypassed");
        check(GlobalChatRichText.decorate(regular, "#", names::containsKey) == regular,
            "other server's global prefix is not modified");
        System.out.println("GlobalChatRichTextCheck: " + passed + " checks passed");
    }
}
