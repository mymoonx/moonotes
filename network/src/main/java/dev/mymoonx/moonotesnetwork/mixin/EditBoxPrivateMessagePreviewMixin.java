package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.PrivateMessagePreview;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CommandSuggestions and other formatters run first. On the final formatted
 * glyph stream, overlay just the allowed /msg or /r message argument emotes.
 * Never touches the buffer, nickname, server command or packet.
 */
@Mixin(value = EditBox.class, remap = false)
public abstract class EditBoxPrivateMessagePreviewMixin {
    @Inject(method = "applyFormat", at = @At("RETURN"), cancellable = true, require = 0)
    private void moonotes$formatPrivateEmotes(String text, int offset,
            CallbackInfoReturnable<FormattedCharSequence> cir) {
        FormattedCharSequence current = cir.getReturnValue();
        FormattedCharSequence decorated = PrivateMessagePreview.decorate(
            (EditBox) (Object) this, text, offset, current);
        if (decorated != current) cir.setReturnValue(decorated);
    }
}
