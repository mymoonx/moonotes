package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.BttvEffectGlyph;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Runs after Streamotes' own glyph substitution (default Mixin priority 1000).
 * Transforms only glyphs that the actual installed Streamotes registered.
 */
@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder",
    remap = false, priority = 800)
public abstract class BttvGlyphMixin {
    @ModifyVariable(
        method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
        at = @At("STORE"), ordinal = 0, require = 0)
    private BakedGlyph moonotes$transformBttv(BakedGlyph glyph,
            int index, Style style, int codepoint) {
        return BttvEffectGlyph.wrap(glyph, style);
    }
}
