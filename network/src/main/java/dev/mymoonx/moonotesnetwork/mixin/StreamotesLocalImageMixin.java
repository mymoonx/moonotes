package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.StreamotesBridge;
import dev.mymoonx.moonotesnetwork.BttvImageRequestGuard;
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
        // BTTV modifier pseudo-emotes are text operators, not drawable icons.
        // Their 10-second HTTP timeouts can block Streamotes' single FIFO loader.
        if (BttvImageRequestGuard.skip(provider, emote)) {
            ci.cancel();
            return;
        }
        if (StreamotesBridge.loadCachedImage(emote, source, provider, id)) ci.cancel();
    }
}
