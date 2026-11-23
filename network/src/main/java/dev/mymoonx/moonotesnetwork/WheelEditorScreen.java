package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Local draft editor. Choosing an image never sends chat or starts a reaction. */
final class WheelEditorScreen extends Screen {
    private static final int ROW_HEIGHT = 30;
    private final Screen parent;
    private final WheelSettings settings = WheelSettings.local();
    private final LibraryPreferences preferences = LibraryPreferences.local();
    private List<String> draft;
    private List<ChatPalette.Emote> source = List.of(), results = List.of();
    private final Map<String, ChatPalette.Emote> indexed = new HashMap<>();
    private final MoonotesButton[] slots = new MoonotesButton[WheelSettings.SLOTS];
    private EmotePreviews previews;
    private EditBox search;
    private String query = "", status = "";
    private int selected, row, candidate = -1;
    private int left, contentWidth, slotWidth, slotsX, slotsY, detailsY, listTop, listBottom, visibleRows;
    private long revision = -1;
    private int lastHovered = -1;
    private long hoverSince;

    WheelEditorScreen(Screen parent) { this(parent, 0); }
    WheelEditorScreen(Screen parent, int selected) {
        super(Component.literal("Настройка колеса"));
        this.parent = parent;
        this.selected = Math.clamp(selected, 0, WheelSettings.SLOTS - 1);
        settings.seed(EmoteWheelScreen.choose(ChatPalette.snapshot(), preferences));
        draft = settings.slots();
    }

    @Override protected void init() {
        if (previews == null) previews = new EmotePreviews();
        contentWidth = Math.min(610, width - 24);
        left = (width - contentWidth) / 2;
        slotWidth = Math.min(46, (contentWidth - 7 * 4) / 8);
        slotsX = (width - (slotWidth * 8 + 7 * 4)) / 2;
        slotsY = 39;
        detailsY = slotsY + slotWidth + 5;
        for (int i = 0; i < slots.length; i++) {
            final int slot = i;
            slots[i] = button(slotsX + i * (slotWidth + 4), slotsY, slotWidth, slotWidth,
                "", "Слот " + (i + 1) + ". Порядок — по часовой стрелке, начиная сверху.", false,
                b -> { selected = slot; clearFocus(); });
        }
        button(left, detailsY, 22, 20, "←", "Поменять выбранный слот с предыдущим.", false, b -> move(-1));
        button(left + 26, detailsY, 22, 20, "→", "Поменять выбранный слот со следующим.", false, b -> move(1));
        button(left + contentWidth - 67, detailsY, 67, 20, "Очистить", "Оставить выбранный слот пустым.", false,
            b -> { draft = WheelSettings.assign(draft, selected, ""); status = ""; clearFocus(); });
        search = addRenderableWidget(new MoonotesEditBox(font, left, detailsY + 26, contentWidth, 22,
            Component.literal("Поиск эмота для колеса")));
        search.setHint(Component.literal("Поиск по названию…"));
        search.setMaxLength(80);
        search.setValue(query);
        search.setResponder(value -> { query = value; row = 0; candidate = -1; filter(); });
        listTop = detailsY + 54;
        listBottom = height - 42;
        visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        listBottom = listTop + visibleRows * ROW_HEIGHT;
        int actionWidth = (contentWidth - 12) / 3;
        button(left, height - 29, actionWidth, 22, "Сброс", "Заполнить слоты избранными и недавними эмотами. Сохранение — отдельной кнопкой.",
            false, b -> {
                var reset = new ArrayList<>(WheelSettings.emptySlots());
                var defaults = EmoteWheelScreen.choose(source, preferences);
                for (int i = 0; i < defaults.size(); i++) reset.set(i, defaults.get(i).key());
                draft = List.copyOf(reset); status = ""; clearFocus();
            });
        button(left + actionWidth + 6, height - 29, actionWidth, 22, "Отмена", "Закрыть без сохранения. Esc делает то же самое.",
            false, b -> onClose());
        button(left + (actionWidth + 6) * 2, height - 29, contentWidth - (actionWidth + 6) * 2, 22,
            "Сохранить", "Сохранить эмоты и порядок восьми слотов.", true, b -> {
                if (settings.saveSlots(draft)) minecraft.gui.setScreen(parent);
                else status = settings.error();
            });
        refreshSource();
    }

    private MoonotesButton button(int x, int y, int w, int h, String title, String hint, boolean accent,
                                  net.minecraft.client.gui.components.Button.OnPress action) {
        return addRenderableWidget(new MoonotesButton(new MoonotesLayout.Rect(x, y, w, h), title, hint, accent, action));
    }

    private void refreshSource() {
        revision = ChatPalette.revision();
        source = ChatPalette.snapshot();
        indexed.clear();
        for (ChatPalette.Emote emote : source) indexed.putIfAbsent(emote.key(), emote);
        filter();
    }

    private void filter() {
        results = ChatPalette.find(source, query, 2000, preferences::favorite, preferences::lastUsed, "all");
        row = Math.clamp(row, 0, Math.max(0, results.size() - visibleRows));
        candidate = Math.clamp(candidate, -1, results.size() - 1);
    }

    private void move(int direction) {
        int next = Math.floorMod(selected + direction, WheelSettings.SLOTS);
        var reordered = new ArrayList<>(draft);
        Collections.swap(reordered, selected, next);
        draft = List.copyOf(reordered);
        selected = next;
        status = "";
        clearFocus();
    }

