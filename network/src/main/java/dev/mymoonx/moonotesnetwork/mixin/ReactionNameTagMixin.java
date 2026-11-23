package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.LocalReactionTag;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render-only, client-local reaction tag; no entity or packet mutation. */
@Mixin(value = EntityRenderer.class, remap = false)
public abstract class ReactionNameTagMixin {
    @Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
        at = @At("TAIL"), require = 0)
    private void moonotes$reaction(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        LocalReactionTag.decorate(entity, state, partialTicks);
    }
}
