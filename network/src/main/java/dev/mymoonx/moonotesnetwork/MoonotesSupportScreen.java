package dev.mymoonx.moonotesnetwork;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;

/**
 * Help and privacy controls. Reports are local until the player copies them or
 * explicitly opens a GitHub issue. No automatic telemetry endpoint is shipped.
 */
final class MoonotesSupportScreen extends Screen {
    private final Screen parent;
    private final SupportPreferences settings=SupportPreferences.local();
    private String status="Нажми «Самодиагностика», чтобы проверить мод.";
    private String report="";
    private boolean confirmRestore;
    private int left, top, widthPanel;

    MoonotesSupportScreen(Screen parent){
        super(Component.literal("Moonotes — поддержка"));
        this.parent=parent;
    }
    @Override protected void init(){
        super.init();
        widthPanel=Math.min(490,width-20);
        left=(width-widthPanel)/2;
        top=Math.max(7,(height-285)/2);
        int inner=widthPanel-24, x=left+12;
        add(x,top+45,(inner-8)/2,"Самодиагностика",()->{
            try{report=MoonotesHealthCheck.run();status="Готово. Отчёт можно скопировать.";}
            catch(RuntimeException e){status="Ошибка локальной диагностики";}
        });
        add(x+inner/2+4,top+45,(inner-8)/2,"Копировать отчёт",()->{
            if(report.isBlank()) report=MoonotesHealthCheck.compactReport();
            if(minecraft!=null)minecraft.keyboardHandler.setClipboard(report);
            status="Скопировано. Проверь содержимое перед отправкой.";
        });
        add(x,top+73,inner,"Сообщить об ошибке на GitHub",()->{
            if(!settings.allowManualReports()){status="Ручные отчёты выключены в настройках";return;}
            if(report.isBlank())report=MoonotesHealthCheck.compactReport();
            if(minecraft!=null)minecraft.keyboardHandler.setClipboard(report);
            try{
                URI url=URI.create("https://github.com/mymoonx/moonotes/issues/new?template=bug_report.yml");
                if(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)){
                    Desktop.getDesktop().browse(url);
                    status="Открыта форма GitHub. Отчёт в буфере — вставь при необходимости.";
                }else status="Скопируй отчёт, затем открой GitHub Issues в браузере.";
            }catch(Exception e){status="Не удалось открыть браузер; отчёт скопирован";}
        });
        add(x,top+104,(inner-8)/2,"Режим качества: "+settings.performance().label,()->{
            settings.cyclePerformance();
            SignEmoteRenderer.reset();
            BookEmoteRenderer.disconnect();
            if(minecraft!=null)minecraft.gui.setScreen(new MoonotesSupportScreen(parent));
        });
        add(x+inner/2+4,top+104,(inner-8)/2,"Проверить обновления",()->{
            MoonotesUpdateCheck.check();
            status="Проверка запущена. Результат появится ниже.";
        });
        add(x,top+136,(inner-8)/2,"Создать резервную копию",()->{
            try{
                Path saved=LocalBackup.backup(config());
                status="Копия сохранена в config/moonotes-backups/"+saved.getFileName();
            }catch(Exception e){status="Не удалось создать резервную копию";}
        });
        add(x+inner/2+4,top+136,(inner-8)/2,"Восстановить последнюю",()->{
            if(!confirmRestore){
                confirmRestore=true;
                status="Нажми «Восстановить» повторно. Нужен перезапуск Minecraft.";
                return;
            }
            confirmRestore=false;
            try{
                Path archive=LocalBackup.newest(config());
                if(archive==null){status="Резервных копий нет";return;}
                int count=LocalBackup.restore(config(),archive);
                status="Восстановлено файлов: "+count+". Перезапусти Minecraft.";
            }catch(Exception e){status="Не удалось восстановить настройки";}
        });
        add(x,top+168,inner,"Анонимная телеметрия: "+(settings.telemetryOptIn()?"Разрешена":"Выключена"),()->{
            settings.toggleTelemetry();
            if(minecraft!=null)minecraft.gui.setScreen(new MoonotesSupportScreen(parent));
        });
        add(x,top+200,inner,"Ручная отправка: "+(settings.allowManualReports()?"Разрешена":"Выключена"),()->{
            settings.toggleManualReports();
            if(minecraft!=null)minecraft.gui.setScreen(new MoonotesSupportScreen(parent));
        });
        add(x+inner-80,top+246,80,"Назад",this::onClose);
    }

    private Path config(){return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();}
    private void add(int x,int y,int w,String label,Runnable action){
        addRenderableWidget(new MoonotesButton(new MoonotesLayout.Rect(x,y,Math.max(20,w),22),
            label,label,false,b->action.run()));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(left,top,left+widthPanel,top+280,0xF0192638);
        g.outline(left,top,widthPanel,280,0xFF57789A);
        g.text(font,"Диагностика и настройки поддержки",left+12,top+12,0xFFF4F7FD,false);
        g.text(font,"Отчёты отправляются только после твоего подтверждения.",left+12,top+27,0xFFB4C8DC,false);
        String info="Автоматическая передача телеметрии пока не подключена.";
        g.text(font,font.plainSubstrByWidth(info,widthPanel-24),left+12,top+226,0xFFB7C9DA,false);
        String result=MoonotesUpdateCheck.busy()?"Проверяем обновления…":MoonotesUpdateCheck.result();
        g.text(font,font.plainSubstrByWidth(result,widthPanel-105),left+12,top+242,0xFF9ED8C4,false);
        String label=font.plainSubstrByWidth(status,widthPanel-108);
        g.text(font,label,left+12,top+263,0xFFFFD4A2,false);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public void onClose(){if(minecraft!=null)minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean isInGameUi(){return true;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){}
}
