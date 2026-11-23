package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * True eight-sector ring. Emotes use their image previews, not cramped
 * name-on-image rectangles. Selected names appear separately and may wrap.
 */
final class EmoteWheelScreen extends Screen {
    private static final int MAX = 8;
    private static final int ICON = 38;
    private final List<ChatPalette.Emote> choices;
    private EmotePreviews previews;
    private List<WheelGeometry.Span> ring = List.of();
    private int outer, inner, middle;
    private String status = "";

    EmoteWheelScreen() {
        super(Component.literal("Колесо эмотов Moonotes"));
        choices = choose(ChatPalette.snapshot(), LibraryPreferences.local());
    }

    static List<ChatPalette.Emote> choose(List<ChatPalette.Emote> source, LibraryPreferences prefs) {
        if (source == null || source.isEmpty()) return List.of();
        List<ChatPalette.Emote> result = new ArrayList<>(MAX);
        Set<String> seen = new HashSet<>();
        for (String mode : List.of("favorites", "recent", "all")) {
            for (ChatPalette.Emote emote : ChatPalette.find(source, "", 100,
                    prefs::favorite, prefs::lastUsed, mode)) {
                if (seen.add(emote.key())) result.add(emote);
                if (result.size() >= MAX) return List.copyOf(result);
            }
        }
        return List.copyOf(result);
    }

    @Override
    protected void init() {
        super.init();
        if (previews == null) previews = new EmotePreviews();
        outer = Math.min(140, Math.max(65, Math.min(width, height)/2 - 28));
        inner = Math.max(35, (int)(outer * 0.41));
        middle = (inner + outer) / 2;
        ring = WheelGeometry.spans(inner, outer);
    }

    private int hit(double x, double y) {
        int index = WheelGeometry.slot(x - width/2.0, y - height/2.0, inner, outer);
        return index >= 0 && index < choices.size() ? index : -1;
    }

    private int iconX(int i) {
        return width/2 + (int)Math.round(middle * Math.sin(i * Math.PI / 4)) - ICON/2;
    }
    private int iconY(int i) {
        return height/2 - (int)Math.round(middle * Math.cos(i * Math.PI / 4)) - ICON/2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int cx = width / 2, cy = height / 2;
        int hovered = hit(mouseX, mouseY);
        g.fill(0, 0, width, height, 0x9A0B1222);
        for (WheelGeometry.Span span : ring) {
            int color = span.slot() >= choices.size() ? 0x55344357
                : span.slot() == hovered ? 0xE95A90BF : 0xD9273D57;
            if (span.edge() == 1) color = 0xFF526C8B;
            else if (span.edge() == 2) color = span.slot()==hovered ? 0xFFFFD58C : 0xFF6587A8;
            else if (span.edge() == 3) color = 0xFF102438;
            g.fill(cx + span.from(), cy + span.y(), cx + span.to(), cy + span.y()+1, color);
        }
        for (int i = 0; i < choices.size(); i++) {
            int x = iconX(i), y = iconY(i);
            if (i == hovered) {
                g.fill(x-4, y-4, x+ICON+4, y+ICON+4, 0xA66797BF);
                g.outline(x-4, y-4, ICON+8, ICON+8, 0xFFFFD58C);
            }
            previews.draw(g, choices.get(i).id(), x + 3, y + 3, ICON-6);
        }

        g.centeredText(font, "MOONOTES", cx, cy-25, 0xFFEEF6FD);
        String action = hovered < 0 ? "Выбери эмот" : "ЛКМ: отправить";
        g.centeredText(font, action, cx, cy+20, 0xFFA8C7E9);
        g.centeredText(font, "ПКМ: реакция", cx, cy+31, 0xFFA8C7E9);

        if (choices.isEmpty())
            g.centeredText(font, "Нет доступных эмотов", cx, cy, 0xFFFFD49D);
        else {
            String name = hovered >= 0 ? choices.get(hovered).name() : "8 избранных / недавних";
            int maxWidth = Math.max(110, Math.min(width - 24, 340));
            int y = cy + outer + 13;
            String line = font.plainSubstrByWidth(name, maxWidth);
            String suffix = name.length() > line.length() ? "…" : "";
            g.fill(cx - maxWidth/2-8, y-7, cx+maxWidth/2+8, y+17, 0xD21A293D);
            g.centeredText(font, line + suffix, cx, y, 0xFFF8F4E9);
            g.centeredText(font, "Shift + ЛКМ — черновик", cx, y+13, 0xFFA2B9D0);
        }
        if (!status.isBlank()) g.centeredText(font, status, cx,
            Math.min(height-12, cy + outer + 45), 0xFFFFC596);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int index = hit(event.x(), event.y());
        if (index < 0) return super.mouseClicked(event, doubleClick);
        ChatPalette.Emote emote = choices.get(index);
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            try {
                if (event.hasShiftDown()) ChatInsertion.open(emote.name());
                else {
                    ChatInsertion.send(emote.name());
                    onClose();
                }
                LibraryPreferences.local().used(emote.key());
            } catch (IllegalArgumentException error) {
                status = "Не удалось отправить эмот";
            }
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            LocalReactions.show(emote);
            onClose();
            return true;
        }
        return super.mouseClicked(event,doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) { onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(null);
    }

    @Override
    public void removed() {
        if (previews != null) { previews.close(); previews = null; }
        ring = List.of();
        super.removed();
    }
}
