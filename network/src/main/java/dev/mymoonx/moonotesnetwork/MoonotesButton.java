package dev.mymoonx.moonotesnetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.time.Duration;

/** A native Button with Moonotes colors; keyboard navigation and narration stay native. */
final class MoonotesButton extends Button {
    private final boolean accent;
    private int labelColor=MoonotesTheme.TEXT;
    void setLabelColor(int color){labelColor=color;}
    MoonotesButton(MoonotesLayout.Rect rect, String label, String hint, boolean accent, OnPress action) {
        super(rect.x(), rect.y(), rect.width(), rect.height(), Component.literal(label), action, DEFAULT_NARRATION);
        this.accent = accent;
        setTooltip(Tooltip.create(Component.literal(hint)));
        setTooltipDelay(Duration.ofMillis(500));
    }
    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mx, int my, float partialTick) {
        boolean hovered = isHoveredOrFocused();
        int fill = !active ? 0xFF1A2534 : accent ? (hovered ? 0xFF325B89 : 0xFF25466C)
            : hovered ? MoonotesTheme.HOVER : MoonotesTheme.CARD;
        int border = !active ? 0xFF2B3647 : hovered ? MoonotesTheme.ACCENT : accent ? 0xFF527AA9 : MoonotesTheme.LINE;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        graphics.fill(x, y, x + w, y + h, border);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        if (accent && active) graphics.fill(x + 1,y + h - 2,x + w - 1,y + h - 1,MoonotesTheme.ACCENT);
        if (isFocused()) graphics.fill(x + 3, y + h - 3, x + w - 3, y + h - 2, 0xFFB7D6FF);
        var font = Minecraft.getInstance().font;
        String full = getMessage().getString(), label = full;
        if (font.width(label) > w - 10) label = font.plainSubstrByWidth(label, Math.max(0, w - 16)) + "…";
        graphics.text(font, Component.literal(label), x + Math.max(3, (w - font.width(label)) / 2),
            y + (h - font.lineHeight) / 2, active ? labelColor : 0xFF8390A3, false);
    }
}

