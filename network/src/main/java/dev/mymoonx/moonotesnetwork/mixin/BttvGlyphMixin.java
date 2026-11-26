package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.BttvEffectGlyph;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Target the exact overloaded accept() call in Minecraft 26.3, AFTER
 * Streamotes' glyph substitution has completed. The earlier @At("STORE")
 * hook could be applied before or miss the substituted glyph entirely.
 *
 * This is draw-only: the submitted chat packet and stored Component are
 * untouched. If an installed Streamotes changes its renderer, fail closed.
 */
@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder",
    remap = false, priority = 700)
public abstract class BttvGlyphMixin {
    @ModifyArg(
        method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font$PreparedTextBuilder;accept(ILnet/minecraft/network/chat/Style;Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z"),
        index = 2, require = 0)
    private BakedGlyph moonotes$transformBttv(
            BakedGlyph glyph, int index, Style style, int codePoint) {
        return BttvEffectGlyph.wrap(glyph, style);
    }
}
