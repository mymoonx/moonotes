package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.PaintSettings;
import dev.mymoonx.moonotesnetwork.PaintStyle;
import dev.mymoonx.moonotesnetwork.TwitchLink;
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
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || info == null || info.getProfile() == null
            || !minecraft.player.getUUID().equals(info.getProfile().getId())) return;
        if (!TwitchLink.local().linkedFor(minecraft.player.getUUID().toString())) return;
        PaintSettings settings = PaintSettings.local();
        PaintStyle style = settings.paint();
        if (!settings.tab() || style == null || result.getReturnValue() == null) return;
        String visibleName = result.getReturnValue().getString();
        if (visibleName.isEmpty() || visibleName.length() > 128) return;
        result.setReturnValue(style.colored(visibleName, settings.animation(), System.currentTimeMillis()));
    }
}
