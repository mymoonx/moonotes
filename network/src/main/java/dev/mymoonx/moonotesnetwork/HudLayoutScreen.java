package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Edits a draft. Save commits once; Escape/Cancel preserve the previous placement. */
final class HudLayoutScreen extends Screen {
    private final Screen parent;
    private HudLayout.Placement draft;
    private boolean dragging;
    private double grabX, grabY;
    private final MoonotesButton[] corners = new MoonotesButton[4];

    HudLayoutScreen(Screen parent) {
        super(Component.literal("Расположение Moonotes"));
        this.parent = parent;
        draft = MoonotesNetworkClient.hudPlacement();
    }

    @Override protected void init() {
        dragging = false;
        int content = Math.min(570, Math.max(1, width - 16));
        int left = (width - content) / 2, y = height - 30;
        int cornerWidth = content < 250 ? 18 : 22;
        int gap = 4;
        int actionWidth = Math.max(20, (content - cornerWidth * 4 - gap * 6) / 3);
        String[] labels = {"ЛВ", "ПВ", "ЛН", "ПН"};
        String[] names = {"Сверху слева", "Сверху справа", "Снизу слева", "Снизу справа"};
        for (int i = 0; i < 4; i++) {
            final int corner = i;
            corners[i] = addRenderableWidget(new MoonotesButton(
                new MoonotesLayout.Rect(left + i * (cornerWidth + gap), y, cornerWidth, 22),
                labels[i], names[i] + ". Можно затем перетащить панель в точное место.", false,
                button -> { draft = HudLayout.Placement.corner(corner); clearFocus(); }));
        }
        int actionsX = left + 4 * (cornerWidth + gap);
        addRenderableWidget(new MoonotesButton(new MoonotesLayout.Rect(actionsX, y, actionWidth, 22),
            "Сброс", "Вернуть стандартное положение: справа под областью эффектов.", false,
            button -> { draft = HudLayout.Placement.defaults(); clearFocus(); }));
        addRenderableWidget(new MoonotesButton(new MoonotesLayout.Rect(actionsX + actionWidth + gap, y, actionWidth, 22),
            "Отмена", "Закрыть без сохранения. То же действие — Esc.", false, button -> onClose()));
        addRenderableWidget(new MoonotesButton(new MoonotesLayout.Rect(actionsX + (actionWidth + gap) * 2, y, actionWidth, 22),
            "Сохранить", "Сохранить положение. Оно подстроится при изменении размера окна и масштаба GUI.", true,
            button -> { MoonotesNetworkClient.saveHudPlacement(draft); minecraft.gui.setScreen(parent); }));
    }

    private HudLayout.Cards preview() {
        return HudLayout.cards(width, height, draft, true, 23 + font.lineHeight * 2);
    }
    private static boolean contains(HudLayout.Cards cards, double x, double y) {
        return cards.progress() != null && cards.progress().contains(x, y)
            || cards.notice() != null && cards.notice().contains(x, y);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x780C1420);
        g.centeredText(font, getTitle(), width / 2, 11, 0xFFEDF2FA);
        String hint = "Перетащи панель. Стрелки — точнее. Esc — отмена.";
        g.centeredText(font, Component.literal(font.plainSubstrByWidth(hint, Math.max(40, width - 16))),
            width / 2, 27, 0xFFA9B8CB);
        HudLayout.Cards boxes = preview();
        if (boxes.progress() != null) {
            var box = boxes.progress();
            MoonotesNotifications.drawProgress(g, box, "Загрузка эмотов", "216/704 · 580 КиБ/с", 216.0 / 704, 0xFF81B3F5);
            if (dragging || contains(boxes, mx, my)) {
                g.outline(box.x() - 1, box.y() - 1, box.width() + 2, box.height() + 2, 0xFFB8D7FF);
                g.requestCursor(CursorTypes.RESIZE_ALL);
            }
        }
        if (boxes.notice() != null) MoonotesNotifications.drawNotice(g, boxes.notice(), "Пример уведомления",
            font.split(Component.literal("Уведомления располагаются рядом с прогрессом."), boxes.notice().width() - 16), false);
        for (int i = 0; i < corners.length; i++) corners[i].setLabelColor(
            !draft.custom() && draft.corner() == i ? 0xFFAAD2FF : 0xFFA9B8CB);
        super.extractRenderState(g, mx, my, delta);
        if (!draft.custom()) {
            var button = corners[draft.corner()];
            g.fill(button.getX() + 3, button.getY() + button.getHeight() - 3,
                button.getX() + button.getWidth() - 3, button.getY() + button.getHeight() - 2, 0xFF81B3F5);
        }
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        HudLayout.Cards boxes = preview();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && contains(boxes, event.x(), event.y())) {
            clearFocus();
            var primary = boxes.primary();
            grabX = event.x() - primary.x(); grabY = event.y() - primary.y();
            dragging = true;
            return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            draft = HudLayout.moved(width, height, event.x() - grabX, event.y() - grabY);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) { dragging = false; return true; }
        return super.mouseReleased(event);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (getFocused() == null && (event.isLeft() || event.isRight() || event.isUp() || event.isDown())) {
            var box = preview().primary();
            if (box != null) {
                int step = event.hasShiftDown() ? 10 : 1;
                int dx = event.isLeft() ? -step : event.isRight() ? step : 0;
                int dy = event.isUp() ? -step : event.isDown() ? step : 0;
                draft = HudLayout.moved(width, height, box.x() + dx, box.y() + dy);
            }
            return true;
        }
        return super.keyPressed(event);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override protected void setInitialFocus() { clearFocus(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
    @Override public boolean showsActiveEffects() { return true; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) { }
}
