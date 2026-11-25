package dev.mymoonx.moonotesnetwork;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Local, user-editable list of command prefixes that allow emotes in their body. */
final class ChatCommandRulesScreen extends Screen {
    private static final int ROW_H = 27;
    private final Screen parent;
    private final ChatCommandRules rules = ChatCommandRules.local();
    private ChatCommandRules.Mode newMode = ChatCommandRules.Mode.RECIPIENT;
    private MoonotesEditBox entry;
    private String draft = "";
    private String notice = "";
    private boolean confirmReset;
    private int x, y, w, h, listTop, rows, scroll;

    ChatCommandRulesScreen(Screen parent) {
        super(Component.literal("Moonotes — команды для эмотов"));
        this.parent = parent;
    }

    @Override protected void init() {
        super.init();
        w = Math.min(512, width - 16);
        h = Math.min(342, height - 12);
        x = (width - w) / 2;
        y = (height - h) / 2;
        int toggleW = Math.min(102, Math.max(78, w / 3));
        int addW = 60;
        int editW = w - 24 - toggleW - addW - 12;
        entry = addRenderableWidget(new MoonotesEditBox(font,
            x + 12, y + 46, editW, 22, Component.literal("Команда")));
        entry.setMaxLength(49);
        entry.setHint(Component.literal("/msg или /plugin:msg"));
        entry.setValue(draft);
        entry.setResponder(value -> draft = value);
        addButton(x + 18 + editW, y + 46, toggleW, 22,
            modeLabel(newMode), "После ника — текст или сразу текст после команды",
            false, button -> {
                newMode = newMode == ChatCommandRules.Mode.RECIPIENT
                    ? ChatCommandRules.Mode.MESSAGE : ChatCommandRules.Mode.RECIPIENT;
                button.setMessage(Component.literal(modeLabel(newMode)));
            });
        addButton(x + 24 + editW + toggleW, y + 46, addW, 22,
            "Добавить", "Добавить новую команду или обновить её тип", true,
            button -> {
                if (rules.put(draft, newMode)) {
                    notice = "Команда добавлена";
                    draft = "";
                    confirmReset = false;
                    rebuild();
                } else notice = rules.error();
            });

        listTop = y + 101;
        rows = Math.max(1, (h - 145) / ROW_H);
        var commands = rules.list();
        scroll = Math.clamp(scroll, 0, Math.max(0, commands.size() - rows));
        addButton(x + w - 71, y + 76, 26, 20, "▲",
            "Прокрутить список вверх", false, button -> move(-1));
        addButton(x + w - 39, y + 76, 26, 20, "▼",
            "Прокрутить список вниз", false, button -> move(1));

        for (int i = 0; i < rows && scroll + i < commands.size(); i++) {
            var rule = commands.get(scroll + i);
            int ry = listTop + i * ROW_H;
            int modeX = x + w - 140;
            addButton(modeX, ry + 2, 98, 22, modeLabel(rule.mode()),
                "Переключить: команда с получателем / команда без получателя",
                false, button -> {
                    ChatCommandRules.Mode next = rule.mode() == ChatCommandRules.Mode.RECIPIENT
                        ? ChatCommandRules.Mode.MESSAGE : ChatCommandRules.Mode.RECIPIENT;
                    if (rules.put(rule.name(), next)) {
                        notice = "Тип команды изменён";
                        rebuild();
                    } else notice = rules.error();
                });
            addButton(x + w - 36, ry + 2, 24, 22, "×",
                "Убрать команду из списка разрешённых", false, button -> {
                    if (rules.remove(rule.name())) {
                        notice = "Команда удалена";
                        rebuild();
                    } else notice = rules.error();
                });
        }

        int footer = y + h - 32;
        int resetW = Math.min(167, (w - 30) / 2);
        addButton(x + 12, footer, resetW, 22,
            confirmReset ? "Подтвердить сброс" : "Стандартный список",
            "Вернуть стандартные команды /msg, /tell, /w, /whisper, /pm, /r, /reply",
            false, button -> {
                if (!confirmReset) {
                    confirmReset = true;
                    notice = "Нажми ещё раз, чтобы вернуть стандартный список";
                    rebuild();
                } else {
                    rules.reset();
                    confirmReset = false;
                    notice = rules.error().isEmpty() ? "Стандартные команды восстановлены" : rules.error();
                    scroll = 0;
                    rebuild();
                }
            });
        addButton(x + w - 104, footer, 92, 22, "Готово",
            "Вернуться в настройки Moonotes", true, button -> onClose());
    }

    private MoonotesButton addButton(int bx, int by, int bw, int bh, String label,
            String tooltip, boolean accent,
            net.minecraft.client.gui.components.Button.OnPress action) {
        return addRenderableWidget(new MoonotesButton(
            new MoonotesLayout.Rect(bx, by, bw, bh), label, tooltip, accent, action));
    }

    private static String modeLabel(ChatCommandRules.Mode mode) {
        return mode == ChatCommandRules.Mode.RECIPIENT ? "Ник + текст" : "Только текст";
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void move(int direction) {
        scroll = Math.clamp(scroll + direction, 0, Math.max(0, rules.list().size() - rows));
        confirmReset = false;
        rebuild();
    }

    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (mx >= x && mx < x + w && my >= listTop && my < listTop + rows * ROW_H
                && vertical != 0) {
            move(vertical > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xB00A1320);
        g.fill(x, y, x + w, y + h, 0xF01C2C42);
        g.outline(x, y, w, h, 0xFF6484A5);
        g.text(font, "Команды для подбора эмотов", x + 12, y + 10, 0xFFF3F7FD, false);
        g.text(font, Component.literal(font.plainSubstrByWidth(
            "Moonotes пропускает команду и ник, а эмоты ищет только в тексте.",
            w - 24)), x + 12, y + 27, 0xFFB3C5D8, false);
        g.text(font, "Разрешённые команды: " + rules.list().size() + " / 32",
            x + 12, y + 80, 0xFFACC9E7, false);

        var commands = rules.list();
        for (int i = 0; i < rows && scroll + i < commands.size(); i++) {
            var rule = commands.get(scroll + i);
            int ry = listTop + i * ROW_H;
            g.fill(x + 12, ry, x + w - 12, ry + ROW_H - 2,
                i % 2 == 0 ? 0xFF20344C : 0xFF1B2E43);
            g.text(font, Component.literal(font.plainSubstrByWidth(
                "/" + rule.name(), Math.max(44, w - 180))),
                x + 19, ry + 8, 0xFFF2F7FF, false);
        }
        if (commands.isEmpty()) {
            g.text(font, "Нет команд — подсказки работают только в обычном чате.",
                x + 17, listTop + 9, 0xFFB3C5D8, false);
        }
        if (!notice.isBlank()) {
            g.text(font, Component.literal(font.plainSubstrByWidth(notice, w - 25)),
                x + 12, y + h - 46, 0xFFFFCD99, false);
        }
        super.extractRenderState(g, mx, my, delta);
    }

    @Override public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) { }
}
