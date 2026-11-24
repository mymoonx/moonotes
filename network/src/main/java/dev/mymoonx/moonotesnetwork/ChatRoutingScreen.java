package dev.mymoonx.moonotesnetwork;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Separate server-specific chat routing from the spatial editor. */
final class ChatRoutingScreen extends Screen {
    private final Screen parent;
    private MoonotesEditBox prefix;
    private String notice="";
    private int x,y,w;
    ChatRoutingScreen(Screen parent){
        super(Component.literal("Moonotes — глобальный чат"));
        this.parent=parent;
    }
    @Override protected void init(){
        super.init();w=Math.min(380,width-28);x=(width-w)/2;y=Math.max(8,height/2-85);
        prefix=addRenderableWidget(new MoonotesEditBox(font,x+12,y+52,w-24,22,
            Component.literal("Символ глобального чата")));
        prefix.setMaxLength(4);
        prefix.setValue(WheelSettings.local().prefix());
        int half=(w-30)/2;
        addRenderableWidget(new MoonotesButton(new MoonotesLayout.Rect(x+12,y+101,half,22),
            "Отмена","Закрыть без изменений.",false,b->onClose()));
        addRenderableWidget(new MoonotesButton(new MoonotesLayout.Rect(x+18+half,y+101,half,22),
            "Сохранить","Сохранить префикс для текущего мира или сервера.",true,b->{
                if(WheelSettings.local().setPrefix(prefix.getValue()))
                    minecraft.gui.setScreen(parent);
                else notice=WheelSettings.local().error();
            }));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0x9A0A1320);
        g.fill(x,y,x+w,y+142,0xF01C2C42);
        g.outline(x,y,w,142,0xFF6484A5);
        g.text(font,"Глобальный чат для этого сервера",x+12,y+13,0xFFF3F7FD,false);
        g.text(font,"Символ добавляется перед сообщением с эмотом.",x+12,y+30,0xFFB3C5D8,false);
        String v=prefix==null?"!":prefix.getValue();
        String example="Пример: "+v+"42   ·   Локальный: 42";
        g.text(font,font.plainSubstrByWidth(example,w-24),x+12,y+80,0xFFBCE2CA,false);
        if(!notice.isEmpty())g.text(font,font.plainSubstrByWidth(notice,w-24),
            x+12,y+130,0xFFFFCB9A,false);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public void onClose(){if(minecraft!=null)minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean isInGameUi(){return true;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){}
}
