package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.StreamotesBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only covered 7TV channel metadata is supplied locally; other providers keep their normal loaders. */
@Mixin(targets = "xeed.mc.streamotes.addon.pack.X7tvChannelPack", remap = false)
public abstract class StreamotesLocalChannelMixin {
    @Inject(method = "loadMetadata(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void moonotes$useSavedChannel(String channel, CallbackInfo ci) {
        if (StreamotesBridge.useLocalChannel(channel)) ci.cancel();
    }
}
