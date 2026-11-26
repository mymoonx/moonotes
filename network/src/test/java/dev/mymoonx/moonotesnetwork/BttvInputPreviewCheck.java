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
            test(visible(result).contains("WW"), "real glyph text remains "+name);
            if (!name.equals("z!"))
                test(marked(result,"WW",name.charAt(0)), "correct marker "+name);
        }
        test(BttvInputPreview.format("Hello WW",0) == null,
            "native input fallback for chat without modifier");
        test(BttvInputPreview.format("w! missing",0) == null,
            "no side effects for unregistered alias");
        test(BttvInputPreview.format("/tp h! WW",0) == null,
            "never apply effects to command syntax");
        test(BttvInputPreview.format("/msg Steve h! WW",0) == null,
            "private commands are a separate milestone");
        test(BttvInputPreview.format("h! !vanish",0) != null,
            "registered punctuation alias remains supported");
        System.out.println("BttvInputPreviewCheck: " + checks + " checks passed");
    }
}
