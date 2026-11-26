package dev.mymoonx.moonotesnetwork;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Explicitly opt-in peer room: no automatic network connection or persistent
 * secret sharing. A private 128-bit code is shared only with trusted testers.
 */
final class NetworkReactionsScreen extends Screen {
    private final Screen parent;
    private MoonotesEditBox codeField;
    private String draft = "";
    private String notice = "";
    private int x, y, w;

    NetworkReactionsScreen(Screen parent) {
        super(Component.literal("Moonotes — сетевые реакции"));
        this.parent = parent;
    }
    @Override protected void init() {
        super.init();
        w = Math.min(420, width - 24);
        x = (width - w) / 2;
        y = Math.max(8, (height - 218) / 2);
        codeField = addRenderableWidget(new MoonotesEditBox(font, x + 12, y + 80,
            w - 24, 22, Component.literal("Код комнаты")));
        codeField.setMaxLength(48);
        codeField.setHint(Component.literal("32 символа 0–9, A–F"));
        codeField.setValue(draft.isEmpty() ? NetworkReactionSession.local().code() : draft);
        codeField.setResponder(value -> draft = value);

        int half = (w - 30) / 2;
        addButton(x + 12, y + 112, half, "Создать комнату",
            "Создать случайный секретный код и подключиться", true,
            button -> { NetworkReactionSession.local().startNew(); draft = ""; rebuild(); });
        addButton(x + 18 + half, y + 112, half, "Присоединиться",
            "Подключиться к комнате друга по коду", false,
            button -> { NetworkReactionSession.local().start(draft); rebuild(); });

        addButton(x + 12, y + 140, half, "Копировать код",
            "Скопировать 32-символьный код и передать другу лично", false,
            button -> {
                String code = NetworkReactionSession.local().code();
                if (!code.isEmpty()) {
                    minecraft.keyboardHandler.setClipboard(code);
                    notice = "Код скопирован — отправь его только доверенному игроку";
                } else notice = "Сначала создай комнату";
            });
        addButton(x + 18 + half, y + 140, half, "Отключиться",
            "Прекратить передачу и получение реакций", false,
            button -> { NetworkReactionSession.local().stop(); draft = ""; rebuild(); });
        addButton(x + w - 104, y + 185, 92, "Готово",
            "Вернуться в настройки Moonotes", true, button -> onClose());
    }
    private MoonotesButton addButton(int bx, int by, int bw, String label,
            String tooltip, boolean accent, net.minecraft.client.gui.components.Button.OnPress action) {
        return addRenderableWidget(new MoonotesButton(
            new MoonotesLayout.Rect(bx, by, bw, 22), label, tooltip, accent, action));
    }
    private void rebuild() { clearWidgets(); init(); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xB00A1320);
        g.fill(x, y, x + w, y + 217, 0xF01C2C42);
        g.outline(x, y, w, 217, 0xFF6484A5);
        g.text(font, "Сетевые реакции · 0.6.2 beta", x + 12, y + 10, 0xFFF3F7FD, false);
        g.text(font, Component.literal(font.plainSubstrByWidth(
            "Без мода на сервере. Подключение только вручную.", w - 24)),
            x + 12, y + 27, 0xFFB3C5D8, false);
        String status = NetworkReactionSession.local().status();
        String connected = "Статус: " + status;
        g.text(font, Component.literal(font.plainSubstrByWidth(connected, w - 24)),
            x + 12, y + 46, 0xFFA6DCC5, false);
        g.text(font, "Других участников: " + NetworkReactionSession.local().peers(),
            x + 12, y + 62, 0xFFACC9E7, false);
        g.text(font, Component.literal(font.plainSubstrByWidth(
            "Комната по коду не подтверждает владение Minecraft-ником.", w - 24)),
            x + 12, y + 170, 0xFFFFCB9A, false);
        if (!notice.isBlank())
            g.text(font, Component.literal(font.plainSubstrByWidth(notice, w - 24)),
                x + 12, y + 201, 0xFFD7EFFF, false);
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) { }
}
