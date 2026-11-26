package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.GlobalChatPrefix;
import dev.mymoonx.moonotesnetwork.StreamotesBridge;
import dev.mymoonx.moonotesnetwork.WheelSettings;
import net.minecraft.client.ComponentCollector;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Optional;

/**
 * Normalize only the visible chat's rich text before Streamotes' original
 * component wrapper decorates registered emotes. This does NOT edit the
 * original Minecraft message, its signature, global routing or server data.
 *
 * !WW -> ! + [WW emote] when WW is registered and !WW is not.
 * !vanish stays [!vanish emote] whenever that exact alias is registered.
 */
@Mixin(value = ComponentRenderUtils.class, remap = false)
public abstract class GlobalChatPrefixMixin {
    @ModifyVariable(method = "wrapComponents", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static FormattedText moonotes$globalEmotePrefix(FormattedText text) {
        if (text == null) return null;
        String prefix = WheelSettings.activePrefix();
        if (prefix == null || prefix.isEmpty()) return text;

        ComponentCollector result = new ComponentCollector();
        final boolean[] changed = {false};
        text.visit((style, content) -> {
            if (content == null || content.isEmpty() || style.getClickEvent() != null
                    || !content.contains(prefix)) {
                result.append(FormattedText.of(content == null ? "" : content, style));
                return Optional.empty();
            }
            var substitutions = GlobalChatPrefix.matches(content, prefix,
                    StreamotesBridge::registeredEmoteName);
            if (substitutions.isEmpty()) {
                result.append(FormattedText.of(content, style));
                return Optional.empty();
            }
            changed[0] = true;
            int cursor = 0;
            for (var replacement : substitutions) {
                if (replacement.contentStart() > cursor)
                    result.append(FormattedText.of(
                        content.substring(cursor, replacement.contentStart()), style));
                result.append(FormattedText.of(replacement.name(),
                    style.withClickEvent(new ClickEvent.CopyToClipboard(replacement.name()))));
                cursor = replacement.end();
            }
            if (cursor < content.length())
                result.append(FormattedText.of(content.substring(cursor), style));
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? result.getResultOrEmpty() : text;
    }
}
