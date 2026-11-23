package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Local chat overlay. Every action edits a draft; this class never sends chat packets. */
public final class ChatEmotePicker {
    public enum Click { NONE, OPEN, CLOSE, SEARCH, INSERT, EDIT_GROUP, MANAGER, CONSUMED }
    private enum View { EMOTES, GROUPS, MEMBERSHIP, ADD_EMOTES, CREATE, RENAME, DELETE }
    private record SavedView(int mode,String group,String search,int scroll) { }
    private static final String[] TABS={"Избранное","Недавние","Все","Группы"};
    private final LibraryPreferences preferences;
    private final java.util.function.BooleanSupplier pickerEnabled,suggestionsEnabled;
    private final ChatSuggestionSelection selection=new ChatSuggestionSelection();
    private EmotePreviews previews;
    private int width,height,mode,scroll,rowHighlight=-1;
    private boolean open,draggingScroll;
    private double dragOffset;
    private View view=View.EMOTES,formReturn=View.GROUPS;
    private String search="",group="",lastPrefix="",ignoredPrefix="",paletteStamp="",groupNameInput="",message="";
    private long suggestionRevision=-1,messageUntil,hoverStarted;
    private String hoveredKey="";
    private ChatPalette.Emote membershipTarget;
    private SavedView membershipReturn;
    private List<ChatPalette.Emote> cachedSuggestions=List.of(),visible=List.of();

    public ChatEmotePicker(){this(LibraryPreferences.local(),MoonotesNetworkClient::chatPickerEnabled,MoonotesNetworkClient::chatSuggestionsEnabled);}
    ChatEmotePicker(LibraryPreferences preferences,java.util.function.BooleanSupplier pickerEnabled,java.util.function.BooleanSupplier suggestionsEnabled){
        this.preferences=preferences;this.pickerEnabled=pickerEnabled;this.suggestionsEnabled=suggestionsEnabled;
    }
    public void resize(int width,int height){this.width=width;this.height=height;draggingScroll=false;clampScroll();}
    public boolean isOpen(){return open;}
    public String search(){return search;}
    public String groupName(){return groupNameInput;}
    public boolean namingGroup(){return view==View.CREATE||view==View.RENAME;}
    public boolean fieldVisible(){return searchVisible()||nameFieldVisible();}
    public boolean searchVisible(){return open&&!form();}
    public boolean nameFieldVisible(){return open&&namingGroup();}
    public String inputHint(){return groupRows()?"Найти группу…":"Найти эмот…";}
    public String groupNameHint(){return "Например: Коты";}
    public int searchX(){return geometry().input().x();}
    public int searchY(){return geometry().input().y();}
    public int searchWidth(){return geometry().input().width();}
    public void searchChanged(String value){search=value==null?"":value;scroll=0;rowHighlight=-1;paletteStamp="";}
    public void groupNameChanged(String value){groupNameInput=value==null?"":value;}
    private ChatOverlayGeometry.Layout geometry(){return ChatOverlayGeometry.forScreen(width,height,form());}
    private ChatOverlayGeometry.Rect pickerButton(){return ChatOverlayGeometry.pickerButton(width,height);}
    private ChatOverlayGeometry.Rect managerButton(){return ChatOverlayGeometry.managerButton(width,height);}
    private EmotePreviews previews(){if(previews==null)previews=new EmotePreviews();return previews;}
    public void release(){if(previews!=null){previews.close();previews=null;}draggingScroll=false;}
    private void status(String text){message=text;messageUntil=System.currentTimeMillis()+3600;}
    private void invalidate(){paletteStamp="";rowHighlight=-1;clampScroll();}
    private void resetView(){scroll=0;rowHighlight=-1;paletteStamp="";search="";}
    private boolean groupRows(){return view==View.GROUPS||view==View.MEMBERSHIP;}
    private boolean form(){return namingGroup()||view==View.DELETE;}

