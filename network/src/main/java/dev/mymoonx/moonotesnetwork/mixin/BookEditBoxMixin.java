package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.BookEmoteRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Leaves the editable buffer, line widths, cursor and selection entirely vanilla. */
@Mixin(value = MultiLineEditBox.class, remap = false)
public abstract class BookEditBoxMixin extends AbstractTextAreaWidget {
    @Shadow @Final private MultilineTextField textField;

    protected BookEditBoxMixin(int x, int y, int width, int height, Component title,
                               AbstractScrollArea.ScrollbarSettings settings) {
        super(x, y, width, height, title, settings);
    }

    @Redirect(method = "extractContents", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"))
    private void moonotes$drawEditableLine(GuiGraphicsExtractor graphics, Font font, String part,
                                           int x, int y, int color, boolean shadow) {
        if (Minecraft.getInstance().gui.screen() instanceof BookEditScreen) {
            int row = (y - getInnerTop()) / font.lineHeight;
            if (row >= 0 && row < textField.getLineCount()) {
                BookLineViewAccessor line = (BookLineViewAccessor) (Object) textField.getLineView(row);
                // Vanilla splits a line at the cursor. Match the full page so
                // fragments of an emote name can never become another emote.
                int offset = x == getInnerLeft() ? line.moonotes$beginIndex() : line.moonotes$endIndex() - part.length();
                if (BookEmoteRenderer.drawEditablePart(graphics, font, textField.value(),
                        offset, part, x, y, color, shadow)) return;
            }
        }
        graphics.text(font, part, x, y, color, shadow);
    }
}
