package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Draw-time transform of the *original* Streamotes glyph and current animation
 * frame. Reuses the same texture; there are no extra atlas copies, downloads or
 * server packets. It is never applied to vanilla fonts or unknown glyph types.
 */
public final class BttvEffectGlyph implements BakedGlyph {
    private final BakedGlyph source;
    private final BttvModifierSyntax.Modifier modifier;

    private BttvEffectGlyph(BakedGlyph source, BttvModifierSyntax.Modifier modifier) {
        this.source = source;
        this.modifier = modifier;
    }

    public static BakedGlyph wrap(BakedGlyph source, Style style) {
        if (source == null || style == null) return source;
        var effect = BttvModifierSyntax.marked(style.getInsertion());
        if (effect == null || effect == BttvModifierSyntax.Modifier.ZERO_SPACE) return source;
        BttvRenderProbe.marker(); // Did the marker survive the font pipeline?
        if (!source.getClass().getName().equals("xeed.mc.streamotes.EmoticonGlyph"))
            return source;
        BttvRenderProbe.wrapped();
        return new BttvEffectGlyph(source, effect);
    }

    @Override
    public GlyphInfo info() { return source.info(); }

    @Override
    public TextRenderable.Styled createGlyph(float x, float y, int color,
            int shadowColor, Style style, float boldOffset, float shadowOffset) {
        TextRenderable.Styled base = source.createGlyph(x, y, color,
            shadowColor, style, boldOffset, shadowOffset);
        if (base == null) return null;
        return new Transformed(base, modifier);
    }

    private record Transformed(TextRenderable.Styled base,
            BttvModifierSyntax.Modifier modifier) implements TextRenderable.Styled {
        @Override
        public void render(Matrix4fc original, VertexConsumer consumer, int light, boolean noDepth) {
            BttvRenderProbe.drawn();
            if (modifier == BttvModifierSyntax.Modifier.HORIZONTAL
                    || modifier == BttvModifierSyntax.Modifier.VERTICAL) {
                // A negative X/Y scale reverses the quad's winding and is
                // culled on Minecraft's textured chat pipeline. Mirror the
                // live sprite-frame UVs while keeping geometry facing front.
                BttvMirroredVertices mirrored = new BttvMirroredVertices(
                    consumer, modifier == BttvModifierSyntax.Modifier.HORIZONTAL,
                    modifier == BttvModifierSyntax.Modifier.VERTICAL);
                try {
                    base.render(original, mirrored, light, noDepth);
                } finally {
                    mirrored.finish();
                }
                return;
            }

            if (modifier == BttvModifierSyntax.Modifier.WIDE) {
                float left = base.left();
                float centerY = (base.top() + base.bottom()) / 2f;
                Matrix4f widened = new Matrix4f(original);
                widened.translate(left, centerY, 0f)
                    .scale(2f, 1f, 1f)
                    .translate(-left, -centerY, 0f);
                base.render(widened, consumer, light, noDepth);
                return;
            }
            base.render(original, consumer, light, noDepth);
        }

        @Override public RenderType renderType(Font.DisplayMode displayMode) {
            return base.renderType(displayMode);
        }

        @Override public GpuTextureView textureView() {
            return base.textureView();
        }

        @Override public RenderPipeline guiPipeline() {
            return base.guiPipeline();
        }

        @Override public float left() { return base.left(); }
        @Override public float top() { return base.top(); }
        @Override public float right() {
            return modifier == BttvModifierSyntax.Modifier.WIDE
                ? base.left() + (base.right() - base.left()) * 2f
                : base.right();
        }
        @Override public float bottom() { return base.bottom(); }
        @Override public Style style() { return base.style(); }
    }
}
