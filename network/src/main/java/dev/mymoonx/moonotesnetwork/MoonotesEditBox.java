package dev.mymoonx.moonotesnetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Native editing, selection and IME, with a background matching the Moonotes panels. */
public final class MoonotesEditBox extends EditBox {
    private boolean drawingText;

    public MoonotesEditBox(Font font, int x, int y, int width, int height, Component label) {
        super(font, x, y, width, height, label);
        setTextColor(MoonotesTheme.TEXT);
        setTextColorUneditable(MoonotesTheme.MUTED);
        setTextShadow(false);
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        if (!isVisible()) return;
        int x=getX(), y=getY(), w=getWidth(), h=getHeight();
        boolean hover=mx>=x && mx<x+w && my>=y && my<y+h;
        int border=isFocused()?MoonotesTheme.ACCENT:hover?0xFF57718E:MoonotesTheme.LINE;
        graphics.fill(x,y,x+w,y+h,border);
        graphics.fill(x+1,y+1,x+w-1,y+h-1,hover||isFocused()?MoonotesTheme.HOVER:MoonotesTheme.CARD);
        // The native bordered state still controls padding and vertical text placement.
        // Only its sprite is suppressed; native text, selection and IME rendering remain.
        drawingText=true;
        try { super.extractWidgetRenderState(graphics,mx,my,delta); }
        finally { drawingText=false; }
    }

    @Override public boolean isBordered() { return !drawingText && super.isBordered(); }
    @Override public int getInnerWidth() { return Math.max(0,getWidth()-(super.isBordered()?8:0)); }
}
