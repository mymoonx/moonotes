package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.PaintRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Purely visual, local paint in the player list. Never changes network data. */
@Mixin(value = PlayerTabOverlay.class, remap = false)
public abstract class PaintTabMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, require = 0)
    private void moonotes$paintLocalName(PlayerInfo info, CallbackInfoReturnable<Component> result) {
        if (info == null || info.getProfile() == null) return;
        result.setReturnValue(PaintRenderer.decorateTab(result.getReturnValue(), info.getProfile().id()));
    }
}
