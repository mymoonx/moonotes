package dev.mymoonx.moonotesnetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;

/** Billboard name-tag fallback for local reactions; real sprite display is optional. */
public final class LocalReactionTag {
    private LocalReactionTag() {}
    public static void decorate(Entity entity, EntityRenderState state, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(entity instanceof Player) || state == null || entity.isInvisible()) return;
        String emote = LocalReactions.current(entity.getUUID(), System.currentTimeMillis());
        if (emote.isEmpty() || state.distanceToCameraSq > 32 * 32) return;
        Component label = WorldEmoteText.decorate(Component.literal(emote));
        Component existing = state.nameTag == null ? Component.literal(entity.getName().getString()) : state.nameTag;
        state.nameTag = Component.empty().append(existing).append(Component.literal("  ✦ ")).append(label);
        state.nameTagAttachment = entity.getAttachments()
            .getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTicks));
    }
}
