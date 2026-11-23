package dev.mymoonx.moonotesnetwork;

import java.time.Duration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Subordinate navigation: a left marker and text, distinct from the main tab row. */
final class MoonotesSectionButton extends Button {
    private final boolean selected;
    MoonotesSectionButton(MoonotesLayout.Rect rect, String label, String hint, boolean selected, OnPress action) {
        super(rect.x(),rect.y(),rect.width(),rect.height(),Component.literal(label),action,DEFAULT_NARRATION);
        this.selected=selected;
        setTooltip(Tooltip.create(Component.literal(hint)));
        setTooltipDelay(Duration.ofMillis(500));
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta) {
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        boolean hover=isHoveredOrFocused();
        if(selected||hover)g.fill(x,y,x+w,y+h,selected?0xFF25405F:MoonotesTheme.HOVER);
        if(selected)g.fill(x,y+3,x+2,y+h-3,MoonotesTheme.ACCENT);
        if(isFocused())g.outline(x,y,w,h,MoonotesTheme.ACCENT);
        var font=Minecraft.getInstance().font;
        String label=getMessage().getString();
        if(font.width(label)>w-16)label=font.plainSubstrByWidth(label,Math.max(0,w-22))+"…";
        g.text(font,Component.literal(label),x+8,y+(h-font.lineHeight)/2,
            selected||hover?MoonotesTheme.TEXT:MoonotesTheme.MUTED,false);
    }
}
