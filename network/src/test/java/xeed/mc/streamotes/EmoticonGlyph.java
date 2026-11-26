package xeed.mc.streamotes;

import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;

/** Test fixture ONLY: mimics the runtime Streamotes glyph's exact class name. */
public final class EmoticonGlyph implements BakedGlyph {
    @Override public GlyphInfo info() { return null; }
    @Override public TextRenderable.Styled createGlyph(float x, float y,
        int color, int shadowColor, Style style, float bold, float shadow) {
        return null;
    }
}
