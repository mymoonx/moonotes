package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.WorldTextRendering;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignText;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Modifies prepared sign glyphs only; never changes sign contents or packets. */
@Mixin(value = SignText.class, remap = false)
public abstract class SignTextMixin {
    @Inject(method="getRenderMessages",at=@At("RETURN"),cancellable=true,require=0)
    private void moonotes$signGlyphs(boolean filtered,
            Function<Component, FormattedCharSequence> prepare,
            CallbackInfoReturnable<FormattedCharSequence[]> cir) {
        WorldTextRendering.decorateSign((SignText)(Object)this, filtered, prepare, cir);
    }
}