    private void assign(int index) {
        if (index < 0 || index >= results.size()) return;
        draft = WheelSettings.assign(draft, selected, results.get(index).key());
        status = "";
        candidate = index;
        clearFocus();
    }

    private int rowAt(double x, double y) {
        if (x < left || x >= left + contentWidth || y < listTop || y >= listBottom) return -1;
        int index = row + (int) (y - listTop) / ROW_HEIGHT;
        return index < results.size() ? index : -1;
    }

    private String slotName(int slot) {
        ChatPalette.Emote emote = indexed.get(draft.get(slot));
        return emote != null ? emote.name() : draft.get(slot).isEmpty() ? "Пустой слот" : "Каталог выключен";
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (revision != ChatPalette.revision()) refreshSource();
        g.fill(0, 0, width, height, 0xF0111C2C);
        g.centeredText(font, getTitle(), width / 2, 8, 0xFFF0F5FC);
        String hint = status.isBlank() ? "Выбери слот сверху, затем эмот из списка" : status;
        g.centeredText(font, fit(hint, contentWidth), width / 2, 23, status.isBlank() ? 0xFFA6BDD5 : 0xFFFFC596);
        g.text(font, Component.literal(fit("Слот " + (selected + 1) + ": " + slotName(selected), contentWidth - 131)),
            left + 56, detailsY + 6, 0xFFF4F0E4, false);

        int hovered = rowAt(mx, my);
        if (hovered != lastHovered) { lastHovered = hovered; hoverSince = System.nanoTime(); }
        g.fill(left, listTop, left + contentWidth, listBottom, 0xFF142236);
        for (int shown = 0; shown < visibleRows && row + shown < results.size(); shown++) {
            int index = row + shown, y = listTop + shown * ROW_HEIGHT;
            ChatPalette.Emote emote = results.get(index);
            boolean assigned = draft.get(selected).equals(emote.key());
            boolean highlighted = index == hovered || index == candidate;
            if (assigned || highlighted)
                g.fill(left + 1, y + 1, left + contentWidth - 5, y + ROW_HEIGHT - 1, assigned ? 0xFF2D4E6D : 0xFF243950);
            previews.draw(g, emote.id(), left + 5, y + 3, 24);
            String label = emote.name();
            int slot = draft.indexOf(emote.key());
            if (slot >= 0) label += "  ·  слот " + (slot + 1);
            g.text(font, Component.literal(fit(label, contentWidth - 43)), left + 35, y + 5, 0xFFEAF3FF, false);
            g.text(font, Component.literal(fit(emote.channel(), contentWidth - 43)), left + 35, y + 17, 0xFF8FA9C4, false);
        }
        if (results.isEmpty()) g.centeredText(font, source.isEmpty() ? "Нет включённых каталогов" : "Эмоты не найдены",
            width / 2, listTop + 11, 0xFFA6BDD5);
        if (results.size() > visibleRows) {
            int track = listBottom - listTop, thumb = Math.max(12, track * visibleRows / results.size());
            int y = listTop + row * (track - thumb) / Math.max(1, results.size() - visibleRows);
            g.fill(left + contentWidth - 3, listTop, left + contentWidth, listBottom, 0xFF26394E);
            g.fill(left + contentWidth - 3, y, left + contentWidth, y + thumb, 0xFF7799BC);
        }
        super.extractRenderState(g, mx, my, delta);
        for (int i = 0; i < slots.length; i++) {
            int x = slotsX + i * (slotWidth + 4);
            if (i == selected) g.outline(x, slotsY, slotWidth, slotWidth, 0xFFFFD58C);
            ChatPalette.Emote emote = indexed.get(draft.get(i));
            if (emote != null) previews.draw(g, emote.id(), x + 7, slotsY + 9, Math.max(8, slotWidth - 14));
            else g.centeredText(font, "+", x + slotWidth / 2, slotsY + (slotWidth - font.lineHeight) / 2, 0xFF8FA9C4);
            g.text(font, Component.literal(Integer.toString(i + 1)), x + 3, slotsY + 2, 0xFFBBCDE0, false);
        }
        if (hovered >= 0 && System.nanoTime() - hoverSince >= 500_000_000L) g.setTooltipForNextFrame(font,
            font.split(Component.literal(results.get(hovered).name() + "\nНажать — назначить в слот " + (selected + 1)),
                Math.min(300, contentWidth)), mx, my);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int index = rowAt(event.x(), event.y());
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && index >= 0) { assign(index); return true; }
        return false;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= left && x < left + contentWidth && y >= listTop && y < listBottom && vertical != 0) {
            row = Math.clamp(row - (int) Math.copySign(Math.max(1, Math.ceil(Math.abs(vertical))), vertical),
                0, Math.max(0, results.size() - visibleRows));
            candidate = -1;
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) { onClose(); return true; }
        if (getFocused() == null || getFocused() == search) {
            if ((event.isDown() || event.isUp()) && !results.isEmpty()) {
                candidate = Math.clamp(candidate < 0 ? row : candidate + (event.isDown() ? 1 : -1), 0, results.size() - 1);
                if (candidate < row) row = candidate;
                if (candidate >= row + visibleRows) row = candidate - visibleRows + 1;
                return true;
            }
            if (event.isConfirmation() && candidate >= 0) { assign(candidate); return true; }
        }
        return super.keyPressed(event);
    }

    @Override protected void setInitialFocus() { clearFocus(); }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) { }
    @Override public void removed() {
        if (previews != null) { previews.close(); previews = null; }
        super.removed();
    }
}
