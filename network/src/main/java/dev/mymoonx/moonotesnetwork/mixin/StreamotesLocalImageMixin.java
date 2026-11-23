package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.StreamotesBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.net.URI;

/** A validated PNG atlas remains usable offline regardless of its filesystem age. */
@Mixin(targets = "xeed.mc.streamotes.addon.TwitchEmotesAPI", remap = false)
public abstract class StreamotesLocalImageMixin {
    @Inject(method = "loadEmoteImage(Lxeed/mc/streamotes/emoticon/Emoticon;Ljava/net/URI;Ljava/lang/String;Ljava/lang/String;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void moonotes$loadSharedPng(@Coerce Object emote, URI source, String provider,
                                              String id, CallbackInfo ci) {
        if (StreamotesBridge.loadCachedImage(emote, source, provider, id)) ci.cancel();
    }
}
