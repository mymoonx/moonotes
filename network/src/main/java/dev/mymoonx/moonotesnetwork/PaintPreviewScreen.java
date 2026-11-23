package dev.mymoonx.moonotesnetwork;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Paint controls and a deliberately labelled local demo preview.
 * No unverified Chatterino+ endpoint is queried, and enabling a switch never
 * claims paints are already visible in vanilla chat, TAB, or nameplates.
 */
final class PaintPreviewScreen extends Screen {
    private static final int[] SAMPLE = {
        0xffd08fff, 0xff70baff, 0xff70e3c4, 0xffffce7a
    };
    private static final String[] OPTIONS = {
        "Показывать пейнты", "В чате", "В списке игроков (TAB)",
        "Над персонажами", "Анимация", "Свечение и тени", "Качество"
    };
    private final Screen parent;
    private final PaintPreferences settings = PaintPreferences.local();
    private final Button[] controls = new Button[OPTIONS.length];
    private int left, top, panelWidth;

    PaintPreviewScreen(Screen parent) {
        super(Component.literal("Moonotes · Пейнты"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        panelWidth = Math.max(180, Math.min(490, width - 20));
        left = (width - panelWidth) / 2;
        top = Math.max(8, (height - 380) / 2);
        int rowWidth = panelWidth - 24;
        for (int i = 0; i < OPTIONS.length; i++) {
            final int index = i;
            int y = top + 74 + i * 30;
            controls[i] = addRenderableWidget(Button.builder(Component.literal(label(i)),
                b -> {
                    settings.toggle(index);
                    updateLabels();
                }).bounds(left + 12, y, rowWidth, 22).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> onClose())
            .bounds(left + panelWidth - 94, top + 344, 82, 20).build());
    }
    private String label(int index) { return OPTIONS[index] + ": " + settings.state(index); }
    private void updateLabels() {
        for (int i = 0; i < controls.length; i++) {
            if (controls[i] != null) controls[i].setMessage(Component.literal(label(i)));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(left, top, left + panelWidth, top + 372, 0xF0192638);
        g.outline(left, top, panelWidth, 372, 0xFF4A6683);
        g.text(font, "Пейнты и оформление ников", left + 12, top + 12, 0xFFF0F5FC, false);
        g.text(font, "Предпросмотр локального демо-градиента:", left + 12, top + 31, 0xFFA9B8CB, false);

        String name = TwitchLink.local().linked() ? TwitchLink.local().login() : "mymoon_x";
        int textX = left + 12, y = top + 47;
        long now = System.currentTimeMillis();
        for (int i = 0; i < name.length(); i++) {
            String glyph = String.valueOf(name.charAt(i));
            double t = name.length() <= 1 ? 0 : i / (double)(name.length() - 1);
            int color = PaintGradient.animated(SAMPLE, t, now, settings.animation());
            g.text(font, glyph, textX, y, color, settings.shadows());
            textX += font.width(glyph);
        }
        g.text(font, "PLUS API пока не подключён: пейнты в игре не рисуются.",
            left + 12, top + 300, 0xFFFFCB9E, false);
        g.text(font, "Переключатели сохраняются для будущей интеграции.",
            left + 12, top + 315, 0xFFA9B8CB, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
}
