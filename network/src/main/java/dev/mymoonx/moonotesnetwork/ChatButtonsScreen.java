package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag-preview editor for the two chat buttons and current server's global prefix. */
final class ChatButtonsScreen extends Screen {
    private final Screen parent;
    private final ChatButtonPlacement placement=ChatButtonPlacement.local();
    private ChatButtonPlacement.Data draft;
    private EditBox prefix;
    private int dragging=-1;
    private double dx,dy;
    private String status="Перетащи кнопки. Позиции привязаны к краям экрана.";

    ChatButtonsScreen(Screen parent){
        super(Component.literal("Moonotes · Кнопки и чат"));
        this.parent=parent;
        draft=placement.draft();
    }
    @Override protected void init() {
        super.init();
        int x=(width-330)/2;
        prefix=addRenderableWidget(new MoonotesEditBox(font,x,52,330,22,
            Component.literal("Префикс глобального чата")));
        prefix.setMaxLength(4);
        prefix.setValue(WheelSettings.local().prefix());
        addRenderableWidget(new MoonotesButton(new MoonotesLayout.Rect(x,height-28,102,22),
            "Сброс","Вернуть кнопки в стандартное место (не сохраняется до подтверждения).",
            false,b -> {draft=new ChatButtonPlacement.Data();status="Расположение сброшено";}));
        addRenderableWidget(new MoonotesButton(new MoonotesLayout.Rect(x+113,height-28,103,22),
            "Отмена","Закрыть без сохранения.",false,b -> onClose()));
        addRenderableWidget(new MoonotesButton(new MoonotesLayout.Rect(x+228,height-28,102,22),
            "Сохранить","Сохранить обе позиции и глобальный префикс для текущего сервера.",
            true,b -> save()));
    }

    private void save(){
        if(!WheelSettings.validPrefix(prefix.getValue())){
            status="Префикс: от 1 до 4 символов ! # @ $ % & * + . ~ -";
            return;
        }
        var p=ChatButtonPlacement.rect(draft,ChatButtonPlacement.PICKER,width,height);
        var m=ChatButtonPlacement.rect(draft,ChatButtonPlacement.MANAGER,width,height);
        if(p.x()<m.x()+m.width() && m.x()<p.x()+p.width()
            && p.y()<m.y()+m.height() && m.y()<p.y()+p.height()){
            status="Кнопки пересекаются: передвинь одну из них";
            return;
        }
        if(!placement.save(draft)){status="Не удалось сохранить расположение";return;}
        if(!WheelSettings.local().setPrefix(prefix.getValue())){
            status=WheelSettings.local().error();return;
        }
        if(minecraft!=null)minecraft.gui.setScreen(parent);
    }

    private void drawButton(GuiGraphicsExtractor g,ChatOverlayGeometry.Rect rect,String label,
            int mx,int my,boolean picked){
        boolean hover=rect.contains(mx,my);
        g.fill(rect.x(),rect.y(),rect.x()+rect.width(),rect.y()+rect.height(),
            picked?0xFF365B85:hover?0xFF304A69:0xFF23374D);
        g.outline(rect.x(),rect.y(),rect.width(),rect.height(),
            picked?0xFFFFD58C:hover?0xFFA5C8EB:0xFF57789A);
        g.text(font,label,rect.x()+(rect.width()-font.width(label))/2,
            rect.y()+6,0xFFF2F6FF,false);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xC60B1523);
        g.centeredText(font,"Положение кнопок в открытом чате",width/2,10,0xFFF0F5FD);
        g.centeredText(font,"Перетаскивание · Привязка к краям GUI · Esc — отмена",
            width/2,29,0xFFBACEE3);
        int x=(width-330)/2;
        g.text(font,"Символ глобального чата на этом сервере:",x,41,0xFFA6BED8,false);
        g.fill(8,height-72,width-8,height-42,0xA51A293D);
        g.text(font,"Поле чата — пример расположения",18,height-60,0xFFABBCCF,false);
        drawButton(g,ChatButtonPlacement.rect(draft,ChatButtonPlacement.PICKER,width,height),
            "Эмоты",mx,my,dragging==ChatButtonPlacement.PICKER);
        drawButton(g,ChatButtonPlacement.rect(draft,ChatButtonPlacement.MANAGER,width,height),
            "⚙ Moonotes",mx,my,dragging==ChatButtonPlacement.MANAGER);
        g.centeredText(font,font.plainSubstrByWidth(status,Math.max(80,width-20)),
            width/2,height-92,0xFFFFCEA1);
        super.extractRenderState(g,mx,my,delta);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(super.mouseClicked(event,doubleClick))return true;
        if(event.button()!=InputConstants.MOUSE_BUTTON_LEFT)return false;
        for(int which=0;which<2;which++){
            var box=ChatButtonPlacement.rect(draft,which,width,height);
            if(box.contains(event.x(),event.y())){
                dragging=which;
                dx=event.x()-box.x();dy=event.y()-box.y();
                clearFocus();return true;
            }
        }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double x,double y){
        if(dragging>=0){
            ChatButtonPlacement.place(draft,dragging,width,height,event.x()-dx,event.y()-dy);
            return true;
        }
        return super.mouseDragged(event,x,y);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){
        if(dragging>=0){dragging=-1;return true;}
        return super.mouseReleased(event);
    }
    @Override public void onClose(){if(minecraft!=null)minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean isInGameUi(){return true;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){}
}
