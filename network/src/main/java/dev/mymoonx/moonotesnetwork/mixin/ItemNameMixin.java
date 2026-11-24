package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.WorldTextRendering;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Local item display names only. Does not mutate item data or anvil text. */
@Mixin(value = ItemStack.class, remap = false)
public abstract class ItemNameMixin {
    @Inject(method="getHoverName", at=@At("RETURN"), cancellable=true, require=0)
    private void moonotes$itemGlyphs(CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(WorldTextRendering.decorateItem(cir.getReturnValue()));
    }
}
