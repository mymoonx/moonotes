package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.StreamotesBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reapply local metadata after the original mod clears its registry. */
@Mixin(targets = "xeed.mc.streamotes.emoticon.EmoticonRegistry", remap = false)
public abstract class StreamotesRegistryMixin {
    @Inject(method = "reloadEmoticons()V", at = @At("TAIL"), remap = false)
    private static void moonotes$afterRegistryClear(CallbackInfo ci) {
        StreamotesBridge.registryCleared();
    }
}
