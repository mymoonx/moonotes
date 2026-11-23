package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
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
 * Eight-slot client-side radial picker. No server interaction: left-click
 * opens an unsent chat draft, right-click shows a local temporary reaction.
 */
final class EmoteWheelScreen extends Screen {
    private static final int MAX = 8;
    private static final int BOX = 42;
    private final List<ChatPalette.Emote> choices;
    private EmotePreviews previews;
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
    }

    private int radius() { return Math.max(58, Math.min(104, Math.min(width, height) / 3)); }
    private int centerX() { return width / 2; }
    private int centerY() { return height / 2; }
    private int slotX(int i) {
        return centerX() + (int) Math.round(radius() * Math.cos(-Math.PI / 2 + i * 2 * Math.PI / MAX)) - BOX / 2;
    }
    private int slotY(int i) {
        return centerY() + (int) Math.round(radius() * Math.sin(-Math.PI / 2 + i * 2 * Math.PI / MAX)) - BOX / 2;
    }
    private int hovered(double x, double y) {
        for (int i = 0; i < choices.size(); i++) {
            if (x >= slotX(i) && x < slotX(i) + BOX
                    && y >= slotY(i) && y < slotY(i) + BOX) return i;
        }
        return -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int cx = centerX(), cy = centerY();
        g.fill(0, 0, width, height, 0xA0121827);
        g.fill(cx - 84, cy - 18, cx + 84, cy + 20, 0xD31C2B40);
        g.outline(cx - 84, cy - 18, 168, 38, 0xFF6480A5);
        g.centeredText(font, "MOONOTES", cx, cy - 12, 0xFFE7F0FD);
        g.centeredText(font, "ЛКМ: чат  •  ПКМ: реакция", cx, cy + 2, 0xFFADBFD6);
        for (int i = 0; i < choices.size(); i++) {
            var emote = choices.get(i);
            int x = slotX(i), y = slotY(i);
            boolean selected = hovered(mouseX, mouseY) == i;
            g.fill(x, y, x + BOX, y + BOX, selected ? 0xF047678C : 0xE0223349);
            g.outline(x, y, BOX, BOX, selected ? 0xFFFFD58C : 0xFF5D7796);
            previews.draw(g, emote.id(), x + 7, y + 4, 28);
            String name = font.plainSubstrByWidth(emote.name(), BOX - 4);
            g.text(font, name, x + (BOX - font.width(name)) / 2, y + 33, 0xFFFFFFFF, false);
        }
        if (choices.isEmpty())
            g.centeredText(font, "Добавь каталог или эмоты в избранное", cx, cy + 30, 0xFFFFCE9E);
        if (!status.isBlank())
            g.centeredText(font, status, cx, Math.min(height - 16, cy + radius() + 33), 0xFFFFCE9E);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int index = hovered(event.x(), event.y());
        if (index >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            ChatPalette.Emote emote = choices.get(index);
            try {
                ChatInsertion.open(emote.name());
                LibraryPreferences.local().used(emote.key());
            } catch (IllegalArgumentException error) {
                status = "Не удалось вставить эмот в чат";
            }
            return true;
        }
        if (index >= 0 && event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            LocalReactions.show(choices.get(index));
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
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
        super.removed();
    }
}
