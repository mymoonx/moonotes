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
    private static final Set<String> EMOTES = Set.of("catJAM", "WW", "TopHat", "!vanish", "q", "м", "v", "w!", "h!", "v!", "z!");
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
        for (String alias : new String[] {"q", "v", "м"}) {
            String draft = "w! " + alias;
            var parsed = BttvModifierSyntax.find(draft, EMOTES::contains);
            yes(parsed.size() == 1 && parsed.getFirst().emote().equals(alias),
                "registered single-character emote must work: " + draft);
        }
        yes(BttvModifierSyntax.find("w! x", EMOTES::contains).isEmpty(),
            "an unregistered one-letter word is not an emote");
        var oneLetter = BttvChatText.decorate(
            Component.literal("w! q"), EMOTES::contains);
        yes(plain(oneLetter).startsWith("q") && effect(oneLetter, "q", 'w'),
            "shortest chat message w! q becomes a wide emote");
        var russian = BttvChatText.decorate(
            Component.literal("v! м"), EMOTES::contains);
        yes(plain(russian).equals("м") && effect(russian, "м", 'v'),
            "single Cyrillic character remains a valid emote target");

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
        var globalAttached = BttvModifierSyntax.find("!w! q", EMOTES::contains);
        yes(globalAttached.size() == 1 && globalAttached.getFirst().start() == 1
            && globalAttached.getFirst().emoteStart() == 4
            && globalAttached.getFirst().emote().equals("q"),
            "!w! q keeps initial ! as server-owned global routing");
        yes(BttvModifierSyntax.find("! h! q", EMOTES::contains).size() == 1,
            "! h! q is still a valid spaced global message");
        yes(BttvModifierSyntax.find("before !w! q", EMOTES::contains).size() == 1,
            "Paper decorated chat may put global message inside one line");
        yes(BttvModifierSyntax.find("not!w! q", EMOTES::contains).isEmpty(),
            "no recognition inside an ordinary word");
        yes(BttvModifierSyntax.find("!!w! q", EMOTES::contains).isEmpty(),
            "do not swallow unrelated exclamation marks");
        yes(GlobalChatPrefix.matches("!w! q", "!", EMOTES::contains).isEmpty(),
            "global emote fallback must not steal !w! before BTTV");
        yes(GlobalChatPrefix.matches("!WW", "!", EMOTES::contains).size() == 1,
            "existing !WW global emote support remains unchanged");
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
        var clickable = Component.literal("prefix h! catJAM suffix").withStyle(
            Style.EMPTY.withClickEvent(new ClickEvent.CopyToClipboard("server")));
        var decoratedClickable = BttvChatText.decorate(clickable, EMOTES::contains);
        yes(plain(decoratedClickable).equals("prefix catJAM suffix"),
            "Paper clickable text is no longer rejected completely");
        yes(effect(decoratedClickable, "catJAM", 'h'),
            "emote inside Paper click-styled text gets BTTV marker");
        final boolean[] originalClickable = {false};
        decoratedClickable.visit((style, part) -> {
            if (part.contains("prefix") && style.getClickEvent()
                    instanceof ClickEvent.CopyToClipboard event
                    && event.value().equals("server")) originalClickable[0] = true;
            return Optional.empty();
        }, Style.EMPTY);
        yes(originalClickable[0], "server's original click action survives outside the emote");

        var paper = Component.literal("[Global] ").withStyle(
                Style.EMPTY.withColor(0x0088AA))
            .append(Component.literal("nick: ").withStyle(
                Style.EMPTY.withClickEvent(new ClickEvent.CopyToClipboard("nick-action"))))
            .append(Component.literal("!").withStyle(Style.EMPTY.withColor(0x555555)))
            .append(Component.literal("w!").withStyle(Style.EMPTY.withColor(0x666666)
                .withClickEvent(new ClickEvent.CopyToClipboard("chat-action"))))
            .append(Component.literal(" ").withStyle(Style.EMPTY.withColor(0x888888)))
            .append(Component.literal("q").withStyle(Style.EMPTY.withColor(0xFFFFFF)
                .withInsertion("paper-insert")));
        var globalFirst = GlobalChatRichText.decorate(paper, "!", EMOTES::contains);
        yes(globalFirst == paper,
            "!w! is not stolen as regular !<emote> by global fallback");
        var paperEffect = BttvChatText.decorate(globalFirst, EMOTES::contains);
        yes(plain(paperEffect).startsWith("[Global] nick: !q"),
            "Paper split-styled global chat renders modified emote and keeps global !");
        yes(effect(paperEffect, "q", 'w'),
            "Paper emote with insertion and styled segments gets proper marker");
        final boolean[] nickClick = {false};
        paperEffect.visit((style, part) -> {
            if (part.equals("nick: ") && style.getClickEvent()
                    instanceof ClickEvent.CopyToClipboard action
                    && action.value().equals("nick-action")) nickClick[0] = true;
            return Optional.empty();
        }, Style.EMPTY);
        yes(nickClick[0], "server nickname click action remains intact");

        var visibleGlobal = BttvChatText.decorate(Component.literal("!w! q"), EMOTES::contains);
        yes(plain(visibleGlobal).startsWith("!q") && effect(visibleGlobal,"q",'w'),
            "simple !w! q leaves server global ! visible but renders wide q");
        var spacedGlobal = BttvChatText.decorate(Component.literal("! w! q"), EMOTES::contains);
        yes(plain(spacedGlobal).startsWith("! q") && effect(spacedGlobal,"q",'w'),
            "spaced ! w! q remains compatible");

        var text = "Use w! catJAM";
        BttvChatText.decorate(Component.literal(text), EMOTES::contains);
        yes(text.equals("Use w! catJAM"), "no sent message mutation");
        System.out.println("BttvModifierSyntaxCheck: " + checks + " checks passed");
    }
}
