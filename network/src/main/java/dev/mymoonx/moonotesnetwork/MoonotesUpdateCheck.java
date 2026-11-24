package dev.mymoonx.moonotesnetwork;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.ZipFile;

/**
 * Only official GitHub Releases of mymoonx/moonotes; never downloads arbitrary
 * URLs supplied by release metadata. SHA256SUMS.txt is mandatory for staging.
 * A separate pure-Java helper applies updates only AFTER Minecraft exits.
 */
final class MoonotesUpdateCheck {
    private static final String REPO="mymoonx/moonotes";
    private static final String API="https://api.github.com/repos/"+REPO+"/releases?per_page=30";
    private static final long DAY_MS=24*60*60*1000L;
    private static final int MAX_API=240*1024, MAX_JAR=12*1024*1024, MAX_SUMS=4096;
    private static final Pattern VERSION=Pattern.compile(
        "^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?:-(alpha|beta|rc)\\.(\\d{1,3}))?$");
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"Moonotes-Update-Check");t.setDaemon(true);return t;
    });
    record Candidate(String version,String tag,String jar) {}
    private record Pending(String operation,Path current,Path staged,Path updates,
                           String sha256,String version) {}
    private static volatile String result="Проверка ещё не выполнялась.";
    private static volatile boolean busy,downloading;
    private static volatile Candidate available;
    private static volatile Path staged;
    private static volatile String stagedSha="";
    private static volatile Pending pending;
    private static volatile boolean hookRegistered;
    private MoonotesUpdateCheck(){}

    static String result(){return result;}
    static boolean busy(){return busy||downloading;}
    static boolean isStaged(){return available!=null && staged!=null && Files.isRegularFile(staged,LinkOption.NOFOLLOW_LINKS);}
    static boolean isPending(){return pending!=null;}
    static String availableVersion(){return available==null?"":available.version();}
    static String currentVersion(){
        return FabricLoader.getInstance().getModContainer("moonotes_network")
            .map(c->c.getMetadata().getVersion().getFriendlyString()).orElse("0.0.0");
    }

    static void maybeCheck(){
        SupportPreferences pref=SupportPreferences.local();
        if(pref.updateMode()==SupportPreferences.UpdateMode.OFF)return;
        long now=System.currentTimeMillis();
        if(now-pref.lastUpdateCheck()<DAY_MS)return;
        pref.checkedNow(now);check(true);
    }
    static synchronized void check(){check(false);}
    private static synchronized void check(boolean automatic){
        if(busy||downloading)return;
        busy=true;result="Проверка GitHub Releases…";
        var pref=SupportPreferences.local();
        SupportPreferences.UpdateChannel channel=pref.updateChannel();
        SupportPreferences.UpdateMode mode=pref.updateMode();
        IO.execute(()->{
            try{
                byte[] bytes=fetch(URI.create(API),MAX_API);
                JsonArray array=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonArray();
                Candidate selected=choose(array,currentVersion(),channel);
                available=selected;staged=null;stagedSha="";
                if(selected==null){
                    result="Новых версий для "+channel.label+" нет.";
                    return;
                }
                result="Доступна Moonotes Network "+selected.version();
                if(automatic && mode!=SupportPreferences.UpdateMode.OFF){
                    Minecraft mc=Minecraft.getInstance();
                    mc.execute(()->MoonotesNotifications.notifyUser("Обновление Moonotes",
                        "Доступна версия "+selected.version()+". Открой Настройки → Поддержка.",false));
                }
                if(mode==SupportPreferences.UpdateMode.DOWNLOAD
                    ||mode==SupportPreferences.UpdateMode.INSTALL){
                    stageInternal(selected);
                    if(mode==SupportPreferences.UpdateMode.INSTALL)planInstall();
                }
            }catch(Exception ex){result="Не удалось проверить обновления GitHub.";}
            finally{busy=false;}
        });
    }

    static Candidate choose(JsonArray releases,String installed,SupportPreferences.UpdateChannel channel) {
        Candidate best=null;
        for(JsonElement item:releases){
            try{
                JsonObject o=item.getAsJsonObject();
                if(o.get("draft").getAsBoolean())continue;
                if(channel==SupportPreferences.UpdateChannel.STABLE&&o.get("prerelease").getAsBoolean())continue;
                String tag=o.get("tag_name").getAsString();
                if(!tag.startsWith("v")||!VERSION.matcher(tag.substring(1)).matches())continue;
                String version=tag.substring(1);
                if(compare(version,installed)<=0)continue;
                String jar="moonotes-network-"+version+".jar";
                boolean exists=false,sums=false;
                for(JsonElement asset:o.getAsJsonArray("assets")){
                    String name=asset.getAsJsonObject().get("name").getAsString();
                    if(jar.equals(name))exists=true;
                    if("SHA256SUMS.txt".equals(name))sums=true;
                }
                // Do not advertise an unverified/uninstallable release as auto-updatable.
                if(!exists||!sums)continue;
                if(best==null||compare(version,best.version())>0)best=new Candidate(version,tag,jar);
            }catch(RuntimeException ignored){}
        }
        return best;
    }
    static int compare(String a,String b){
        int[] x=parse(a),y=parse(b);
        if(x==null)return -1;
        if(y==null)return 1;
        for(int i=0;i<x.length;i++){
            int c=Integer.compare(x[i],y[i]);if(c!=0)return c;
        }
        return 0;
    }
    private static int[] parse(String raw){
        if(raw==null)return null;
        Matcher m=VERSION.matcher(raw);
        if(!m.matches())return null;
        int stage=switch(m.group(4)==null?"":m.group(4)){
            case "alpha"->1;case "beta"->2;case "rc"->3;default->4;
        };
        return new int[]{Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),
            Integer.parseInt(m.group(3)),stage,m.group(5)==null?0:Integer.parseInt(m.group(5))};
    }
    private static URI githubAsset(Candidate c,String filename) {
        return URI.create("https://github.com/"+REPO+"/releases/download/"+c.tag()+"/"+filename);
    }
    private static boolean allowed(URI uri){
        if(uri==null||!"https".equalsIgnoreCase(uri.getScheme())
            ||uri.getUserInfo()!=null || uri.getPort()!=-1)return false;
        String host=uri.getHost();
        if("api.github.com".equals(host))return uri.getPath().equals("/repos/"+REPO+"/releases");
        if("github.com".equals(host))return uri.getPath().startsWith("/"+REPO+"/releases/download/");
        return "release-assets.githubusercontent.com".equals(host)
            ||"objects.githubusercontent.com".equals(host);
    }
    private static byte[] fetch(URI start,int max)throws IOException {
        URI uri=start;
        for(int tries=0;tries<6;tries++){
            if(!allowed(uri))throw new IOException("Untrusted update host");
            HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(6500);c.setReadTimeout(13000);
            c.setRequestProperty("Accept",uri.getHost().equals("api.github.com")
                ?"application/vnd.github+json":"application/octet-stream");
            c.setRequestProperty("User-Agent","Moonotes-updater");
            try{
                int status=c.getResponseCode();
                if(status==301||status==302||status==303||status==307||status==308){
                    String location=c.getHeaderField("Location");
                    if(location==null)throw new IOException("Redirect without Location");
                    uri=uri.resolve(location);
                    continue;
                }
                if(status!=200)throw new IOException("HTTP "+status);
                if(c.getContentLengthLong()>max)throw new IOException("Update too large");
                try(InputStream in=c.getInputStream()){
                    byte[] result=in.readNBytes(max+1);
                    if(result.length>max)throw new IOException("Update too large");
                    return result;
                }
            }finally{c.disconnect();}
        }
        throw new IOException("Too many redirects");
    }
    static String expectedHash(byte[] sums,String jar)throws IOException {
        String body=new String(sums,StandardCharsets.UTF_8);
        if(!jar.matches("moonotes-network-[a-z0-9.-]{1,50}\\.jar"))throw new IOException("Invalid name");
        for(String line:body.split("\\R")){
            String[] pieces=line.strip().split("\\s+",2);
            if(pieces.length!=2)continue;
            String file=pieces[1].startsWith("*")?pieces[1].substring(1):pieces[1];
            // GitHub Actions releases may store the original build path in SHA256SUMS.
            if((file.equals(jar)||file.equals("network/build/libs/"+jar))
                &&pieces[0].matches("[a-fA-F0-9]{64}"))
                return pieces[0].toLowerCase(Locale.ROOT);
        }
        throw new IOException("JAR checksum missing");
    }
    static void validateJar(Path jar,Candidate selected)throws IOException {
        try(ZipFile zip=new ZipFile(jar.toFile())){
            var entry=zip.getEntry("fabric.mod.json");
            if(entry==null||entry.getSize()>32768)throw new IOException("Not Fabric JAR");
            byte[] bytes;
            try(InputStream in=zip.getInputStream(entry)){bytes=in.readNBytes(32769);}
            if(bytes.length>32768)throw new IOException("Metadata too large");
            JsonObject obj=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            if(!"moonotes_network".equals(obj.get("id").getAsString())
                ||!selected.version().equals(obj.get("version").getAsString())
                ||!"26.3".equals(obj.getAsJsonObject("depends").get("minecraft").getAsString()))
                throw new IOException("Wrong mod or Minecraft version");
        }catch(RuntimeException e){throw new IOException("Invalid mod metadata",e);}
    }
    static synchronized void stage(){
        if(available==null||busy||downloading)return;
        Candidate selected=available;downloading=true;result="Загрузка "+selected.version()+"…";
        IO.execute(()->{
            try{stageInternal(selected);}catch(Exception ex){result="Не удалось проверить или скачать JAR обновления.";}
            finally{downloading=false;}
        });
    }
    private static void stageInternal(Candidate selected)throws Exception{
        byte[] sums=fetch(githubAsset(selected,"SHA256SUMS.txt"),MAX_SUMS);
        String expected=expectedHash(sums,selected.jar());
        byte[] jar=fetch(githubAsset(selected,selected.jar()),MAX_JAR);
        Path folder=updatesDir();
        Files.createDirectories(folder);
        Path tmp=Files.createTempFile(folder,"moonotes-download-",".tmp");
        try{
            Files.write(tmp,jar);
            if(!MoonotesUpdateInstaller.checksum(tmp).equals(expected))
                throw new IOException("SHA-256 mismatch");
            validateJar(tmp,selected);
            Path target=folder.resolve(selected.jar());
            Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);
            staged=target;stagedSha=expected;
            result="Файл "+selected.version()+" проверен и готов к установке.";
        }finally{Files.deleteIfExists(tmp);}
    }
    private static Path updatesDir(){
        return FabricLoader.getInstance().getConfigDir().resolve("moonotes-updates").toAbsolutePath().normalize();
    }
    private static Path installedJar()throws IOException{
        Path dir=FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();
        if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))throw new IOException("mods not found");
        Path found=null;
        try(var files=Files.newDirectoryStream(dir,"moonotes-network-*.jar")){
            for(Path entry:files){
                if(!MoonotesUpdateInstaller.validJarName(entry.getFileName().toString())
                    ||!Files.isRegularFile(entry,LinkOption.NOFOLLOW_LINKS))continue;
                if(found!=null)throw new IOException("Several Moonotes Network mods detected");
                found=entry.toAbsolutePath().normalize();
            }
        }
        if(found==null)throw new IOException("Moonotes Network JAR not found in mods");
        return found;
    }
    private static synchronized void registerHook(){
        if(hookRegistered)return;
        hookRegistered=true;
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            Pending p=pending;
            if(p==null)return;
            try{
                Path java=Path.of(System.getProperty("java.home"),"bin",
                    System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win")
                        ?"javaw.exe":"java");
                Path helper=p.updates().resolve("moonotes-updater-helper.jar");
                ProcessBuilder command=new ProcessBuilder(java.toString(),"-cp",helper.toString(),
                    MoonotesUpdateInstaller.class.getName(),p.operation(),
                    Long.toString(ProcessHandle.current().pid()),p.current().toString(),
                    p.staged().toString(),p.updates().toString(),p.sha256(),p.version());
                command.redirectErrorStream(true);
                command.redirectOutput(ProcessBuilder.Redirect.appendTo(
                    p.updates().resolve("installer.log").toFile()));
                command.start(); // Independent worker waits for our process to exit.
            }catch(Exception ignored){}
        },"Moonotes-Update-Shutdown"));
    }
    private static void copyHelper(Path current,Path updates)throws IOException{
        Files.createDirectories(updates);
        Files.copy(current,updates.resolve("moonotes-updater-helper.jar"),StandardCopyOption.REPLACE_EXISTING);
    }
    static synchronized boolean planInstall(){
        if(!isStaged()||available==null||stagedSha.length()!=64)return false;
        try{
            Path old=installedJar(),updates=updatesDir();
            copyHelper(old,updates);
            pending=new Pending("INSTALL",old,staged,updates,stagedSha,available.version());
            registerHook();
            result="Установка "+available.version()+" после закрытия Minecraft (с копией старого JAR).";
            return true;
        }catch(IOException e){result="Не удалось подготовить установку: проверь папку mods.";return false;}
    }
    static synchronized void cancelPending(){
        pending=null;
        result="Автоустановка отменена. Текущий JAR не изменён.";
    }
    static synchronized boolean hasRollback(){
        return rollbackRecord()!=null;
    }
    private static Properties rollbackRecord(){
        try{
            Path dir=updatesDir(),file=dir.resolve("last-update.properties");
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>1024)return null;
            Properties p=new Properties();
            try(InputStream in=Files.newInputStream(file)){p.load(in);}
            if(!MoonotesUpdateInstaller.validJarName(p.getProperty("oldName",""))
                ||!MoonotesUpdateInstaller.validJarName(p.getProperty("newName",""))
                ||!p.getProperty("backup","").matches("previous-[a-f0-9-]{36}\\.bak")
                ||!p.getProperty("sha256","").matches("[a-f0-9]{64}"))return null;
            Path backup=dir.resolve(p.getProperty("backup"));
            if(!Files.isRegularFile(backup,LinkOption.NOFOLLOW_LINKS))return null;
            return p;
        }catch(Exception ignored){return null;}
    }
    static synchronized boolean planRollback(){
        Properties record=rollbackRecord();
        if(record==null){result="Резервной версии для отката нет.";return false;}
        try{
            Path current=installedJar(),dir=updatesDir(),backup=dir.resolve(record.getProperty("backup"));
            if(!record.getProperty("newName").equals(current.getFileName().toString()))
                throw new IOException("Different installed version");
            if(!MoonotesUpdateInstaller.checksum(backup).equals(record.getProperty("sha256")))
                throw new IOException("Backup checksum mismatch");
            String oldVer=record.getProperty("oldName").replace("moonotes-network-","").replace(".jar","");
            copyHelper(current,dir);
            pending=new Pending("ROLLBACK",current,backup,dir,record.getProperty("sha256"),oldVer);
            registerHook();
            result="Откат к "+oldVer+" выполнится после закрытия Minecraft.";
            return true;
        }catch(Exception ex){result="Не удалось подготовить безопасный откат.";return false;}
    }
}
