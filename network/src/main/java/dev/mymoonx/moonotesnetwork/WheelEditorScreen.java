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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compact multi-column editor; thumb dragging and mouse-wheel scrolling are equivalent. */
final class WheelEditorScreen extends Screen {
    private static final int ROW_HEIGHT = 50;
    private final Screen parent;
    private final WheelSettings settings = WheelSettings.local();
    private final LibraryPreferences preferences = LibraryPreferences.local();
    private List<String> draft;
    private List<ChatPalette.Emote> source = List.of(), results = List.of();
    private final Map<String, ChatPalette.Emote> indexed = new HashMap<>();
    private final MoonotesButton[] slots = new MoonotesButton[WheelSettings.SLOTS];
    private EmotePreviews previews;
    private EditBox search, pageName;
    private String query = "", status = "";
    private int selected, row, candidate = -1;
    private int left, contentWidth, slotWidth, slotsX, slotsY, detailsY, listTop, listBottom, visibleRows, columns;
    private boolean draggingThumb;
    private double thumbGrabOffset;
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
        contentWidth = Math.min(695, width - 24);
        left = (width - contentWidth) / 2;
        columns = contentWidth >= 540 ? 3 : contentWidth >= 345 ? 2 : 1;
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
        pageName = addRenderableWidget(new MoonotesEditBox(font, left+57, detailsY,
            Math.max(45,contentWidth-230),20, Component.literal("Название страницы")));
        pageName.setMaxLength(24);
        pageName.setValue(settings.pageTitle());
        button(left+contentWidth-166,detailsY,86,20,"Имя страницы",
            "Сохранить название текущей страницы.",false,b -> {
                if(settings.renamePage(pageName.getValue()))status="Название страницы сохранено";
                else status=settings.error();
                clearFocus();
            });
        button(left + contentWidth - 74, detailsY, 74, 20, "Очистить",
            "Оставить выбранный слот пустым.", false,
            b -> {
                if(!settings.pageGroup().isEmpty()){status="Сначала отвяжи страницу от группы";return;}
                draft=WheelSettings.assign(draft,selected,"");status="";clearFocus();
            });
        search = addRenderableWidget(new MoonotesEditBox(font, left, detailsY + 26, contentWidth, 22,
            Component.literal("Поиск эмота для колеса")));
        search.setHint(Component.literal("Поиск по названию…"));
        search.setMaxLength(80);
        search.setValue(query);
        search.setResponder(value -> { query = value; row = 0; candidate = -1; filter(); });
        listTop = detailsY + 54;
        listBottom = Math.max(listTop + ROW_HEIGHT, height - 73);
        visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        listBottom = listTop + visibleRows * ROW_HEIGHT;
        int pageButtonWidth=(contentWidth-25)/6;
        int pageY=height-57;
        button(left,pageY,pageButtonWidth,22,"◀ Стр.",
            "Предыдущая страница; сначала сохрани изменения в слотах.",false,b -> pageAction(-1));
        button(left+(pageButtonWidth+5),pageY,pageButtonWidth,22,"Стр. ▶",
            "Следующая страница; сначала сохрани изменения в слотах.",false,b -> pageAction(1));
        button(left+2*(pageButtonWidth+5),pageY,pageButtonWidth,22,"+ Страница",
            "Создать пустую страницу. Изменение сохраняется сразу.",false,b -> {
                if(!pageReady())return;
                if(settings.addPage("Страница "+(settings.pageCount()+1)))refreshPage();
                else status=settings.error();
            });
        button(left+3*(pageButtonWidth+5),pageY,pageButtonWidth,22,"Удалить",
            "Удалить эту страницу. Изменение сохраняется сразу.",false,b -> {
                if(!pageReady())return;
                if(settings.removePage())refreshPage();else status=settings.error();
            });
        button(left+4*(pageButtonWidth+5),pageY,pageButtonWidth,22,"Группа →",
            "Связать страницу со следующей группой мини-библиотеки. Состав обновляется сам.",false,b -> {
                if(!pageReady())return;
                if(settings.linkNextGroup())refreshPage();else status=settings.error();
            });
        button(left+5*(pageButtonWidth+5),pageY,
            Math.max(26,contentWidth-5*(pageButtonWidth+5)),22,"Отвязать",
            "Сделать страницу независимой, сохранив восемь текущих эмотов.",false,b -> {
                if(!pageReady())return;
                if(settings.unlinkGroup())refreshPage();else status=settings.error();
            });
        int actionWidth = (contentWidth - 12) / 3;
        button(left, height - 29, actionWidth, 22, "Сброс",
            "Заполнить слоты избранными и недавними эмотами. Сохранение — отдельно.", false,
            b -> {
                var reset = new ArrayList<>(WheelSettings.emptySlots());
                var defaults = EmoteWheelScreen.choose(source, preferences);
                for (int i = 0; i < defaults.size(); i++) reset.set(i, defaults.get(i).key());
                draft = List.copyOf(reset); status = ""; clearFocus();
            });
        button(left + actionWidth + 6, height - 29, actionWidth, 22, "Отмена",
            "Закрыть без сохранения.", false, b -> onClose());
        button(left + (actionWidth + 6) * 2, height - 29, contentWidth - (actionWidth + 6) * 2, 22,
            "Сохранить", "Сохранить все восемь слотов.", true, b -> {
                if (settings.saveSlots(draft)) minecraft.gui.setScreen(parent);
                else status = settings.error();
            });
        refreshSource();
    }

    private MoonotesButton button(int x, int y, int w, int h, String title, String hint, boolean accent,
                                  net.minecraft.client.gui.components.Button.OnPress action) {
        return addRenderableWidget(new MoonotesButton(new MoonotesLayout.Rect(x, y, w, h),
            title, hint, accent, action));
    }

    private WheelGridGeometry grid() {
        return new WheelGridGeometry(left, listTop, contentWidth, listBottom,
            columns, ROW_HEIGHT, visibleRows, results.size());
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
        row = Math.clamp(row, 0, grid().maxRow());
        candidate = Math.clamp(candidate, -1, results.size() - 1);
        lastHovered = -1;
    }

    private boolean pageReady(){
        if(!settings.pageGroup().isEmpty())return true;
        if(!draft.equals(settings.slots())){
            status="Сначала сохрани изменения восьми слотов";
            return false;
        }
        return true;
    }
    private void pageAction(int delta){
        if(!pageReady())return;
        settings.stepPage(delta);
        refreshPage();
    }
    private void refreshPage(){
        draft=settings.slots();selected=0;candidate=-1;
        if(pageName!=null)pageName.setValue(settings.pageTitle());
        status="Страница "+(settings.page()+1)+" из "+settings.pageCount();
        clearFocus();
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
        if(!settings.pageGroup().isEmpty()){status="Связанная страница заполняется из мини-библиотеки";return;}
        draft = WheelSettings.assign(draft, selected, results.get(index).key());
        status = "";
        candidate = index;
        clearFocus();
    }

    private int itemAt(double x, double y) { return grid().itemAt(x, y, row); }

    private String slotName(int slot) {
        ChatPalette.Emote emote = indexed.get(draft.get(slot));
        return emote != null ? emote.name() : draft.get(slot).isEmpty() ? "Пустой слот" : "Каталог выключен";
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    private void moveThumb(double y) {
        row = grid().rowForThumb(y, thumbGrabOffset);
        candidate = -1;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (revision != ChatPalette.revision()) refreshSource();
        g.fill(0, 0, width, height, 0xF0111C2C);
        g.centeredText(font, Component.literal("Настройка колеса · "+(settings.page()+1)+"/"+
            settings.pageCount()), width / 2, 8, 0xFFF0F5FC);
        String hint = status.isBlank()
            ? (settings.pageGroup().isEmpty() ? "Выбери слот и эмот из сетки" :
                "Связь с группой: "+settings.pageGroup()+" — меняй состав в мини-библиотеке")
            : status;
        g.centeredText(font, fit(hint, contentWidth), width / 2, 23,
            status.isBlank() ? 0xFFA6BDD5 : 0xFFFFC596);
        g.text(font, Component.literal(fit("Слот " + (selected + 1) + ": " +
            slotName(selected), contentWidth - 144)), left + 56,
            detailsY + 6, 0xFFF4F0E4, false);

        WheelGridGeometry grid = grid();
        int hovered = itemAt(mx, my);
        if (hovered != lastHovered) { lastHovered = hovered; hoverSince = System.nanoTime(); }
        g.fill(left, listTop, left + contentWidth, listBottom, 0xFF142236);
        int cardWidth = grid.cardWidth();
        for (int visible = 0; visible < visibleRows; visible++) {
            for (int col = 0; col < columns; col++) {
                int index = (row + visible) * columns + col;
                if (index >= results.size()) break;
                ChatPalette.Emote emote = results.get(index);
                int x = grid.cardX(col), y = grid.cardY(visible), h = ROW_HEIGHT - 4;
                boolean assigned = draft.get(selected).equals(emote.key());
                boolean highlighted = index == hovered || index == candidate;
                g.fill(x, y, x + cardWidth, y + h, assigned ? 0xFF304F70 :
                    highlighted ? 0xFF28415A : 0xFF1D3046);
                if (assigned) g.outline(x, y, cardWidth, h, 0xFFB2D3F2);
                previews.draw(g, emote.id(), x + 6, y + 7, 32);
                String name = emote.name();
                int slot = draft.indexOf(emote.key());
                if (slot >= 0) name += "  [" + (slot + 1) + "]";
                int labelWidth = cardWidth - 51;
                g.text(font, Component.literal(fit(name, labelWidth)), x + 44, y + 9,
                    0xFFF3F7FD, false);
                g.text(font, Component.literal(fit(emote.channel(), labelWidth)), x + 44, y + 27,
                    0xFF92AEC8, false);
            }
        }
        if (results.isEmpty())
            g.centeredText(font, source.isEmpty() ? "Нет включённых каталогов" : "Эмоты не найдены",
                width / 2, listTop + 12, 0xFFA6BDD5);

        // Wide drag target, visible rail and a thumb large enough to grab.
        int trackX = grid.trackX(), trackH = grid.trackHeight();
        g.fill(trackX - 3, listTop, trackX + 6, listBottom, 0xFF23394F);
        if (grid.maxRow() > 0) {
            int thumbY = grid.thumbTop(row);
            g.fill(trackX - 2, thumbY, trackX + 5, thumbY + grid.thumbHeight(),
                draggingThumb || grid.onTrack(mx, my) ? 0xFFD3E8FF : 0xFF829FC2);
        }

        super.extractRenderState(g, mx, my, delta);
        for (int i = 0; i < slots.length; i++) {
            int x = slotsX + i * (slotWidth + 4);
            if (i == selected) g.outline(x, slotsY, slotWidth, slotWidth, 0xFFFFD58C);
            ChatPalette.Emote emote = indexed.get(draft.get(i));
            if (emote != null)
                previews.draw(g, emote.id(), x + 7, slotsY + 9, Math.max(8, slotWidth - 14));
            else g.centeredText(font, "+", x + slotWidth / 2,
                slotsY + (slotWidth - font.lineHeight) / 2, 0xFF8FA9C4);
            g.text(font, Component.literal(Integer.toString(i + 1)),
                x + 3, slotsY + 2, 0xFFBBCDE0, false);
        }
        if (hovered >= 0 && System.nanoTime() - hoverSince >= 500_000_000L)
            g.setTooltipForNextFrame(font, font.split(Component.literal(results.get(hovered).name()
                + "\nНажать — назначить в слот " + (selected + 1)), Math.min(300, contentWidth)), mx, my);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        WheelGridGeometry g = grid();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && g.maxRow() > 0
                && g.onTrack(event.x(), event.y())) {
            draggingThumb = true;
            int thumbTop = g.thumbTop(row);
            thumbGrabOffset = event.y() >= thumbTop && event.y() <= thumbTop + g.thumbHeight()
                ? event.y() - thumbTop : g.thumbHeight() / 2.0;
            moveThumb(event.y());
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) return true;
        int index = itemAt(event.x(), event.y());
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && index >= 0) {
            assign(index);
            return true;
        }
        return false;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingThumb) {
            moveThumb(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingThumb) {
            moveThumb(event.y());
            draggingThumb = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= left && x < left + contentWidth && y >= listTop && y < listBottom && vertical != 0) {
            int steps = Math.max(1, (int)Math.ceil(Math.abs(vertical)));
            row = Math.clamp(row - (int)Math.copySign(steps, vertical), 0, grid().maxRow());
            candidate = -1;
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) { onClose(); return true; }
        if (getFocused() == null || getFocused() == search) {
            if ((event.isDown() || event.isUp()) && !results.isEmpty()) {
                candidate = Math.clamp(candidate < 0 ? row * columns :
                    candidate + (event.isDown() ? columns : -columns), 0, results.size() - 1);
                int candidateRow = candidate / columns;
                if (candidateRow < row) row = candidateRow;
                if (candidateRow >= row + visibleRows) row = candidateRow - visibleRows + 1;
                return true;
            }
            if (event.isConfirmation() && candidate >= 0) { assign(candidate); return true; }
        }
        return super.keyPressed(event);
    }

    @Override protected void setInitialFocus() { clearFocus(); }
    @Override public void onClose() { if (minecraft != null) minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) { }
    @Override public void removed() {
        if (previews != null) { previews.close(); previews = null; }
        draggingThumb = false;
        super.removed();
    }
}
