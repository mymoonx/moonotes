package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.PaintRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Paint only a plain local sender prefix. Do not rewrite complex components,
 * other players' nicknames, servers' system messages, or signed payloads.
 * Chat colors are sampled at message arrival; live animation needs rendering hooks.
 */
@Mixin(value = ChatComponent.class, remap = false)
public abstract class PaintChatMixin {
    @ModifyVariable(method = "addPlayerMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private Component moonotes$colorPlainLocalChat(Component original) {
        return PaintRenderer.decorateLocalChat(original);
    }
}
