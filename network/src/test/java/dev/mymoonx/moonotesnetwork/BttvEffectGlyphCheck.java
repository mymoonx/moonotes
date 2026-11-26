package dev.mymoonx.moonotesnetwork;

import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import xeed.mc.streamotes.EmoticonGlyph;

/** Tests marker compatibility at the exact Streamotes BakedGlyph boundary. */
public final class BttvEffectGlyphCheck {
    private static void verify(boolean ok, String detail) {
        if (!ok) throw new AssertionError(detail);
    }

    public static void main(String[] args) {
        BakedGlyph source = new EmoticonGlyph();
        for (char effect : new char[]{'w','h','v'}) {
            BakedGlyph wrapped = BttvEffectGlyph.wrap(source,
                Style.EMPTY.withInsertion("moonotes:bttv:" + effect));
            verify(wrapped instanceof BttvEffectGlyph, "expected transform: " + effect);
            verify(wrapped.info() == source.info(), "original glyph metrics preserved");
        }
        verify(BttvEffectGlyph.wrap(source, Style.EMPTY) == source,
            "normal emote must not be modified");
        verify(BttvEffectGlyph.wrap(source,
            Style.EMPTY.withInsertion("moonotes:bttv:z")) == source,
            "zero-space has no texture transform");
        BakedGlyph unrelated = new BakedGlyph() {
            @Override public com.mojang.blaze3d.font.GlyphInfo info() { return null; }
            @Override public net.minecraft.client.gui.font.TextRenderable.Styled createGlyph(
                float x,float y,int color,int shadowColor,Style style,float bold,float shadow) {
                return null;
            }
        };
        verify(BttvEffectGlyph.wrap(unrelated,
            Style.EMPTY.withInsertion("moonotes:bttv:w")) == unrelated,
            "vanilla glyphs must not be transformed");
        verify(BttvRenderProbe.report().contains("BTTV input/chat/marker/wrap/draw:"),
            "local-only diagnostics report is available");
        System.out.println("BttvEffectGlyphCheck: PASS");
    }
}
