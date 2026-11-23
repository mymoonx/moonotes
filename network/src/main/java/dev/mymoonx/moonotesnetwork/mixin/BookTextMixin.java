package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.WorldTextRendering;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Visual-only formatting for pages in books and lecterns. */
@Mixin(value = BookViewScreen.BookAccess.class, remap = false)
public abstract class BookTextMixin {
    @Inject(method="getPage", at=@At("RETURN"), cancellable=true, require=0)
    private void moonotes$bookGlyphs(int page, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(WorldTextRendering.decorateBook(cir.getReturnValue()));
    }
}
