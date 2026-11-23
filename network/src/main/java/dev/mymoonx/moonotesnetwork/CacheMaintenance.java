package dev.mymoonx.moonotesnetwork;

import net.minecraft.client.Minecraft;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit check/repair. Never removes the user's shared Streamotes cache. */
final class CacheMaintenance {
    private static final AtomicBoolean BUSY=new AtomicBoolean(), CANCELLED=new AtomicBoolean();
    private static final java.util.concurrent.ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"Moonotes-Cache-Check");t.setDaemon(true);return t;
    });
    private static volatile String status="Проверка ещё не выполнялась";
    private static volatile int checked,total,good,missing,bad;
    private static volatile String direct="не проверялся",alternative="не проверялся";
    private static volatile String probeId="";
    static String probedId() { return probeId; }
    static String probeTarget() {
        return probeId.isBlank() ? "нет" : MoonotesNetworkClient.nameForId(probeId) + " (" + probeId + ")";
    }
    private static volatile boolean restoring;
    private CacheMaintenance() { }
    static boolean busy(){return BUSY.get();}
    static String status(){
        if(restoring){
            if(MoonotesNetworkClient.isDownloading())return "Восстановление · "+MoonotesNetworkClient.stageLabel();
            // A completed fetch can still leave a file unrecognised (missing animation
            // sidecar, invalid PNG, or an origin returning 404). Don't claim repair
            // merely because the downloader processed an entry.
            int remaining=0;
            for(String id:MoonotesNetworkClient.selectedCacheIds())
                if(!CacheFiles.looksValid(CacheFiles.forId(id)))remaining++;
            status="Получено "+MoonotesNetworkClient.downloaded()+" · Не хватает "+remaining+
                " · Ошибок загрузки "+MoonotesNetworkClient.failed();
            restoring=false;
        }
        return status;
    }
    static String direct(){return direct;}
    static String alternative(){return alternative;}
    static void cancel(){CANCELLED.set(true);status="Завершаем проверку…";}
    static String report(){return "Cache check: "+checked+"/"+total+"; valid="+good+"; missing="+missing+"; damaged="+bad+"\n"
        +"Direct image probe: "+direct+"\nCloudflare image probe: "+alternative+"\n";}
    /** A safe read-only diagnostic that never writes or removes shared Streamotes files. */
    static void diagnoseMissing() {
        if (MoonotesNetworkClient.isDownloading() || !BUSY.compareAndSet(false,true)) return;
        CANCELLED.set(false);
        WORKER.execute(()->{
            try {
                String id = "";
                for (String candidate : MoonotesNetworkClient.selectedCacheIds()) {
                    if (!CacheFiles.looksValid(CacheFiles.forId(candidate))) { id=candidate; break; }
                }
                if (id.isBlank()) { status="Все изображения доступны в кеше";probeId="";return; }
                probeId=id;
                status="Проверяем источники: "+MoonotesNetworkClient.nameForId(id);
                probeBoth(id);
                status="Источники проверены для "+MoonotesNetworkClient.nameForId(id)
                    +" · 7TV: "+direct+" · Cloudflare: "+alternative;
            } catch(Exception ex) {status="Не удалось проверить: "+DiagnosticsReport.errorKind(ex);}
            finally {BUSY.set(false);}
        });
    }
    private static void probeBoth(String id) {
        URI cdn=URI.create("https://cdn.7tv.app/emote/"+id+"/2x.webp");
        direct=alternative="ожидание";
        direct=probe(cdn);
        if(!CANCELLED.get()) {
            URI mirror=ProxyUrlResolver.resolve(cdn,"https://moonotes-emotes.thefreelom.workers.dev");
            alternative=mirror==null?"неверный адрес":probe(mirror);
        }
    }
    static void start(boolean repair) {
        if(MoonotesNetworkClient.isDownloading() || !BUSY.compareAndSet(false,true))return;
        List<String> ids=List.copyOf(MoonotesNetworkClient.selectedCacheIds());
        CANCELLED.set(false); restoring=false;checked=good=missing=bad=0;total=ids.size();
        direct=alternative="не проверялся";
        status="Проверяем PNG и анимацию: 0/"+total;
        WORKER.execute(()->{
            boolean handedOff=false;
            try {
                List<String> brokenIds=new ArrayList<>();
                for(String id:ids) {
                    if(CANCELLED.get())break;
                    var png=CacheFiles.forId(id);
                    if(!Files.isRegularFile(png)){missing++;brokenIds.add(id);}
                    else try {
                        if(CacheFiles.expectsAnimation(id) && !Files.isRegularFile(CacheFiles.animationMetadata(png)))
                            throw new java.io.IOException("Missing animation metadata");
                        CacheIntegrity.Loaded loaded=CacheIntegrity.read(png);
                        loaded.image().flush();CacheFiles.markValid(id);good++;
                    }catch(Exception ex){bad++;brokenIds.add(id);CacheFiles.markInvalid(id);}
                    checked++;status="Проверяем PNG и анимацию: "+checked+"/"+total;
                }
                if(!CANCELLED.get() && !ids.isEmpty()) {
                    status="Проверяем доступ к источникам…";
                    probeId=brokenIds.isEmpty()?ids.getFirst():brokenIds.getFirst();
                    probeBoth(probeId);
                }
                MoonotesNetworkClient.cacheChanged();
                boolean cancelled=CANCELLED.get();
                status=cancelled?"Проверка остановлена: "+checked+"/"+total:
                    "Проверено "+total+" · Исправных "+good+" · Нет "+missing+" · Повреждено "+bad;
                if(repair && !cancelled && (missing>0 || bad>0)) {
                    status="Проверка завершена. Восстанавливаем "+(missing+bad)+" файлов…";
                    Minecraft.getInstance().execute(()->{
                        try{
                            if(!CANCELLED.get()){MoonotesNetworkClient.repairFromGui(ids);restoring=true;}
                            else status="Восстановление отменено";
                        }finally{BUSY.set(false);}
                    });
                    handedOff=true;
                }
            } catch(Exception failure) {status="Проверка не завершена: "+DiagnosticsReport.errorKind(failure);}
            finally {if(!handedOff)BUSY.set(false);}
        });
    }
    private static String probe(URI uri) {
        long start=System.nanoTime();
        try {
            var response=BoundedHttp.get(uri,"image/webp",ImageDownloader.MAX_IMAGE_BYTES,Duration.ofSeconds(6),CANCELLED::get,b->{});
            if(response.statusCode()!=200)return "HTTP "+response.statusCode();
            ImageDownloader.validateImage(response.headers().firstValue("content-type").orElse(""), response.body());
            return "доступен, "+((System.nanoTime()-start)/1_000_000)+" мс";
        }catch(InterruptedException ex){Thread.currentThread().interrupt();return "отменено";}
        catch(Exception ex){
            return switch(DiagnosticsReport.errorKind(ex)){
                case "tls" -> "ошибка TLS (защищённое соединение)";
                case "timeout" -> "истекло время ожидания";
                case "dns" -> "не работает DNS";
                case "connection" -> "нет соединения";
                default -> DiagnosticsReport.errorKind(ex);
            };
        }
    }
}
