package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A stable eight-sector ring with a separate channel selector and slot editor. */
final class EmoteWheelScreen extends Screen {
    private final WheelSettings settings = WheelSettings.local();
    private ChatPalette.Emote[] choices = new ChatPalette.Emote[WheelSettings.SLOTS];
    private EmotePreviews previews;
    private List<WheelGeometry.Span> ring = List.of();
    private WheelLayout layout;
    private String status = "";
    private long statusUntil;

    EmoteWheelScreen() { super(Component.literal("Колесо эмотов Moonotes")); }

    static List<ChatPalette.Emote> choose(List<ChatPalette.Emote> source, LibraryPreferences prefs) {
        if (source == null || source.isEmpty()) return List.of();
        List<ChatPalette.Emote> result = new ArrayList<>(WheelSettings.SLOTS);
        Set<String> seen = new HashSet<>();
        for (String mode : List.of("favorites", "recent", "all")) {
            for (ChatPalette.Emote emote : ChatPalette.find(source, "", 100,
                    prefs::favorite, prefs::lastUsed, mode)) {
                if (seen.add(emote.key())) result.add(emote);
                if (result.size() >= WheelSettings.SLOTS) return List.copyOf(result);
            }
        }
        return List.copyOf(result);
    }

    @Override protected void init() {
        super.init();
        if (previews == null) previews = new EmotePreviews();
        List<ChatPalette.Emote> available = ChatPalette.snapshot();
        settings.seed(choose(available, LibraryPreferences.local()));
        choices = settings.resolve(available);
        layout = WheelLayout.of(width, height);
        ring = WheelGeometry.spans(layout.inner(), layout.outer());
        // Keep the actual ring uncluttered: page name/count lives inside its center.
        int controlWidth=Math.min(width-24,260), x=(width-controlWidth)/2;
        int channelWidth=controlWidth-82;
        addRenderableWidget(new MoonotesButton(new MoonotesLayout.Rect(x,10,channelWidth,22),
            channelLabel(),"ЛКМ — отправить в выбранный чат; префикс задаётся в настройках Moonotes.",
            false,b -> {
                settings.setChannel(settings.channel()==WheelSettings.Channel.LOCAL
                    ? WheelSettings.Channel.GLOBAL:WheelSettings.Channel.LOCAL);
                b.setMessage(Component.literal(channelLabel()));clearFocus();
            }));
        addRenderableWidget(new MoonotesButton(new MoonotesLayout.Rect(x+channelWidth+6,10,
            controlWidth-channelWidth-6,22),"Настроить",
            "Восемь слотов, список страниц и привязка групп.",false,
            b -> minecraft.gui.setScreen(new WheelEditorScreen(this))));
    }

