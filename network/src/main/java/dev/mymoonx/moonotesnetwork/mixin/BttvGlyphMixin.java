package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.BttvEffectGlyph;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Modifies the final glyph argument AFTER Streamotes substitutes its baked
 * glyph. The target overload takes (int, Style, BakedGlyph).
 *
 * The alpha.3 @ModifyArg handler incorrectly appended the outer method's
 * (int, Style, int) parameters. Mixin rejected that signature and crashed
 * when Font$PreparedTextBuilder loaded on Minecraft's title screen.
 *
 * @ModifyArgs accesses BOTH the actual invocation Style and BakedGlyph,
 * so there is no extra handler signature and no change to vanilla text.
 */
@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder",
    remap = false, priority = 700)
public abstract class BttvGlyphMixin {
    @ModifyArgs(
        method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font$PreparedTextBuilder;accept(ILnet/minecraft/network/chat/Style;Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z"),
        require = 0)
    private void moonotes$transformBttv(Args args) {
        Style style = args.get(1);
        BakedGlyph original = args.get(2);
        BakedGlyph decorated = BttvEffectGlyph.wrap(original, style);
        if (decorated != original) args.set(2, decorated);
    }
}
