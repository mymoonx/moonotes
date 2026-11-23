package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.MoonotesNetworkClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;

/** The same 7TV/Cloudflare policy as prefetch, without a second unbounded request. */
@Mixin(targets = "xeed.mc.streamotes.addon.TwitchEmotesAPI", remap = false)
public abstract class TwitchEmotesApiMixin {
    @Inject(method = "openStream(Ljava/net/URI;)Ljava/io/InputStream;", at = @At("HEAD"), cancellable = true, remap = false)
    private static void moonotes$viaConfiguredSource(URI original, CallbackInfoReturnable<InputStream> cir) {
        try {
            InputStream image = MoonotesNetworkClient.openImageStream(original);
            if (image != null) cir.setReturnValue(image);
        } catch (IOException failure) {
            // TwitchEmotesAPI.openStream wraps IO failures in this same exception.
            throw new UncheckedIOException(failure);
        }
        // Unsupported providers use Streamotes unchanged. An eligible image failure
        // propagates like the original loader, respecting forced modes.
    }
}
