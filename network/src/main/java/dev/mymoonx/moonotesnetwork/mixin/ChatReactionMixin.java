package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.ChatReactionObserver;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jspecify.annotations.Nullable;

/** Observe displayed player messages without mutating content/signatures. */
@Mixin(value = ChatComponent.class, remap = false)
public abstract class ChatReactionMixin {
    @Inject(method="addPlayerMessage", at=@At("TAIL"), require=0)
    private void moonotes$observeReaction(Component message,
            @Nullable MessageSignature signature, @Nullable GuiMessageTag tag,
            CallbackInfo ci) {
        ChatReactionObserver.observe(message);
    }
}
