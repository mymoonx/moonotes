package dev.mymoonx.moonotesnetwork.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mymoonx.moonotesnetwork.SignEmoteRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses the sign's existing face transform, with complete textures instead of font glyphs. */
@Mixin(value = AbstractSignRenderer.class, remap = false)
public abstract class SignTextMixin {
    @Shadow @Final private Font font;

    @Inject(method = "submitSignText", at = @At("HEAD"), cancellable = true)
    private void moonotes$signImages(SignRenderState state, PoseStack poseStack,
            SubmitNodeCollector collector, SignText text, CallbackInfo ci) {
        if (SignEmoteRenderer.submit(font, state, poseStack, collector, text)) ci.cancel();
    }
}
