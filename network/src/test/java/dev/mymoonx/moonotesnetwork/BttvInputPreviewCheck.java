package dev.mymoonx.moonotesnetwork;

import java.util.ArrayList;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Smoke checks for the actual chat EditBox formatted character stream. */
public final class BttvInputPreviewCheck {
    private static int checks;
    private static void test(boolean yes, String reason) {
        checks++;
        if (!yes) throw new AssertionError(reason);
    }
    private static String visible(FormattedCharSequence glyphs) {
        var content = new StringBuilder();
        glyphs.accept((index, style, codepoint) -> {
            content.appendCodePoint(codepoint);
            return true;
        });
        return content.toString();
    }
    private static boolean marked(FormattedCharSequence glyphs,
            String emote, char effect) {
        var matched = new StringBuilder();
        final boolean[] found = {false};
        glyphs.accept((index, style, codepoint) -> {
            if (style.getClickEvent() instanceof ClickEvent.CopyToClipboard clip
                    && clip.value().equals(emote)
                    && BttvModifierSyntax.marked(style.getInsertion()) != null
                    && BttvModifierSyntax.marked(style.getInsertion()).code() == effect) {
                matched.appendCodePoint(codepoint);
                found[0] = true;
            }
            return true;
        });
        return found[0] && matched.toString().equals(emote);
    }
    public static void main(String[] args) {
        for (String name : new String[] {"w!", "h!", "v!", "z!"}) {
            FormattedCharSequence result = BttvInputPreview.format(name + " WW", 0);
            test(result != null, "preview handles "+name);
            test(visible(result).equals(name + " WW"),
                "input positions stay aligned with the unmodified chat buffer: " + name);
            if (!name.equals("z!"))
                test(marked(result,"WW",name.charAt(0)), "correct marker "+name);
        }
        FormattedCharSequence zero = BttvInputPreview.format("WW z! WW", 0);
        test(zero != null && visible(zero).equals("WW z! WW"),
            "z! preserves all characters while editing");
        final int[] markedZero = {0};
        zero.accept((index, style, codepoint) -> {
            if (style.getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.CopyToClipboard
                    && style.getInsertion() == null) markedZero[0]++;
            return true;
        });
        test(markedZero[0] >= 4, "z! styles target as emote without a geometric flip");
        test(BttvInputPreview.format("Hello WW",0) == null,
            "native input fallback for chat without modifier");
        FormattedCharSequence pending = BttvInputPreview.format("w! missing", 0);
        test(pending != null && visible(pending).equals("w! missing"),
            "pending modifier stays visible as literal text without a fetch");
        test(!marked(pending, "missing", 'w'),
            "unregistered alias is not transformed");
        FormattedCharSequence partial = BttvInputPreview.format("w!", 0);
        test(partial != null && visible(partial).equals("w!"),
            "modifier is recognized before an emote is typed");

        for (String alias : new String[]{"q", "v", "м"}) {
            String draft = "h! " + alias;
            FormattedCharSequence shortAlias = BttvInputPreview.formatVisible(
                draft, draft, 0, word -> word.equals(alias));
            test(shortAlias != null && visible(shortAlias).equals(draft),
                "one-character input preview keeps original text: " + draft);
            test(marked(shortAlias, alias, 'h'),
                "one-character glyph preview retains effect: " + draft);
        }
        FormattedCharSequence globalAttached = BttvInputPreview.formatVisible(
            "!w! q", "!w! q", 0, word -> word.equals("q") || word.equals("w!"));
        test(globalAttached != null && visible(globalAttached).equals("!w! q"),
            "global message !w! q preserves the entire real server-bound draft");
        test(marked(globalAttached, "q", 'w'),
            "attached global ! never steals the wide effect from q");
        final int[] fakeOperator = {0};
        globalAttached.accept((index, style, codepoint) -> {
            if (codepoint == 'w' && style.getClickEvent() instanceof ClickEvent.CopyToClipboard)
                fakeOperator[0]++;
            return true;
        });
        test(fakeOperator[0] == 0,
            "the operator w! is not styled as a standalone downloaded image");
        FormattedCharSequence globalSpaced = BttvInputPreview.formatVisible(
            "! w! q", "! w! q", 0, word -> word.equals("q"));
        test(globalSpaced != null && visible(globalSpaced).equals("! w! q"),
            "global message with an explicit gap remains editable");
        test(marked(globalSpaced, "q", 'w'),
            "spaced global operator applies correctly");
        FormattedCharSequence pendingGlobal = BttvInputPreview.formatVisible(
            "!w!", "!w!", 0, word -> word.equals("w!"));
        test(pendingGlobal != null && visible(pendingGlobal).equals("!w!"),
            "incomplete !w! stays plain server text rather than a pseudo-emote");
        test(BttvInputPreview.formatVisible("!w! q", "q", 4,
            word -> word.equals("q")) != null,
            "global prefix remains parseable when scrolled out of the visible EditBox");
        test(GlobalChatPrefix.suggestion("!w!", "!", word -> word.equals("w!")).isEmpty(),
            "server prefix + BTTV operator never offers pseudo-emote completion");
        test(BttvInputPreview.format("/tp h! WW",0) == null,
            "never apply effects to command syntax");
        test(BttvInputPreview.format("/msg Steve h! WW",0) == null,
            "private commands are a separate milestone");
        test(BttvInputPreview.format("h! !vanish",0) != null,
            "registered punctuation alias remains supported");

        // The entire input text must remain visible and editable, including
        // modifier syntax. The font draws only the registered name as a glyph.
        String original = "hello w! WW and WW";
        FormattedCharSequence complete = BttvInputPreview.format(original, 0);
        test(complete != null && visible(complete).equals(original),
            "text is not removed, replaced or padded while editing");
        test(marked(complete, "WW", 'w'), "target glyph keeps wide marker");

        String scrolled = "prefix h! WW";
        FormattedCharSequence afterScroll = BttvInputPreview.formatVisible(
            scrolled, scrolled.substring(7), 7);
        test(afterScroll != null && visible(afterScroll).equals(scrolled.substring(7)),
            "scrolling retains original character positions");
        test(marked(afterScroll, "WW", 'h'),
            "scrolling preserves the effect on the target emote");

        test(BttvInputPreview.formatVisible(
            "prefix w! WW", "WW", 10) != null,
            "effect survives when its modifier is outside the visible range");
        test(BttvInputPreview.formatVisible(
            "w! WW", "WW", 0) == null,
            "incorrect viewport offset cannot move the edit cursor");
        test(BttvInputPreview.formatVisible(
            "/msg Steve w! WW", "/msg Steve w! WW", 0) == null,
            "private messages remain on their existing preview renderer");
        System.out.println("BttvInputPreviewCheck: " + checks + " checks passed");
    }
}
