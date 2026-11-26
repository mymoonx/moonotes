package dev.mymoonx.moonotesnetwork;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

/** Regression for real BTTV prefix syntax and render-only message styling. */
public final class BttvModifierSyntaxCheck {
    private static final Set<String> EMOTES = Set.of("catJAM", "WW", "TopHat", "!vanish");
    private static int checks;
    private static void yes(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
    private static String plain(FormattedText formatted) {
        var result = new StringBuilder();
        formatted.visit((style, text) -> {
            result.append(text);
            return Optional.empty();
        }, Style.EMPTY);
        return result.toString();
    }
    private static boolean effect(FormattedText text, String emote, char modifier) {
        final boolean[] found = {false};
        text.visit((style, part) -> {
            if (part.equals(emote)
                    && style.getClickEvent() instanceof ClickEvent.CopyToClipboard event
                    && event.value().equals(emote)
                    && BttvModifierSyntax.marked(style.getInsertion()) != null
                    && BttvModifierSyntax.marked(style.getInsertion()).code() == modifier)
                found[0] = true;
            return Optional.empty();
        }, Style.EMPTY);
        return found[0];
    }

    public static void main(String[] args) {
        for (char c : new char[] {'w', 'h', 'v', 'z'}) {
            String input = c + "! catJAM";
            var matches = BttvModifierSyntax.find(input, EMOTES::contains);
            yes(matches.size() == 1, input + " recognized");
            yes(matches.getFirst().emote().equals("catJAM"), "next emote only");
            yes(matches.getFirst().modifier().code() == c, "modifier identity");
        }
        yes(BttvModifierSyntax.find("w!fake", EMOTES::contains).isEmpty(),
            "modifier requires a separating space");
        yes(BttvModifierSyntax.find("w! notRegistered", EMOTES::contains).isEmpty(),
            "never hide modifier for unknown alias");
        yes(BttvModifierSyntax.find("xw! catJAM", EMOTES::contains).isEmpty(),
            "modifier must be a separate word");
        yes(BttvModifierSyntax.find("w! (catJAM)", EMOTES::contains).isEmpty(),
            "punctuation adjacency stays literal");
        yes(BttvModifierSyntax.find("w! catJAM", s -> false).isEmpty(),
            "no registered target means no effect");
        yes(BttvModifierSyntax.find("w! catJAM".repeat(1500), EMOTES::contains)
            .isEmpty(), "large chat text ignored");
        yes(BttvModifierSyntax.find("h! catJAM v! WW", EMOTES::contains).size() == 2,
            "multiple modifiers allowed independently");
        yes(BttvModifierSyntax.find("h! !vanish", EMOTES::contains).size() == 1,
            "real emotes beginning with ! work");
        yes(BttvModifierSyntax.marked("moonotes:bttv:h")
            == BttvModifierSyntax.Modifier.HORIZONTAL, "render marker round-trip");
        yes(BttvModifierSyntax.marked("untrusted") == null, "unknown markers ignored");

        var untouched = Component.literal("hello w! missing");
        yes(BttvChatText.decorate(untouched, EMOTES::contains) == untouched,
            "ordinary chat is untouched");
        var horizontal = BttvChatText.decorate(
            Component.literal("hello h! catJAM"), EMOTES::contains);
        yes(plain(horizontal).equals("hello catJAM"), "h! hidden only visually");
        yes(effect(horizontal, "catJAM", 'h'), "emote gets horizontal transform marker");
        var vertical = BttvChatText.decorate(
            Component.literal("v! WW"), EMOTES::contains);
        yes(effect(vertical, "WW", 'v'), "v! gets vertical flip marker");
        var wide = BttvChatText.decorate(
            Component.literal("w! WW"), EMOTES::contains);
        yes(effect(wide, "WW", 'w'), "w! gets width transform marker");
        yes(plain(wide).startsWith("WW") && plain(wide).length() > 2,
            "w! reserves extra visual space");
        var zero = BttvChatText.decorate(
            Component.literal("catJAM z! TopHat"), EMOTES::contains);
        yes(plain(zero).equals("catJAMTopHat"),
            "z! removes the immediately preceding visual separator");

        var coloured = Component.literal("h!").withStyle(Style.EMPTY.withColor(0xF03456))
            .append(Component.literal(" ").withStyle(Style.EMPTY.withColor(0x444444)))
            .append(Component.literal("catJAM").withStyle(Style.EMPTY.withColor(0xABCDEF)));
        var styled = BttvChatText.decorate(coloured, EMOTES::contains);
        yes(plain(styled).equals("catJAM"), "server-coloured fragments combined");
        yes(effect(styled, "catJAM", 'h'), "color fragments keep effect");
        var clickable = Component.literal("h! catJAM").withStyle(
            Style.EMPTY.withClickEvent(new ClickEvent.CopyToClipboard("server")));
        yes(BttvChatText.decorate(clickable, EMOTES::contains) == clickable,
            "preexisting clickable server chat stays untouched");
        var text = "Use w! catJAM";
        BttvChatText.decorate(Component.literal(text), EMOTES::contains);
        yes(text.equals("Use w! catJAM"), "no sent message mutation");
        System.out.println("BttvModifierSyntaxCheck: " + checks + " checks passed");
    }
}
