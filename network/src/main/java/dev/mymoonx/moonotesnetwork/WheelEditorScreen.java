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
    private MoonotesButton pageButton;
    private EmotePreviews previews;
    private EditBox search, pageName;
    private enum Overlay { NONE, PAGES, ACTIONS, GROUPS, RENAME }
    private Overlay overlay=Overlay.NONE;
    private int popupScroll;
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
        button(left, detailsY, 23, 20, "←",
            "Поменять выбранный слот местами с предыдущим.", false, b -> move(-1));
        button(left+28, detailsY, 23, 20, "→",
            "Поменять выбранный слот местами со следующим.", false, b -> move(1));
        int chooserWidth=Math.max(60,contentWidth-100);
        pageButton=button(left+58,detailsY,chooserWidth,20,pageLabel(),
            "Выбрать страницу колеса или создать новую.",false,b -> toggle(Overlay.PAGES));
        button(left+contentWidth-36,detailsY,36,20,"⋯",
            "Дополнительные действия: группа, название, очистка или удаление.",
            false,b -> toggle(Overlay.ACTIONS));
        pageName=addRenderableWidget(new MoonotesEditBox(font,popupX()+8,popupY()+8,
            popupWidth()-16,20,Component.literal("Название страницы")));
        pageName.setMaxLength(24);
        pageName.setValue(settings.pageTitle());
        pageName.setVisible(false);
        pageName.active=false;
        search = addRenderableWidget(new MoonotesEditBox(font, left, detailsY + 26, contentWidth, 22,
            Component.literal("Поиск эмота для колеса")));
        search.setHint(Component.literal("Поиск по названию…"));
        search.setMaxLength(80);
        search.setValue(query);
        search.setResponder(value -> { query = value; row = 0; candidate = -1; filter(); });
        listTop = detailsY + 54;
        listBottom = Math.max(listTop + ROW_HEIGHT, height - 44);
        visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        listBottom = listTop + visibleRows * ROW_HEIGHT;
        int actionWidth=Math.max(40,(contentWidth-6)/2);
        button(left,height-29,actionWidth,22,"Отмена",
            "Закрыть без сохранения изменений слотов.",false,b -> onClose());
        button(left+actionWidth+6,height-29,contentWidth-actionWidth-6,22,
            "Сохранить","Сохранить выбранные эмоты.",true,b -> {
                if(settings.pageGroup().isEmpty() && settings.saveSlots(draft))
                    minecraft.gui.setScreen(parent);
                else if(!settings.pageGroup().isEmpty())
                    minecraft.gui.setScreen(parent); // group data belongs to mini-library
                else status=settings.error();
            });
        overlay=Overlay.NONE;showRename(false);
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
        if(pageButton!=null)pageButton.setMessage(Component.literal(pageLabel()));
        overlay=Overlay.NONE;popupScroll=0;showRename(false);
        status="Страница "+(settings.page()+1)+" из "+settings.pageCount();
        clearFocus();
    }

    private String pageLabel(){
        return settings.pageTitle()+" · "+(settings.page()+1)+"/"+settings.pageCount();
    }
    private int popupWidth(){return Math.min(240,Math.max(125,contentWidth-10));}
    private int popupX(){return left+contentWidth-popupWidth();}
    private int popupY(){return detailsY+24;}
    private int popupRows(){
        return Math.max(2,Math.min(8,(height-popupY()-58)/23));
    }
    private int popupCount(){
        return switch(overlay){
            case PAGES -> settings.pageCount()+1;
            case ACTIONS -> 8;
            case GROUPS -> preferences.groupNames().size();
            case RENAME -> 1;
            default -> 0;
        };
    }
    private void showRename(boolean show){
        if(pageName==null)return;
        pageName.setVisible(show);pageName.active=show;
        if(show){pageName.setValue(settings.pageTitle());pageName.setFocused(true);}
        else pageName.setFocused(false);
    }
    private void toggle(Overlay which){
        overlay=overlay==which?Overlay.NONE:which;
        popupScroll=0;showRename(overlay==Overlay.RENAME);
        if(overlay!=Overlay.RENAME)clearFocus();
    }
    private String popupLabel(int index){
        return switch(overlay){
            case PAGES -> index==settings.pageCount()?"+ Добавить страницу":
                (index==settings.page()?"✓  ":"")+(index+1)+". "+settings.pageTitle(index);
            case ACTIONS -> switch(index){
                case 0 -> "+ Добавить страницу";
                case 1 -> "Переименовать…";
                case 2 -> "Связать с группой…";
                case 3 -> "Отвязать от группы";
                case 4 -> "Очистить выбранный слот";
                case 5 -> "Заполнить популярными";
                case 6 -> "Переместить слот";
                default -> "Удалить страницу";
            };
            case GROUPS -> preferences.groupNames().get(index);
            default -> "";
        };
    }
    private void openPage(int index){
        if(!pageReady())return;
        if(index==settings.pageCount()){
            if(!settings.addPage("Страница "+(settings.pageCount()+1)))status=settings.error();
        }else settings.setPage(index);
        refreshPage();
    }
    private void popupAction(int index){
        if(overlay==Overlay.PAGES){openPage(index);return;}
        if(overlay==Overlay.GROUPS){
            if(!pageReady())return;
            String group=preferences.groupNames().get(index);
            if(settings.linkGroup(group))refreshPage();else status=settings.error();
            return;
        }
        if(overlay!=Overlay.ACTIONS)return;
        overlay=Overlay.NONE;
        switch(index){
            case 0 -> openPage(settings.pageCount());
            case 1 -> toggle(Overlay.RENAME);
            case 2 -> {popupScroll=0;toggle(Overlay.GROUPS);
                if(preferences.groupNames().isEmpty())status="Сначала создай группу в мини-библиотеке";}
            case 3 -> {if(settings.unlinkGroup())refreshPage();else status=settings.error();}
            case 4 -> {if(settings.pageGroup().isEmpty()){
                    draft=WheelSettings.assign(draft,selected,"");status="Слот очищен (не забудь сохранить)";
                }else status="Связанную страницу редактируй в мини-библиотеке";}
            case 5 -> {if(settings.pageGroup().isEmpty()){
                    var output=new ArrayList<>(WheelSettings.emptySlots());
                    var defaults=EmoteWheelScreen.choose(source,preferences);
                    for(int i=0;i<defaults.size();i++)output.set(i,defaults.get(i).key());
                    draft=List.copyOf(output);status="Автозаполнение — сохрани слоты";
                }else status="Связанная страница использует состав группы";}
            case 6 -> move(1);
            case 7 -> {if(!pageReady())return;
                if(settings.removePage())refreshPage();else status=settings.error();}
        }
    }
    private void popupDraw(GuiGraphicsExtractor g,int mx,int my){
        if(overlay==Overlay.NONE||overlay==Overlay.RENAME)return;
        int n=popupCount(),shown=Math.min(n,popupRows()),x=popupX(),y=popupY(),
            w=popupWidth(),h=shown*23+12;
        g.fill(x+2,y+3,x+w+2,y+h+3,0x64000000);
        g.fill(x,y,x+w,y+h,0xFC152438);
        g.outline(x,y,w,h,0xFF57799E);
        for(int i=0;i<shown;i++){
            int idx=popupScroll+i,yy=y+6+i*23;
            boolean hover=mx>=x+5 && mx<x+w-5 && my>=yy && my<yy+22;
            g.fill(x+5,yy,x+w-5,yy+22,hover?0xFF345374:0xFF21364E);
            g.text(font,Component.literal(fit(popupLabel(idx),w-20)),x+11,yy+7,
                idx==settings.page()&&overlay==Overlay.PAGES?0xFFFFD797:0xFFEDF4FD,false);
        }
        if(popupScroll>0)g.text(font,"▴",x+w-13,y+2,0xFFA5C9EB,false);
        if(popupScroll+shown<n)g.text(font,"▾",x+w-13,y+h-13,0xFFA5C9EB,false);
    }
    private boolean clickPopup(double x,double y){
        if(overlay==Overlay.NONE)return false;
        int px=popupX(),py=popupY(),pw=popupWidth();
        if(overlay==Overlay.RENAME){
            if(x>=px && x<px+pw && y>=py+35 && y<py+59){
                if(settings.renamePage(pageName.getValue()))refreshPage();else status=settings.error();
                return true;
            }
            if(x>=px && x<px+pw && y>=py && y<py+65)return false;
        }else if(x>=px+5 && x<px+pw-5 && y>=py+6 &&
                y<py+6+Math.min(popupCount()-popupScroll,popupRows())*23){
            int index=popupScroll+(int)(y-py-6)/23;
            if(index>=0 && index<popupCount())popupAction(index);
            return true;
        }
        overlay=Overlay.NONE;showRename(false);return true;
    }
    private void drawRenamePopup(GuiGraphicsExtractor g){
        if(overlay!=Overlay.RENAME)return;
        int x=popupX(),y=popupY(),w=popupWidth();
        g.fill(x,y,x+w,y+67,0xFF18283D);
        g.outline(x,y,w,67,0xFF6687A8);
        g.fill(x+8,y+35,x+w-8,y+58,0xFF2E527A);
        g.centeredText(font,"Сохранить название",x+w/2,y+42,0xFFF1F6FD);
    }

    private void move(int direction) {
        if(!settings.pageGroup().isEmpty()){status="Связанную страницу редактируй в мини-библиотеке";return;}
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
        g.centeredText(font,Component.literal("Настройка колеса"),width/2,8,0xFFF0F5FC);
        String hint=status.isBlank()
            ? settings.pageGroup().isEmpty()
                ? "Слот "+(selected+1)+" · "+slotName(selected)+" — выбери эмот ниже"
                : "Страница связана с группой «"+settings.pageGroup()+"»"
            : status;
        g.centeredText(font, fit(hint, contentWidth), width / 2, 23,
            status.isBlank() ? 0xFFA6BDD5 : 0xFFFFC596);


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

        drawRenamePopup(g);
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
        popupDraw(g,mx,my);
        if (hovered >= 0 && overlay==Overlay.NONE && System.nanoTime() - hoverSince >= 500_000_000L)
            g.setTooltipForNextFrame(font, font.split(Component.literal(results.get(hovered).name()
                + "\nНажать — назначить в слот " + (selected + 1)), Math.min(300, contentWidth)), mx, my);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if(overlay!=Overlay.NONE){
            boolean consumed=clickPopup(event.x(),event.y());
            if(consumed)return true;
            // Text input inside the rename overlay keeps normal native focus.
            return super.mouseClicked(event,doubleClick);
        }
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
        if(overlay!=Overlay.NONE&&overlay!=Overlay.RENAME&&vertical!=0){
            int max=Math.max(0,popupCount()-popupRows());
            popupScroll=Math.clamp(popupScroll-(int)Math.signum(vertical),0,max);return true;
        }
        if (x >= left && x < left + contentWidth && y >= listTop && y < listBottom && vertical != 0) {
            int steps = Math.max(1, (int)Math.ceil(Math.abs(vertical)));
            row = Math.clamp(row - (int)Math.copySign(steps, vertical), 0, grid().maxRow());
            candidate = -1;
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if(event.isEscape()){
            if(overlay!=Overlay.NONE){overlay=Overlay.NONE;showRename(false);return true;}
            onClose();return true;
        }
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