    private List<ChatPalette.Emote> suggestions(EditBox draft,boolean searchFocused){
        if(draft==null||searchFocused||open||!suggestionsEnabled.getAsBoolean()||!draft.getHighlighted().isEmpty())return List.of();
        String prefix=ChatTextEdit.activePrefix(draft.getValue(),draft.getCursorPosition());
        long revision=31*ChatPalette.revision()+preferences.revision();
        if(!prefix.equals(lastPrefix)||suggestionRevision!=revision){
            if(!prefix.equals(lastPrefix))ignoredPrefix="";
            lastPrefix=prefix;suggestionRevision=revision;selection.afterCommit();
            cachedSuggestions=prefix.isEmpty()?List.of():ChatPalette.find(ChatPalette.snapshot(),prefix,5,
                preferences::favorite,preferences::lastUsed,"all");
        }
        return prefix.equals(ignoredPrefix)?List.of():cachedSuggestions;
    }
    /** Hide the native emote popup only while this overlay supplies the same non-command suggestions. */
    public boolean ownsSuggestions(EditBox draft){
        if(open)return true;
        if(draft==null||!draft.getHighlighted().isEmpty()||!suggestionsEnabled.getAsBoolean()||ChatTextEdit.activePrefix(draft.getValue(),draft.getCursorPosition()).isEmpty())return false;
        suggestions(draft,false);
        return !cachedSuggestions.isEmpty();
    }
    private ChatOverlayGeometry.Strip strip(EditBox draft,int count){
        return ChatOverlayGeometry.suggestions(width,height,count,draft==null?6:draft.getScreenX(draft.getCursorPosition()));
    }
    private List<ChatPalette.Emote> palette(){
        String stamp=search+"|"+view+"|"+mode+"|"+group+"|"+ChatPalette.revision()+"|"+preferences.revision();
        if(!stamp.equals(paletteStamp)){
            paletteStamp=stamp;
            visible=ChatPalette.find(ChatPalette.snapshot(),search,2000,preferences::favorite,preferences::lastUsed,
                view==View.ADD_EMOTES?"all":switch(mode){case 0->"favorites";case 1->"recent";case 3->"group";default->"all";},key->preferences.inGroup(group,key));
        }
        return visible;
    }
    private List<String> groups(){
        String needle=search.toLowerCase(Locale.ROOT).strip();
        return preferences.groupNames().stream().filter(name->name.toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    private int rowCount(){return form()?0:groupRows()?groups().size():palette().size();}
    private int maxScroll(){return Math.max(0,rowCount()-geometry().rows());}
    private void clampScroll(){scroll=Math.max(0,Math.min(scroll,maxScroll()));}
    private ChatOverlayGeometry.Rect tab(int index){return geometry().tab(index);}
    private ChatOverlayGeometry.Rect footer(int index,int count){return geometry().footer(index,count);}
    private ChatOverlayGeometry.Rect track(){var g=geometry();return new ChatOverlayGeometry.Rect(g.x()+g.width()-12,g.y()+g.bodyTop(),7,g.rows()*ChatOverlayGeometry.ROW-2);}
    private ChatOverlayGeometry.Rect thumb(){
        var t=track();int n=rowCount();int h=n==0?t.height():Math.min(t.height(),Math.max(16,t.height()*geometry().rows()/n));
        int y=t.y()+(maxScroll()==0?0:(t.height()-h)*scroll/maxScroll());
        return new ChatOverlayGeometry.Rect(t.x(),y,t.width(),h);
    }
    private ChatOverlayGeometry.Rect star(ChatOverlayGeometry.Rect row){return ChatOverlayGeometry.favoriteAction(row);}
    private ChatOverlayGeometry.Rect more(ChatOverlayGeometry.Rect row){return ChatOverlayGeometry.moreAction(row);}

    public void draw(GuiGraphicsExtractor g,Font font,int mouseX,int mouseY,EditBox draft,boolean searchFocused){
        if(pickerEnabled.getAsBoolean()){
            button(g,font,pickerButton(),"Эмоты",open,mouseX,mouseY,"picker","Мини-библиотека: избранное, недавние и ваши группы");
        }
        button(g,font,managerButton(),"Moonotes",false,mouseX,mouseY,"manager","Открыть Moonotes: библиотека, каталоги и загрузки");
        if(open&&pickerEnabled.getAsBoolean())drawPopup(g,font,mouseX,mouseY);
        else drawSuggestions(g,font,mouseX,mouseY,draft,searchFocused);
        if(!isHoveredInteractive(mouseX,mouseY))hoveredKey="";
    }
    private boolean isHoveredInteractive(int x,int y){return pickerButton().contains(x,y)||managerButton().contains(x,y)||open&&geometry().bounds().contains(x,y);}
    private void button(GuiGraphicsExtractor g,Font font,ChatOverlayGeometry.Rect r,String label,boolean selected,int mx,int my,String key,String hint){
        boolean hover=r.contains(mx,my);
        g.fill(r.x(),r.y(),r.x()+r.width(),r.y()+r.height(),hover?MoonotesTheme.HOVER:selected?0xFF294360:MoonotesTheme.CARD);
        if(selected)g.fill(r.x(),r.y()+r.height()-2,r.x()+r.width(),r.y()+r.height(),MoonotesTheme.ACCENT);
        if(hover){g.outline(r.x(),r.y(),r.width(),r.height(),MoonotesTheme.ACCENT);g.requestCursor(CursorTypes.POINTING_HAND);tooltip(g,font,key,hint,mx,my);}
        String text=fit(font,label,r.width()-6);
        g.text(font,Component.literal(text),r.x()+(r.width()-font.width(text))/2,r.y()+(r.height()-8)/2,selected||hover?MoonotesTheme.TEXT:MoonotesTheme.MUTED,false);
    }
    private void tooltip(GuiGraphicsExtractor g,Font font,String key,String text,int x,int y){
        if(text.isBlank())return;
        if(!key.equals(hoveredKey)){hoveredKey=key;hoverStarted=System.currentTimeMillis();}
        if(System.currentTimeMillis()-hoverStarted>=450)g.setTooltipForNextFrame(font,font.split(Component.literal(text),Math.min(230,width-20)),x,y);
    }
    private static String fit(Font font,String text,int width){return font.width(text)<=width?text:font.plainSubstrByWidth(text,Math.max(0,width-font.width("…")))+"…";}
    /** Row actions have their own small hover surface; gaps remain inert. */
    private void actionButton(GuiGraphicsExtractor g,Font font,ChatOverlayGeometry.Rect r,String label,boolean selected,int mx,int my,String key,String hint){
        boolean hover=r.contains(mx,my);
        if(hover){g.fill(r.x(),r.y(),r.x()+r.width(),r.y()+r.height(),MoonotesTheme.HOVER);g.outline(r.x(),r.y(),r.width(),r.height(),MoonotesTheme.ACCENT);g.requestCursor(CursorTypes.POINTING_HAND);tooltip(g,font,key,hint,mx,my);}
        g.text(font,Component.literal(label),r.x()+(r.width()-font.width(label))/2,r.y()+(r.height()-8)/2,selected?0xFFF1D28A:hover?MoonotesTheme.TEXT:MoonotesTheme.MUTED,false);
    }
    private void drawPopup(GuiGraphicsExtractor g,Font font,int mx,int my){
        var p=geometry();int x=p.x(),y=p.y(),w=p.width();
        g.fill(x+2,y+3,x+w+2,y+p.height()+3,0x60000000);
        g.fill(x,y,x+w,y+p.height(),MoonotesTheme.PANEL);g.outline(x,y,w,p.height(),MoonotesTheme.LINE);
        String title=switch(view){case MEMBERSHIP->"Группы для «"+membershipTarget.name()+"»";case ADD_EMOTES->"Добавить в «"+group+"»";case CREATE->"Создать группу";case RENAME->"Изменить группу";case DELETE->"Удалить группу?";case GROUPS->"Мои группы";default->mode==3&&!group.isEmpty()?"Эмоты · "+group:"Эмоты";};
        g.text(font,Component.literal(fit(font,title,w-39)),x+8,y+7,MoonotesTheme.TEXT,false);
        button(g,font,new ChatOverlayGeometry.Rect(x+w-23,y+3,18,15),"×",false,mx,my,"close","Закрыть мини-библиотеку");
        if(form()){
            g.fill(x+8,y+23,x+w-8,y+24,MoonotesTheme.LINE);
            if(namingGroup()){
                g.text(font,Component.literal("Название группы"),x+8,p.inputLabelY(),MoonotesTheme.TEXT,false);
                String note=view==View.CREATE&&formReturn==View.MEMBERSHIP?"Выбранный эмот попадёт в новую группу.":view==View.CREATE?"Далее выберите эмоты для новой группы.":"Эмоты в группе сохранятся.";
                g.text(font,Component.literal(fit(font,note,w-16)),x+8,y+68,MoonotesTheme.MUTED,false);
                if(p.height()>115)g.text(font,Component.literal("До 24 символов · сохраняется на устройстве"),x+8,y+81,MoonotesTheme.MUTED,false);
            }else{
                g.text(font,Component.literal(fit(font,"«"+group+"»",w-16)),x+8,y+34,MoonotesTheme.TEXT,false);
                g.text(font,Component.literal("Эмоты и избранное сохранятся."),x+8,y+50,MoonotesTheme.MUTED,false);
            }
            int count=view==View.RENAME?3:2;
            button(g,font,footer(0,count),view==View.DELETE?"Удалить":view==View.CREATE?"Создать":"Сохранить",true,mx,my,"save","Сохранить изменение группы на этом устройстве");
            if(count==3)button(g,font,footer(1,count),"Удалить…",false,mx,my,"delete","Удалить только группу; эмоты не удаляются");
            button(g,font,footer(count-1,count),"Отмена",false,mx,my,"cancel","Вернуться без изменений");
        }else{
            for(int i=0;i<4;i++)button(g,font,tab(i),TABS[i],mode==i,mx,my,"tab"+i,switch(i){case 0->"Ваши избранные эмоты";case 1->"Недавно вставленные эмоты";case 2->"Все эмоты активных каталогов чата";default->"Ваши группы: создать, выбрать и изменить";});
            if(p.hasInputLabel()){
                String label=view==View.MEMBERSHIP?"Отметьте группы для этого эмота":view==View.ADD_EMOTES?"Отметьте эмоты · в группе: "+preferences.groupSize(group):view==View.GROUPS?"Поиск групп · "+preferences.groupNames().size()+"/"+LibraryPreferences.MAX_GROUPS:"Поиск по названию";
                g.text(font,Component.literal(fit(font,label,w-16)),x+8,p.inputLabelY(),MoonotesTheme.MUTED,false);
            }
            clampScroll();int count=rowCount();
            for(int i=0;i<p.rows()&&i+scroll<count;i++)drawRow(g,font,i,i+scroll,mx,my);
            if(count==0&&p.rows()>0){
                String empty=!search.isBlank()?"Ничего не найдено":groupRows()?"Нет групп. Нажмите «Создать группу».":mode==3&&view==View.EMOTES?"Пусто. Нажмите «+ Эмоты» ниже.":mode==0?"Добавляйте эмоты кнопкой ☆":"Пока пусто";
                g.text(font,Component.literal(fit(font,empty,w-20)),x+10,y+p.bodyTop()+6,MoonotesTheme.MUTED,false);
            }
            if(maxScroll()>0&&p.rows()>0){var t=track();var th=thumb();g.fill(t.x(),t.y(),t.x()+t.width(),t.y()+t.height(),MoonotesTheme.BG);g.fill(th.x(),th.y(),th.x()+th.width(),th.y()+th.height(),draggingScroll||t.contains(mx,my)?MoonotesTheme.ACCENT:MoonotesTheme.LINE);if(t.contains(mx,my))g.requestCursor(CursorTypes.POINTING_HAND);}
            if(view==View.GROUPS)button(g,font,footer(0,1),"+ Создать группу",false,mx,my,"create","Задать название, затем выбрать эмоты");
            else if(view==View.MEMBERSHIP){button(g,font,footer(0,2),"+ Создать группу",false,mx,my,"create","Создать группу для выбранного эмота");button(g,font,footer(1,2),"Готово",true,mx,my,"back","Вернуться к эмотам");}
            else if(view==View.ADD_EMOTES){g.text(font,Component.literal("В группе: "+preferences.groupSize(group)),x+9,p.footerY()+5,MoonotesTheme.MUTED,false);button(g,font,footer(1,2),"Готово",true,mx,my,"members-done","Открыть эту группу; выбор уже сохранён");}
            else if(mode==3){button(g,font,footer(0,3),"‹ Группы",false,mx,my,"groups","Выбрать другую группу");button(g,font,footer(1,3),"+ Эмоты",false,mx,my,"add-members","Выбрать эмоты для этой группы");button(g,font,footer(2,3),"Изменить…",false,mx,my,"manage","Переименовать или удалить эту группу");}
            else {String range=count==0?"0 эмотов":(scroll+1)+"–"+Math.min(count,scroll+p.rows())+" из "+count;g.text(font,Component.literal(range),x+9,p.footerY()+5,MoonotesTheme.MUTED,false);}
        }
        if(System.currentTimeMillis()<messageUntil){
            String text=fit(font,message,Math.min(w,width-16)-10);int by=Math.max(2,y-16);
            g.fill(x,by,x+Math.min(w,font.width(text)+10),by+14,MoonotesTheme.BG);g.text(font,Component.literal(text),x+5,by+3,MoonotesTheme.WARN,false);
        }
    }
    private void drawRow(GuiGraphicsExtractor g,Font font,int visibleIndex,int index,int mx,int my){
        var r=geometry().row(visibleIndex);boolean hover=r.contains(mx,my);
        boolean subAction=view==View.EMOTES?mx>=star(r).x()-3:view==View.GROUPS?mx>=more(r).x()-3:false;
        g.fill(r.x(),r.y(),r.x()+r.width(),r.y()+r.height(),MoonotesTheme.CARD);
        if(rowHighlight==index||hover&&!subAction){int right=subAction?r.x()+r.width():view==View.EMOTES?star(r).x()-3:view==View.GROUPS?more(r).x()-3:r.x()+r.width();g.fill(r.x(),r.y(),right,r.y()+r.height(),hover?MoonotesTheme.HOVER:0xFF294360);}
        if(hover&&!subAction)g.requestCursor(CursorTypes.POINTING_HAND);
        if(groupRows()){
            String name=groups().get(index);boolean member=view==View.MEMBERSHIP&&preferences.inGroup(name,membershipTarget.key());
            int reserve=view==View.GROUPS?62:36;
            g.text(font,Component.literal(fit(font,name,r.width()-reserve)),r.x()+8,r.y()+8,MoonotesTheme.TEXT,false);
            if(view==View.GROUPS){String count=String.valueOf(preferences.groupSize(name));g.text(font,Component.literal(count),more(r).x()-font.width(count)-7,r.y()+8,MoonotesTheme.MUTED,false);actionButton(g,font,more(r),"⋯",false,mx,my,"manage:"+name,"Переименовать или удалить группу «"+name+"»");}
            else{if(member)g.fill(r.x(),r.y()+3,r.x()+2,r.y()+r.height()-3,MoonotesTheme.GOOD);actionButton(g,font,more(r),member?"✓":"+",member,mx,my,"member:"+name,(member?"Убрать из группы «":"Добавить в группу «")+name+"»");}
            if(hover&&!more(r).contains(mx,my))tooltip(g,font,"group:"+name,view==View.MEMBERSHIP?(member?"Убрать из группы «":"Добавить в группу «")+name+"»":"Открыть группу «"+name+"»",mx,my);
        }else{
            var emote=palette().get(index);previews().draw(g,emote.id(),r.x()+3,r.y()+2,19);
            g.text(font,Component.literal(fit(font,emote.name(),r.width()-(view==View.ADD_EMOTES?60:80))),r.x()+27,r.y()+8,MoonotesTheme.TEXT,false);
            if(view==View.ADD_EMOTES){boolean member=preferences.inGroup(group,emote.key());if(member)g.fill(r.x(),r.y()+3,r.x()+2,r.y()+r.height()-3,MoonotesTheme.GOOD);actionButton(g,font,more(r),member?"✓":"+",member,mx,my,"member-emote:"+emote.key(),member?"Убрать из этой группы":"Добавить в эту группу");if(hover&&!more(r).contains(mx,my))tooltip(g,font,"select:"+emote.key(),emote.name()+"\n"+(member?"Убрать из «":"Добавить в «")+group+"»",mx,my);}
            else{
                boolean favorite=preferences.favorite(emote.key());
                actionButton(g,font,star(r),favorite?"★":"☆",favorite,mx,my,"star:"+emote.key(),favorite?"Убрать из избранного":"Добавить в избранное");
                actionButton(g,font,more(r),"⋯",false,mx,my,"more:"+emote.key(),"Выбрать группы для этого эмота");
                if(hover&&!subAction)tooltip(g,font,"emote:"+emote.key(),emote.name()+" · "+emote.channel()+"\nВставить в чат · без отправки.\nПравая кнопка: выбрать группы.",mx,my);
            }
        }
    }
    private void drawSuggestions(GuiGraphicsExtractor g,Font font,int mx,int my,EditBox draft,boolean focused){
        var options=suggestions(draft,focused);var layout=strip(draft,options.size());int count=layout.count();if(count==0)return;
        int hover=layout.hit(mx,my),selected=selection.index();
        for(int i=0;i<count;i++){
            var r=layout.cell(i);boolean hovered=i==hover,active=i==selected;
            g.fill(r.x(),r.y(),r.x()+r.width(),r.y()+r.height(),hovered?MoonotesTheme.HOVER:MoonotesTheme.PANEL);
            if(active||hovered)g.outline(r.x(),r.y(),r.width(),r.height(),active?MoonotesTheme.ACCENT:MoonotesTheme.LINE);
            previews().draw(g,options.get(i).id(),r.x()+3,r.y()+2,21);
            if(hovered)g.requestCursor(CursorTypes.POINTING_HAND);
        }
        int label=hover>=0?hover:selected;
        if(label>=0&&label<count){
            String text=fit(font,options.get(label).name(),Math.min(170,width-16));int tw=font.width(text),cx=layout.cell(label).x()+13;
            int tx=Math.max(4,Math.min(width-tw-9,cx-tw/2-4)),ty=layout.y()-15;
            g.fill(tx,ty,tx+tw+8,ty+13,MoonotesTheme.PANEL);g.text(font,Component.literal(text),tx+4,ty+3,MoonotesTheme.TEXT,false);
        }
    }

    public Click click(double x,double y,int button,EditBox draft,boolean searchFocused){
        if(button!=InputConstants.MOUSE_BUTTON_LEFT&&button!=InputConstants.MOUSE_BUTTON_RIGHT)return Click.NONE;
        if(button==InputConstants.MOUSE_BUTTON_LEFT&&managerButton().contains(x,y)){open=false;return Click.MANAGER;}
        if(pickerEnabled.getAsBoolean()&&button==InputConstants.MOUSE_BUTTON_LEFT&&pickerButton().contains(x,y)){
            open=!open;draggingScroll=false;ignoredPrefix="";selection.reset();return open?Click.OPEN:Click.CLOSE;
        }
        if(open){
            var p=geometry();
            if(!p.bounds().contains(x,y)){open=false;draggingScroll=false;return Click.CLOSE;}
            if(new ChatOverlayGeometry.Rect(p.x()+p.width()-23,p.y()+3,18,15).contains(x,y)){open=false;return Click.CLOSE;}
            for(int i=0;!form()&&i<4;i++)if(tab(i).contains(x,y)){mode=i;view=i==3?View.GROUPS:View.EMOTES;resetView();return Click.EDIT_GROUP;}
            if(form())return clickForm(x,y);
            if(new ChatOverlayGeometry.Rect(searchX(),searchY(),searchWidth(),18).contains(x,y))return Click.SEARCH;
            if(maxScroll()>0&&track().contains(x,y)){
                draggingScroll=true;var t=thumb();dragOffset=t.contains(x,y)?y-t.y():t.height()/2.0;drag(x,y);return Click.CONSUMED;
            }
            if(view==View.GROUPS&&footer(0,1).contains(x,y)){beginForm(View.CREATE);return Click.EDIT_GROUP;}
            if(view==View.MEMBERSHIP){
                if(footer(0,2).contains(x,y)){beginForm(View.CREATE);return Click.EDIT_GROUP;}
                if(footer(1,2).contains(x,y)){finishMembership();return Click.EDIT_GROUP;}
            }else if(view==View.ADD_EMOTES){
                if(footer(1,2).contains(x,y)){finishAddingEmotes();return Click.EDIT_GROUP;}
            }else if(mode==3&&view==View.EMOTES){
                if(footer(0,3).contains(x,y)){view=View.GROUPS;resetView();return Click.EDIT_GROUP;}
                if(footer(1,3).contains(x,y)){view=View.ADD_EMOTES;resetView();return Click.EDIT_GROUP;}
                if(footer(2,3).contains(x,y)){beginForm(View.RENAME);return Click.EDIT_GROUP;}
            }
            for(int i=0;i<p.rows();i++)if(p.row(i).contains(x,y)&&i+scroll<rowCount()){
                int index=i+scroll;
                if(groupRows()){
                    if(view==View.GROUPS&&more(p.row(i)).contains(x,y)){group=groups().get(index);beginForm(View.RENAME);return Click.EDIT_GROUP;}
                    return activateGroup(index);
                }
                var emote=palette().get(index);
                if(view==View.ADD_EMOTES){preferences.toggleGroup(group,emote.key());paletteStamp="";return Click.CONSUMED;}
                if(button==InputConstants.MOUSE_BUTTON_RIGHT||more(p.row(i)).contains(x,y)){beginMembership(emote);return Click.EDIT_GROUP;}
                if(star(p.row(i)).contains(x,y)){preferences.toggle(emote.key());invalidate();return Click.CONSUMED;}
                if(x>=star(p.row(i)).x()-3)return Click.CONSUMED;
                if(insert(draft,emote,false)){open=false;return Click.INSERT;}return Click.CONSUMED;
            }
            return Click.CONSUMED;
        }
        if(button==InputConstants.MOUSE_BUTTON_LEFT){
            var options=suggestions(draft,searchFocused);var layout=strip(draft,options.size());int hit=layout.hit(x,y);
            if(hit>=0&&hit<options.size()){if(insert(draft,options.get(hit),true))return Click.INSERT;return Click.CONSUMED;}
        }
        return Click.NONE;
    }
    private Click activateGroup(int index){
        String name=groups().get(index);
        if(view==View.MEMBERSHIP){preferences.toggleGroup(name,membershipTarget.key());return Click.CONSUMED;}
        group=name;view=View.EMOTES;resetView();return Click.EDIT_GROUP;
    }
    private void beginMembership(ChatPalette.Emote emote){
        membershipReturn=new SavedView(mode,group,search,scroll);membershipTarget=emote;mode=3;view=View.MEMBERSHIP;resetView();
    }
    private void finishMembership(){
        if(membershipReturn!=null){mode=membershipReturn.mode();group=membershipReturn.group();search=membershipReturn.search();scroll=membershipReturn.scroll();}
        view=View.EMOTES;membershipTarget=null;membershipReturn=null;invalidate();
    }
    private void beginForm(View form){formReturn=view;view=form;groupNameInput=form==View.RENAME?group:"";rowHighlight=-1;}
    private void cancelForm(){view=formReturn;groupNameInput="";invalidate();}
    private void finishAddingEmotes(){view=View.EMOTES;resetView();}
    private Click clickForm(double x,double y){
        if(fieldVisible()&&new ChatOverlayGeometry.Rect(searchX(),searchY(),searchWidth(),18).contains(x,y))return Click.SEARCH;
        int count=view==View.RENAME?3:2;
        if(footer(0,count).contains(x,y)){saveForm();return Click.EDIT_GROUP;}
        if(count==3&&footer(1,3).contains(x,y)){view=View.DELETE;return Click.EDIT_GROUP;}
        if(footer(count-1,count).contains(x,y)){cancelForm();return Click.EDIT_GROUP;}
        return Click.CONSUMED;
    }
    private void saveForm(){
        if(view==View.DELETE){preferences.removeGroup(group);group="";view=View.GROUPS;resetView();return;}
        String name=groupNameInput.strip();String error=view==View.CREATE?preferences.createGroup(name):preferences.renameGroup(group,name);
        if(!error.isEmpty()){status(error);return;}
        if(view==View.CREATE&&formReturn==View.MEMBERSHIP&&membershipTarget!=null){
            preferences.toggleGroup(name,membershipTarget.key());view=View.MEMBERSHIP;search="";invalidate();status("Эмот добавлен в «"+name+"»");
        }else if(view==View.CREATE){mode=3;group=name;view=View.ADD_EMOTES;resetView();}
        else{group=name;view=formReturn;invalidate();}
        groupNameInput="";
    }
    private boolean insert(EditBox input,ChatPalette.Emote emote,boolean replace){
        if(input==null)return false;
        try{
            boolean required=StreamotesBridge.chatText(emote.name()).startsWith(":");
            String value=input.getValue();int cursor=input.getCursorPosition();
            // Keep native selection replacement in the draft, without touching the network.
            int selectedLength=input.getHighlighted().length();
            if(selectedLength>0){
                int other=((dev.mymoonx.moonotesnetwork.mixin.EditBoxSelectionAccessor)input).moonotes$highlightPosition();
                int from=Math.min(cursor,other),to=Math.max(cursor,other);value=value.substring(0,from)+value.substring(to);cursor=from;replace=false;
            }
            ChatTextEdit.Result edit=ChatTextEdit.insert(value,cursor,emote.name(),replace,required);
            input.setValue(edit.text());input.setCursorPosition(edit.cursor());input.setHighlightPos(edit.cursor());
            preferences.used(emote.key());ignoredPrefix="";lastPrefix="";paletteStamp="";return true;
        }catch(IllegalArgumentException error){status(error.getMessage()==null?"Не удалось вставить эмот":error.getMessage());return false;}
    }
    public boolean scroll(double x,double y,double amount){
        if(!open||amount==0||form()||!geometry().bounds().contains(x,y))return false;
        scroll=Math.max(0,Math.min(maxScroll(),scroll+(amount>0?-1:1)*2));rowHighlight=-1;return true;
    }
    public boolean drag(double x,double y){
        if(!open||!draggingScroll)return false;
        var t=track();var th=thumb();int travel=t.height()-th.height();
        scroll=travel<=0?0:Math.max(0,Math.min(maxScroll(),(int)Math.round((y-t.y()-dragOffset)*maxScroll()/travel)));return true;
    }
    public boolean releaseMouse(){boolean was=draggingScroll;draggingScroll=false;return was;}
    public void keyReleased(KeyEvent event){selection.keyReleased(event);}
    public boolean consumeCharacter(int codepoint){return selection.consumeCharacter(codepoint);}
    public boolean key(KeyEvent key,EditBox input,boolean searchFocused){
        if(open){
            if(key.isEscape()){
                if(form())cancelForm();else if(view==View.MEMBERSHIP)finishMembership();else if(view==View.ADD_EMOTES)finishAddingEmotes();else{open=false;draggingScroll=false;}return true;
            }
            if(key.key()==InputConstants.KEY_RETURN||key.key()==InputConstants.KEY_NUMPADENTER){
                if(form())saveForm();else if(rowHighlight>=0&&rowHighlight<rowCount()){
                    if(groupRows())activateGroup(rowHighlight);else if(view==View.ADD_EMOTES){preferences.toggleGroup(group,palette().get(rowHighlight).key());paletteStamp="";}else if(insert(input,palette().get(rowHighlight),false))open=false;
                }return true;
            }
            if(!form()&&(key.isDown()||key.isUp())&&!key.hasControlDown()&&!key.hasAltDown()){
                int n=rowCount();if(n>0){rowHighlight=rowHighlight<0?(key.isUp()?n-1:0):Math.floorMod(rowHighlight+(key.isUp()?-1:1),n);scroll=Math.max(0,Math.min(maxScroll(),Math.min(rowHighlight,Math.max(scroll,rowHighlight-geometry().rows()+1))));}return true;
            }
            return false;
        }
        if(key.key()==InputConstants.KEY_TAB)ignoredPrefix="";
        var options=suggestions(input,searchFocused);int count=strip(input,options.size()).count();
        var action=selection.key(key,count);
        if(action==ChatSuggestionSelection.Action.DISMISS){ignoredPrefix=lastPrefix;return true;}
        if(action==ChatSuggestionSelection.Action.MOVE)return true;
        if(action==ChatSuggestionSelection.Action.COMMIT){insert(input,options.get(selection.index()),true);selection.afterCommit();return true;}
        return false;
    }
}
