package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.StreamotesNotificationBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional compatibility: only automatic notices from the separate Streamotes mod. */
@Mixin(targets = "xeed.mc.streamotes.Streamotes", remap = false)
public abstract class StreamotesNotificationsMixin {
    // These sites run only after Streamotes has checked its own reporting setting.
    // ReportOption.None never reaches either hook, and disabling coordination leaves each call intact.
    @Inject(method = "msg(Ljava/lang/String;)V", at = @At(value = "INVOKE",
        target = "Lxeed/mc/streamotes/Compat;sendToastMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;)V"),
        cancellable = true, require = 0, remap = false)
    private static void moonotes$queueAutomaticToast(String message, CallbackInfo ci) {
        if (StreamotesNotificationBridge.intercept(message, "Toast")) ci.cancel();
    }

    @Inject(method = "msg(Ljava/lang/String;)V", at = @At(value = "INVOKE",
        target = "Lxeed/mc/streamotes/Compat;sendClientMessage(Lnet/minecraft/network/chat/Component;)V"),
        cancellable = true, require = 0, remap = false)
    private static void moonotes$queueAutomaticChat(String message, CallbackInfo ci) {
        if (StreamotesNotificationBridge.intercept(message, "Chat")) ci.cancel();
    }
}
