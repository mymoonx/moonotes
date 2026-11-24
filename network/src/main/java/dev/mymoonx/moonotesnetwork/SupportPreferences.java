package dev.mymoonx.moonotesnetwork;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

/**
 * All diagnostics and performance preferences are local unless a user
 * explicitly opens a prefilled GitHub issue in their browser.
 * Opt-in telemetry is reserved for a future documented receiver; no silent upload.
 */
final class SupportPreferences {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    enum Performance { ECONOMY("Экономичный"), BALANCED("Сбалансированный"), HIGH("Высокое качество");
        final String label; Performance(String label) { this.label=label; }
    }
    enum UpdateChannel {
        STABLE("Стабильные"), BETA("Stable + Beta");
        final String label; UpdateChannel(String label){this.label=label;}
    }
    enum UpdateMode {
        OFF("Выключено"), NOTIFY("Уведомлять"), DOWNLOAD("Скачивать"), INSTALL("Устанавливать при выходе");
        final String label; UpdateMode(String label){this.label=label;}
    }
    private static final class Holder {
        static final SupportPreferences LOCAL = new SupportPreferences(
            net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("moonotes-support.json"));
    }
    static SupportPreferences local() { return Holder.LOCAL; }
    private static final class Data {
        Boolean telemetryOptIn, allowManualReports, updateNotifications;
        String performance, updateChannel, updateMode;
        Long lastUpdateCheck;
    }

    private final Path path;
    private boolean telemetryOptIn, allowManualReports=true, updateNotifications;
    private Performance performance=Performance.BALANCED;
    private UpdateChannel updateChannel=UpdateChannel.STABLE;
    private UpdateMode updateMode=UpdateMode.NOTIFY;
    private long lastUpdateCheck;
    private String status="";

    SupportPreferences(Path file) { path=file; read(); }
    synchronized boolean telemetryOptIn(){return telemetryOptIn;}
    synchronized boolean allowManualReports(){return allowManualReports;}
    synchronized boolean updateNotifications(){return updateNotifications;}
    synchronized Performance performance(){return performance;}
    synchronized String status(){return status;}
    synchronized UpdateChannel updateChannel(){return updateChannel;}
    synchronized UpdateMode updateMode(){return updateMode;}
    synchronized long lastUpdateCheck(){return lastUpdateCheck;}
    synchronized void cycleUpdateChannel(){
        updateChannel=updateChannel==UpdateChannel.STABLE?UpdateChannel.BETA:UpdateChannel.STABLE;
        lastUpdateCheck=0;write();
    }
    synchronized void cycleUpdateMode(){
        updateMode=UpdateMode.values()[(updateMode.ordinal()+1)%UpdateMode.values().length];
        write();
    }
    synchronized void checkedNow(long now){
        lastUpdateCheck=Math.max(0,now);write();
    }

    synchronized void toggleTelemetry(){telemetryOptIn=!telemetryOptIn;if(!telemetryOptIn)MoonotesTelemetry.clear();write();}
    synchronized void toggleManualReports(){allowManualReports=!allowManualReports;write();}
    synchronized void toggleUpdateNotifications(){updateNotifications=!updateNotifications;write();}
    synchronized void cyclePerformance(){
        performance=Performance.values()[(performance.ordinal()+1)%Performance.values().length];
        write();
    }

    private synchronized void read() {
        try {
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>8192)return;
            Data d=JSON.fromJson(Files.readString(path,StandardCharsets.UTF_8),Data.class);
            if(d==null)return;
            // Strictly default to NO consent, including invalid/unrecognized values.
            telemetryOptIn=Boolean.TRUE.equals(d.telemetryOptIn);
            allowManualReports=!Boolean.FALSE.equals(d.allowManualReports);
            updateNotifications=Boolean.TRUE.equals(d.updateNotifications);
            try{if(d.performance!=null)performance=Performance.valueOf(d.performance);}
            catch(IllegalArgumentException ignored){performance=Performance.BALANCED;}
            try{if(d.updateChannel!=null)updateChannel=UpdateChannel.valueOf(d.updateChannel);}
            catch(IllegalArgumentException ignored){updateChannel=UpdateChannel.STABLE;}
            try{if(d.updateMode!=null)updateMode=UpdateMode.valueOf(d.updateMode);}
            catch(IllegalArgumentException ignored){updateMode=UpdateMode.NOTIFY;}
            lastUpdateCheck=d.lastUpdateCheck==null?0:Math.clamp(d.lastUpdateCheck,0,System.currentTimeMillis());
        } catch(IOException|RuntimeException e){status="Настройки поддержки не прочитаны";telemetryOptIn=false;}
    }
    private synchronized void write(){
        try {
            Data d=new Data(); d.telemetryOptIn=telemetryOptIn;
            d.allowManualReports=allowManualReports;
            d.updateNotifications=updateNotifications;
            d.performance=performance.name();
            d.updateChannel=updateChannel.name();
            d.updateMode=updateMode.name();
            d.lastUpdateCheck=lastUpdateCheck;
            byte[] bytes=JSON.toJson(d).getBytes(StandardCharsets.UTF_8);
            if(bytes.length>8192)throw new IOException("Size");
            Files.createDirectories(path.toAbsolutePath().getParent());
            Path temp=Files.createTempFile(path.toAbsolutePath().getParent(),"moonotes-support-",".tmp");
            try {
                Files.write(temp,bytes);
                try{Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
            } finally{Files.deleteIfExists(temp);}
            status="";
        }catch(IOException e){status="Не удалось записать настройки поддержки";}
    }
}
