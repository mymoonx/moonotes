package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.BttvChatText;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Applies BTTV modifiers to the displayed server message immediately before
 * Streamotes' own chat emote decoration. No server messages are edited.
 */
@Mixin(value = ComponentRenderUtils.class, remap = false, priority = 800)
public abstract class BttvChatTextMixin {
    @ModifyVariable(method = "wrapComponents", at = @At("HEAD"),
        argsOnly = true, ordinal = 0, require = 0)
    private static FormattedText moonotes$decorateBttv(FormattedText value) {
        return BttvChatText.decorate(value);
    }
}
