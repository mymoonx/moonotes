package dev.mymoonx.moonotesnetwork;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;

/** Dedicated tabs avoid mixing rare data/recovery actions with ordinary diagnostics. */
final class MoonotesSupportScreen extends Screen {
    private enum Section { CHECK, QUALITY, DATA, UPDATES }
    private static final String[] LABELS={"Проверка","Качество","Данные","Обновления"};
    private final Screen parent;
    private final SupportPreferences settings=SupportPreferences.local();
    private Section section=Section.CHECK;
    private String status="",report="";
    private boolean confirmRestore;
    private int left,top,panelW,panelH,inner;
    MoonotesSupportScreen(Screen parent){this(parent,Section.CHECK);}
    private MoonotesSupportScreen(Screen parent,Section section){
        super(Component.literal("Moonotes — поддержка"));
        this.parent=parent;this.section=section;
    }
    @Override protected void init(){
        super.init();
        panelW=Math.min(500,width-20);
        left=(width-panelW)/2;
        panelH=Math.min(306,height-12);
        top=Math.max(6,(height-panelH)/2);
        inner=panelW-24;
        int x=left+MoonotesUiMetrics.PANEL_PADDING, tabsY=top+34;
        int tabWidth=Math.max(20,(inner-12)/4);
        for(int i=0;i<4;i++){
            final Section which=Section.values()[i];
            addRenderableWidget(new MoonotesButton(
                new MoonotesLayout.Rect(x+i*(tabWidth+4),tabsY,
                    i==3?inner-i*(tabWidth+4):tabWidth,22),
                LABELS[i],LABELS[i],section==which,b -> {
                    if(section!=which && minecraft!=null)
                        minecraft.gui.setScreen(new MoonotesSupportScreen(parent,which));
                }));
        }
        switch(section){
            case CHECK -> buildCheck(x);
            case QUALITY -> buildQuality(x);
            case DATA -> buildData(x);
            case UPDATES -> buildUpdates(x);
        }
        add(left+panelW-96,top+panelH-29,84,"Назад",false,this::onClose);
    }
    private void add(int x,int y,int w,String text,boolean primary,Runnable action){
        addRenderableWidget(new MoonotesButton(new MoonotesLayout.Rect(x,y,Math.max(18,w),MoonotesUiMetrics.ACTION_HEIGHT),
            text,text,primary,b->action.run()));
    }
    private void paired(int x,int y,String a,Runnable fa,String b,Runnable fb){
        int half=(inner-8)/2;
        add(x,y,half,a,false,fa);
        add(x+half+8,y,half,b,false,fb);
    }
    private void buildCheck(int x){
        paired(x,top+69,"Самодиагностика",()->{
            try{report=MoonotesHealthCheck.run();status="Проверка готова. Можно скопировать отчёт.";}
            catch(Exception ex){status="Не удалось выполнить самопроверку";}
        },"Копировать отчёт",()->{
            if(report.isBlank())report=MoonotesHealthCheck.compactReport();
            if(minecraft!=null)minecraft.keyboardHandler.setClipboard(report);
            status="Отчёт скопирован. Просмотри текст перед отправкой.";
        });
        add(x,top+101,inner,"Сообщить об ошибке на GitHub",true,()->{
            if(!settings.allowManualReports()){status="Сначала разреши ручную отправку в разделе Данные";return;}
            if(report.isBlank())report=MoonotesHealthCheck.compactReport();
            if(minecraft!=null)minecraft.keyboardHandler.setClipboard(report);
            try{
                URI url=URI.create("https://github.com/mymoonx/moonotes/issues/new?template=bug_report.yml");
                if(Desktop.isDesktopSupported()&&Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)){
                    Desktop.getDesktop().browse(url);
                    status="GitHub открыт. Технический отчёт в буфере обмена.";
                }else status="Отчёт в буфере. Открой GitHub Issues в браузере.";
            }catch(Exception ex){status="Не удалось открыть браузер. Отчёт скопирован.";}
        });
    }
    private void buildQuality(int x){
        add(x,top+69,inner,"Режим качества: "+settings.performance().label,false,()->{
            settings.cyclePerformance();
            SignEmoteRenderer.reset();BookEmoteRenderer.disconnect();
            status="Режим: "+settings.performance().label+". Ограничения обновлены.";
            minecraft.gui.setScreen(new MoonotesSupportScreen(parent,Section.QUALITY));
        });
    }
    private Path config(){return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();}
    private void buildData(int x){
        paired(x,top+69,"Создать резервную копию",()->{
            try{Path p=LocalBackup.backup(config());status="Сохранено: "+p.getFileName();}
            catch(Exception ex){status="Не удалось сохранить ZIP-копию";}
        },"Восстановить последнюю",()->{
            if(!confirmRestore){
                confirmRestore=true;status="Нажми ещё раз для восстановления. Нужен перезапуск игры.";
                return;
            }
            confirmRestore=false;
            try{
                Path p=LocalBackup.newest(config());
                if(p==null){status="Резервной копии нет.";return;}
                int n=LocalBackup.restore(config(),p);
                status="Восстановлено "+n+" файлов. Перезапусти Minecraft.";
            }catch(Exception ex){status="Не удалось восстановить настройки.";}
        });
        add(x,top+101,inner,"Локальная статистика: "+
            (settings.telemetryOptIn()?"Включена":"Выключена"),false,()->{
                settings.toggleTelemetry();
                minecraft.gui.setScreen(new MoonotesSupportScreen(parent,Section.DATA));
            });
        add(x,top+133,inner,"Ручная отправка отчёта: "+
            (settings.allowManualReports()?"Разрешена":"Выключена"),false,()->{
                settings.toggleManualReports();
                minecraft.gui.setScreen(new MoonotesSupportScreen(parent,Section.DATA));
            });
    }
    private void buildUpdates(int x){
        paired(x,top+69,"Канал: "+settings.updateChannel().label,()->{
            settings.cycleUpdateChannel();
            minecraft.gui.setScreen(new MoonotesSupportScreen(parent,Section.UPDATES));
        },"Режим: "+settings.updateMode().label,()->{
            settings.cycleUpdateMode();
            minecraft.gui.setScreen(new MoonotesSupportScreen(parent,Section.UPDATES));
        });
        paired(x,top+100,"Проверить обновления",MoonotesUpdateCheck::check,
            "Скачать проверенный JAR",()->{
                if(!MoonotesUpdateCheck.isStaged())MoonotesUpdateCheck.stage();
                else status="Обновление уже загружено и проверено.";
            });
        paired(x,top+131,"Установить после выхода",()->{
                if(!MoonotesUpdateCheck.planInstall())
                    status="Сначала скачай и проверь обновление.";
            },"Откатить обновление",()->{
                if(!MoonotesUpdateCheck.planRollback())
                    status="Нет подходящей резервной версии.";
            });
        if(panelH>=265)
            add(x,top+162,inner,"Отменить запланированную установку",false,
                MoonotesUpdateCheck::cancelPending);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(left,top,left+panelW,top+panelH,0xF019293E);
        g.outline(left,top,panelW,panelH,0xFF6584A5);
        g.text(font,"Moonotes  ·  "+LABELS[section.ordinal()],
            left+12,top+11,0xFFF2F6FC,false);
        String tip=switch(section){
            case CHECK->"Локальная самопроверка и отчёт только после твоего действия.";
            case QUALITY->"Баланс качества анимаций и производительности Minecraft.";
            case DATA->"Локальные настройки, копии и разрешения на отправку.";
            case UPDATES->"Только официальные GitHub Releases. Замена JAR после закрытия игры.";
        };
        int y=top+64;
        g.text(font,font.plainSubstrByWidth(tip,inner),left+12,y,0xFFAEC6DF,false);
        switch(section){
            case CHECK -> {
                String[] lines=report.isBlank()?new String[]{"Данные никнейма и чата не включаются.",
                    "Краш-логи самостоятельно проверяются перед публикацией."}
                    : report.split("\\R");
                for(int i=0;i<Math.min(3,lines.length);i++)
                    g.text(font,font.plainSubstrByWidth(lines[i],inner),
                        left+12,top+137+i*14,0xFFB7D0E6,false);
            }
            case QUALITY -> {
                String[] lines={"Экономичный: до 3 активных анимаций, 32 кадра.",
                    "Сбалансированный: до 8 анимаций, 128 кадров.",
                    "Высокое качество: до 12 анимаций, 256 кадров.",
                    "Остальной графикой Minecraft управляют её настройки."};
                for(int i=0;i<lines.length;i++)g.text(font,font.plainSubstrByWidth(lines[i],inner),
                    left+12,top+105+i*18,0xFFB3CDE5,false);
            }
            case DATA -> {
                g.text(font,"Сохраняем только настройки Moonotes, без токенов и логов.",
                    left+12,top+174,0xFFAEC4DA,false);
                g.text(font,"Автоматическая передача телеметрии не подключена.",
                    left+12,top+192,0xFFBCE1CB,false);
            }
            case UPDATES -> {
                String res=MoonotesUpdateCheck.result();
                int base=top+(panelH<265?163:195);
                g.text(font,font.plainSubstrByWidth(res,inner),
                    left+12,base,0xFFBCE2CA,false);
                g.text(font,font.plainSubstrByWidth("Сейчас: "+MoonotesUpdateCheck.currentVersion()+
                    (MoonotesUpdateCheck.isPending()?" · ожидает выхода":""),inner),
                    left+12,base+16,0xFFB4C9DE,false);
                if(panelH>=280)g.text(font,"Старый JAR сохраняется. Требуется SHA-256.",
                    left+12,base+32,0xFFB4C9DE,false);
            }
        }
        if(!status.isBlank())
            g.text(font,font.plainSubstrByWidth(status,Math.max(50,inner-95)),
                left+12,top+panelH-26,0xFFFFD39E,false);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public void onClose(){if(minecraft!=null)minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean isInGameUi(){return true;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){}
}