    private String channelLabel() {
        return settings.channel() == WheelSettings.Channel.LOCAL
            ? "Чат: локальный" : "Чат: глобальный ("+settings.prefix()+")";
    }
    private String pageLabel() {return (settings.page()+1)+"/"+settings.pageCount();}
    private void changePage(int direction) {
        settings.stepPage(direction);
        choices=settings.resolve(ChatPalette.snapshot());
        // Update selector and page title without recreating previews.
        // Page change does not reopen the Screen or lose previews/mouse state.
        status="";statusUntil=0;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int cx = layout.centerX(), cy = layout.centerY();
        int hovered = layout.hit(mouseX, mouseY);
        g.fill(0, 0, width, height, 0x9A0B1222);
        for (WheelGeometry.Span span : ring) {
            boolean active = choices[span.slot()] != null;
            int color = !active ? 0x80344357 : span.slot() == hovered ? 0xE95A90BF : 0xD9273D57;
            if (span.edge() == 1) color = 0xFF526C8B;
            else if (span.edge() == 2) color = span.slot() == hovered ? 0xFFFFD58C : 0xFF6587A8;
            else if (span.edge() == 3) color = 0xFF102438;
            g.fill(cx + span.from(), cy + span.y(), cx + span.to(), cy + span.y() + 1, color);
        }
        for (int i = 0; i < choices.length; i++) {
            if (choices[i] != null) previews.draw(g, choices[i].id(), layout.iconX(i), layout.iconY(i), layout.icon());
            else g.centeredText(font, "+", layout.iconX(i) + layout.icon() / 2,
                layout.iconY(i) + (layout.icon() - font.lineHeight) / 2, 0xFF879CB4);
        }

        // The wheel itself is the title. Use its empty center for the selected
        // emote name instead of leaving an unreadable label over the hotbar.
        String hoveredName = hovered >= 0
            ? choices[hovered] != null ? choices[hovered].name()
                : settings.slots().get(hovered).isEmpty() ? "Пустой слот" : "Эмот недоступен"
            : null;
        String feedback = System.currentTimeMillis() < statusUntil ? status
            : !settings.error().isBlank() ? settings.error() : null;
        WheelCenterLabels labels = WheelCenterLabels.of(
            settings.pageTitle(), settings.page() + 1, settings.pageCount(), hoveredName, feedback);
        String name = labels.title();
        int centerWidth = Math.max(54, layout.inner() * 2 - 24);
        List<net.minecraft.util.FormattedCharSequence> nameLines =
            font.split(Component.literal(name), centerWidth);
        int maxLines = Math.min(3, nameLines.size());
        int firstY = cy - maxLines * 11 / 2 - 7;
        for (int i = 0; i < maxLines; i++) {
            var line = nameLines.get(i);
            g.text(font, line, cx - font.width(line) / 2, firstY + i * 11,
                0xFFF2F6FC, false);
        }
        // No duplicate page title: idle shows only 1/2, hovered emotes show "Page · 1/2".
        if (!labels.subtitle().isBlank()) {
            g.centeredText(font, Component.literal(font.plainSubstrByWidth(
                labels.subtitle(), Math.max(54, layout.inner() * 2 - 18))), cx,
                cy + Math.max(13, layout.inner() / 2), 0xFFA5C5E4);
        }
        // Action hints are immediately below the ring in their own translucent
        // panel; never placed over the health bar or hotbar.
        int panelWidth = Math.min(width - 24, 372), left = (width - panelWidth) / 2;
        int hintY = Math.min(height - 43, cy + layout.outer() + 10);
        g.fill(left, hintY, left + panelWidth, hintY + 35, 0xE61B2B43);
        g.outline(left, hintY, panelWidth, 35, 0xFF6082A7);
        g.centeredText(font, "ЛКМ — отправить   ·   ПКМ — реакция",
            cx, hintY + 6, 0xFFF2F7FE);
        g.centeredText(font, "Shift + ЛКМ — черновик   ·   Esc — закрыть",
            cx, hintY + 20, 0xFFBFD4EB);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if(layout!=null && vertical!=0) {
            double dx=x-layout.centerX(), dy=y-layout.centerY();
            if(dx*dx+dy*dy<=layout.outer()*layout.outer()) {
                if(settings.pageCount()>1)changePage(vertical>0?-1:1);
                return true;
            }
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int index = layout.hit(event.x(), event.y());
        if (index < 0) return false;
        ChatPalette.Emote emote = choices[index];
        if (emote == null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            minecraft.gui.setScreen(new WheelEditorScreen(this, index));
            return true;
        }
        if (emote == null) return false;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            try {
                if (event.hasShiftDown()) ChatInsertion.open(emote.name(), settings.channel());
                else { ChatInsertion.send(emote.name(), settings.channel()); onClose(); }
                LibraryPreferences.local().used(emote.key());
            } catch (IllegalArgumentException error) {
                status = error.getMessage() == null ? "Не удалось отправить эмот" : error.getMessage();
                statusUntil = System.currentTimeMillis() + 3500;
            }
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            if (!ExtrasSettings.local().reactions()) {
                status = "Реакции выключены в настройках Moonotes";
                statusUntil = System.currentTimeMillis() + 3500;
            } else { LocalReactions.show(emote); onClose(); }
            return true;
        }
        return false;
    }

    @Override public void onClose() { if (minecraft != null) minecraft.gui.setScreen(null); }
    @Override protected void setInitialFocus() { clearFocus(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) { }
    @Override public void removed() {
        if (previews != null) { previews.close(); previews = null; }
        ring = List.of();
        super.removed();
    }
}
