package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.PaintSettings;
import dev.mymoonx.moonotesnetwork.PaintStyle;
import dev.mymoonx.moonotesnetwork.TwitchLink;
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
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || original == null || !original.getSiblings().isEmpty()) return original;
        if (!TwitchLink.local().linkedFor(minecraft.player.getUUID().toString())) return original;
        PaintSettings settings = PaintSettings.local();
        PaintStyle paint = settings.paint();
        if (!settings.chat() || paint == null) return original;
        String name = minecraft.player.getGameProfile().getName();
        String prefix = "<" + name + "> ";
        String message = original.getString();
        if (!message.startsWith(prefix) || message.length() > 512) return original;
        MutableComponent decorated = Component.literal("<");
        decorated.append(paint.colored(name, false, System.currentTimeMillis()));
        decorated.append(Component.literal("> " + message.substring(prefix.length())));
        return decorated;
    }
}
