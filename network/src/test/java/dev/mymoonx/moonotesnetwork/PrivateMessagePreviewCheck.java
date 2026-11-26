package dev.mymoonx.moonotesnetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Regressions for the /msg nickname + message-glyph rendering boundary. */
public final class PrivateMessagePreviewCheck {
    private static final Set<String> KNOWN = Set.of("му", "catJAM", "!vanish");
    private static int assertions;

    private static void check(boolean value, String description) {
        assertions++;
        if (!value) throw new AssertionError(description);
    }

    private static List<PrivateMessagePreviewTokens.Span> find(String draft) {
        return PrivateMessagePreviewTokens.find(draft, draft, 0,
            PrivateMessageSyntax.bodyStart(draft), KNOWN::contains);
    }

    private static void word(String draft, String expected) {
        var tokens = find(draft);
        check(tokens.size() == 1, "one message emote: " + draft);
        var token = tokens.getFirst();
        check(token.name().equals(expected), "right emote name: " + draft);
        check(draft.substring(token.start(), token.end()).contains(expected),
            "matches message text, not recipient: " + draft);
        check(token.start() >= PrivateMessageSyntax.bodyStart(draft),
            "name and command stay outside emote span: " + draft);
    }

    private static String emoteAt(FormattedCharSequence sequence, int target) {
        final String[] value = {null};
        sequence.accept((index, style, codepoint) -> {
            if (index == target && style.getClickEvent() instanceof ClickEvent.CopyToClipboard click)
                value[0] = click.value();
            return true;
        });
        return value[0];
    }

    public static void main(String[] args) {
        word("/msg ник му", "му");
        word("/tell Steve catJAM", "catJAM");
        word("/w Alex му", "му");
        word("/pm Alex му", "му");
        word("/whisper Alex му", "му");
        word("/r му", "му");
        word("/reply му", "му");
        word("/msg Alex :му:", "му");
        word("/msg Alex !vanish", "!vanish");

        check(find("/msg му").isEmpty(), "recipient must never appear as emote");
        check(find("/msg му ").isEmpty(), "recipient stays plain with an empty message");
        check(find("/msg Alex").isEmpty(), "unfinished command stays plain");
        check(find("/tp Alex му").isEmpty(), "other commands untouched");
        check(find("/say му").isEmpty(), "arbitrary command untouched");
        check(find("/msg Alex (му)").isEmpty(), "punctuation adjacency stays literal");
        check(find("/msg @a му").isEmpty(), "recipient selectors are excluded");

        String draft = "/msg му му";
        var tokens = find(draft);
        int body = PrivateMessageSyntax.bodyStart(draft);
        check(tokens.size() == 1 && tokens.getFirst().start() == body,
            "same emote as recipient highlights only body");
        // The other formatter may already have mistaken the nickname for a
        // glyph, or applied Brigadier command syntax colours. The last-stage
        // renderer must undo just the recipient marker and preserve the colour.
        var baseline = FormattedCharSequence.forward(draft,
            Style.EMPTY.withColor(0xABCDEF)
                .withClickEvent(new ClickEvent.CopyToClipboard("му")));
        var corrected = PrivateMessagePreview.apply(baseline, draft, 0, body, tokens);
        check(emoteAt(corrected, draft.indexOf("му")) == null,
            "recipient marker is stripped");
        check("му".equals(emoteAt(corrected, draft.lastIndexOf("му"))),
            "message glyph survives syntax formatter");
        final boolean[] sameColour = {false};
        corrected.accept((index, style, cp) -> {
            if (index == body)
                sameColour[0] = style.getColor() != null
                    && style.getColor().getValue() == 0xABCDEF;
            return true;
        });
        check(sameColour[0], "command syntax colour is preserved");
        check(draft.equals("/msg му му"), "original command text stays unchanged");

        String longDraft = "/tell Steve hello му";
        int visibleOffset = longDraft.indexOf("hello");
        String visible = longDraft.substring(visibleOffset);
        var scrolled = PrivateMessagePreviewTokens.find(
            longDraft, visible, visibleOffset,
            PrivateMessageSyntax.bodyStart(longDraft), KNOWN::contains);
        check(scrolled.size() == 1
            && scrolled.getFirst().start() == visible.indexOf("му"),
            "horizontal scrolling uses visible coordinates");

        var invalidOffset = PrivateMessagePreviewTokens.find(
            longDraft, visible, 0,
            PrivateMessageSyntax.bodyStart(longDraft), KNOWN::contains);
        check(invalidOffset.isEmpty(), "bad formatter offset never mis-styles commands");

        var rules = new ChatCommandRules(null);
        check(rules.put("partymsg", ChatCommandRules.Mode.RECIPIENT),
            "custom recipient command enabled");
        check(PrivateMessagePreviewTokens.find("/partymsg Alex му", "/partymsg Alex му", 0,
            PrivateMessageSyntax.bodyStart("/partymsg Alex му", rules), KNOWN::contains).size() == 1,
            "custom allowlisted command supports preview");
        check(PrivateMessagePreviewTokens.find("му ".repeat(130), "му ".repeat(130), 0,
            0, KNOWN::contains).isEmpty(), "no unbounded render scanning");

        System.out.println("PrivateMessagePreviewCheck: " + assertions + " checks passed");
    }
}
