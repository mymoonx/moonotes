package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.GlobalChatRichText;
import dev.mymoonx.moonotesnetwork.StreamotesBridge;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Render-only !WW handling, before Streamotes decorates glyphs.
 * Keeps full registered aliases such as !vanish and !setgame unchanged.
 * Also supports a server chat plugin placing ! and WW in different styles.
 * Original message text and the server-bound message are not modified.
 */
@Mixin(value = ComponentRenderUtils.class, remap = false)
public abstract class GlobalChatPrefixMixin {
    @ModifyVariable(method = "wrapComponents", at = @At("HEAD"),
        argsOnly = true, ordinal = 0, require = 0)
    private static FormattedText moonotes$globalEmotePrefix(FormattedText text) {
        return GlobalChatRichText.decorate(text, StreamotesBridge.activeChatPrefix(),
            StreamotesBridge::registeredEmoteName);
    }
}
