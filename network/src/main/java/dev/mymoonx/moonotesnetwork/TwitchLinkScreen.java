package dev.mymoonx.moonotesnetwork;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.awt.Desktop;
import java.net.URI;

/**
 * User-initiated Twitch login. This screen never asks for a Twitch password,
 * access token or client secret. User code is entered ONLY on Twitch.
 */
final class TwitchLinkScreen extends Screen {
    private final Screen parent;
    private EditBox clientIdField, userCodeField;
    private Button linkButton, unlinkButton, openButton, copyCodeButton;
    private long copiedUntil;
    private int left, top, panelWidth;
    private final TwitchLink link = TwitchLink.local();

    TwitchLinkScreen(Screen parent) {
        super(Component.literal("Moonotes · Twitch"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        panelWidth = Math.min(430, width - 24);
        left = (width - panelWidth) / 2;
        top = Math.max(12, (height - 230) / 2);
        clientIdField = new EditBox(font, left + 12, top + 53, panelWidth - 24, 20,
            Component.literal("Twitch Client ID"));
        clientIdField.setMaxLength(64);
        clientIdField.setValue(link.clientId());
        addRenderableWidget(clientIdField);

        linkButton = addRenderableWidget(Button.builder(Component.literal("Подключить Twitch"),
            b -> {
                if (link.setClientId(clientIdField.getValue()) && minecraft != null && minecraft.player != null)
                    link.start(minecraft.player.getUUID().toString());
                updateButtons();
            }).bounds(left + 12, top + 81, (panelWidth - 30) / 2, 20).build());
        unlinkButton = addRenderableWidget(Button.builder(Component.literal("Отвязать Twitch"),
            b -> { link.unlink(); updateButtons(); })
            .bounds(left + 18 + (panelWidth - 30) / 2, top + 81, (panelWidth - 30) / 2, 20).build());

        int codeWidth = Math.max(84, (panelWidth - 30) / 2 - 18);
        int actionWidth = Math.max(1, (panelWidth - 36 - codeWidth) / 2);
        int actionX = left + 18 + codeWidth;
        userCodeField = new EditBox(font, left + 12, top + 138, codeWidth, 20,
            Component.literal("Код для Twitch"));
        userCodeField.setEditable(false);
        userCodeField.setValue(link.code());
        addRenderableWidget(userCodeField);

        copyCodeButton = addRenderableWidget(Button.builder(Component.literal("Копировать"),
            b -> {
                String activeCode = link.code();
                if (activeCode.isBlank() || minecraft == null) return;
                minecraft.keyboardHandler.setClipboard(activeCode);
                copiedUntil = System.currentTimeMillis() + 2000;
                updateButtons();
            }).bounds(actionX, top + 138, actionWidth, 20).build());

        openButton = addRenderableWidget(Button.builder(Component.literal("Открыть Twitch"),
            b -> {
                String uri = link.verification();
                if (uri.isBlank()) return;
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                        Desktop.getDesktop().browse(URI.create(uri));
                } catch (Exception ignored) {
                    // User may always open twitch.tv/activate manually.
                }
            }).bounds(actionX + actionWidth + 6, top + 138, actionWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Назад"),
            b -> onClose()).bounds(left + panelWidth - 90, top + 197, 78, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        if (clientIdField == null) return;
        String code = link.code();
        if (!userCodeField.getValue().equals(code)) userCodeField.setValue(code);
        copyCodeButton.active = !code.isBlank();
        String copyLabel = System.currentTimeMillis() < copiedUntil ? "Скопировано!" : "Копировать";
        if (!copyCodeButton.getMessage().getString().equals(copyLabel))
            copyCodeButton.setMessage(Component.literal(copyLabel));
        openButton.active = !link.verification().isBlank();
        linkButton.active = !link.connecting();
        unlinkButton.active = link.linked() || link.connecting();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        updateButtons();
        g.fill(left, top, left + panelWidth, top + 224, 0xF0192638);
        g.outline(left, top, panelWidth, 224, 0xFF4A6683);
        g.text(font, "Привязка Twitch", left + 12, top + 12, 0xFFF0F5FC, false);
        g.text(font, "Вводите только публичный Client ID (не секрет!).", left + 12, top + 32, 0xFFB6C5D7, false);
        g.text(font, "Код подтверждается на twitch.tv/activate", left + 12, top + 117, 0xFFB6C5D7, false);
        String state = link.message();
        g.text(font, font.plainSubstrByWidth(state, panelWidth - 24),
            left + 12, top + 169, 0xFFB4E6C3, false);
        g.text(font, "Локальная привязка к UUID. Сервер её не проверяет.",
            left + 12, top + 185, 0xFF9DAFC4, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
}
