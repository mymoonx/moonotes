package dev.nicetry.streamotesinput.mixin;

import dev.nicetry.streamotesinput.InputEmoteFormatter;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Installs the preview only in the vanilla chat text box. */
@Mixin(value = ChatScreen.class, remap = false)
public abstract class ChatScreenMixin {
    @Shadow protected EditBox input;

    @Inject(method = "init", at = @At("TAIL"))
    private void streamotesInputPreview$init(CallbackInfo ci) {
        if (input != null) {
            input.addFormatter(InputEmoteFormatter::format);
        }
    }
}
