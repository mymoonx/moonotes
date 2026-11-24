package dev.mymoonx.moonotesnetwork.mixin;

import dev.mymoonx.moonotesnetwork.BookEmoteRenderer;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace visible page spans; book data, links and cached wrapping stay vanilla. */
@Mixin(value = BookViewScreen.class, remap = false)
public abstract class BookTextMixin {
    @Shadow private BookViewScreen.BookAccess bookAccess;
    @Shadow private int currentPage;
    @Unique private GuiGraphicsExtractor moonotes$bookGraphics;
    @Unique private BookEmoteRenderer.PageContext moonotes$pageContext;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void moonotes$beginPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                    float delta, CallbackInfo ci) {
        moonotes$bookGraphics = graphics;
        moonotes$pageContext = new BookEmoteRenderer.PageContext(bookAccess.getPage(currentPage).getString());
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void moonotes$endPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float delta, CallbackInfo ci) {
        moonotes$bookGraphics = null;
        moonotes$pageContext = null;
    }

    @Redirect(method = "visitText", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/gui/ActiveTextCollector;accept(IILnet/minecraft/util/FormattedCharSequence;)V"))
    private void moonotes$drawPageLine(ActiveTextCollector collector, int x, int y,
                                      FormattedCharSequence line) {
        // visitText also handles link hit testing, outside render extraction.
        if (moonotes$bookGraphics == null) collector.accept(x, y, line);
        else BookEmoteRenderer.drawPageLine(moonotes$bookGraphics, collector, x, y, line, moonotes$pageContext);
    }
}
