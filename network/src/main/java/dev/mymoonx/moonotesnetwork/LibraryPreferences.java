package dev.mymoonx.moonotesnetwork;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local favorites and explicitly inserted/copied names. No chat messages are stored. */
final class LibraryPreferences {
    static final int MAX_FAVORITES=2000, MAX_RECENT=100, MAX_GROUPS=12, MAX_GROUP_MEMBERS=1200;
    private static final int MAX_FILE_BYTES=8*1024*1024;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final ExecutorService WRITER=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"Moonotes-Library-Settings");t.setDaemon(true);return t;
    });
    private static LibraryPreferences instance;
    private final Path path;
    private final Set<String> favorites=new LinkedHashSet<>();
    private final LinkedHashMap<String,Long> recent=new LinkedHashMap<>();
    private final LinkedHashMap<String,LinkedHashSet<String>> groups=new LinkedHashMap<>();
    private long clock, revision;
    private boolean preserveUnreadable;
    private final java.util.concurrent.atomic.AtomicBoolean savePending=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile String warning="";
    private static final class Data { List<String> favorites; Map<String,Long> recent; Map<String,List<String>> groups; }

    static synchronized LibraryPreferences local() {
        if(instance==null)instance=new LibraryPreferences(FabricLoader.getInstance().getConfigDir().resolve("moonotes-library.json"));
        return instance;
    }
    LibraryPreferences(Path path) {
        this.path=path;
        if(!Files.isRegularFile(path,java.nio.file.LinkOption.NOFOLLOW_LINKS))return;
        try {
            String raw=new String(CacheIntegrity.boundedRead(path,MAX_FILE_BYTES),StandardCharsets.UTF_8);
            CatalogStore.checkJson(raw,MAX_FILE_BYTES);
            Data data=JSON.fromJson(raw,Data.class);
            if(data==null)return;
            if(data.favorites!=null)for(String key:data.favorites) {
                if(favorites.size()>=MAX_FAVORITES)break;
                if(validKey(key))favorites.add(key);
            }
            if(data.recent!=null)data.recent.entrySet().stream()
                .filter(e->validKey(e.getKey()) && e.getValue()!=null && e.getValue()>0)
                .sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(MAX_RECENT)
                .sorted(Map.Entry.comparingByValue()).forEach(e->recent.put(e.getKey(),++clock));
            if(data.groups!=null) for (var e:data.groups.entrySet()) {
                if(groups.size()>=MAX_GROUPS)break;
                String name=e.getKey();
                if(!validGroupName(name) || e.getValue()==null)continue;
                var members=new LinkedHashSet<String>();
                for(String key:e.getValue()){
                    if(members.size()>=MAX_GROUP_MEMBERS)break;
                    if(validKey(key))members.add(key);
                }
                groups.put(name,members);
            }
        } catch(IOException | RuntimeException ex) {preserveUnreadable=true;warning="Не удалось прочитать избранное; файл сохранён без изменений.";}
    }
    static boolean validKey(String key) {
        return key!=null && key.length()<=130 && key.matches("[a-zA-Z0-9]{16,40}:[^\\s\\p{Cntrl}\\p{Cf}§]{1,80}");
    }
    static boolean validGroupName(String name) {
        if(name==null || name.isBlank() || name.length()>24 || !name.equals(name.strip()))return false;
        if(!name.matches("[\\p{L}\\p{N} _-]{1,24}"))return false;
        return !java.util.Set.of("все","избранное","недавние").contains(name.toLowerCase(java.util.Locale.ROOT));
    }
    synchronized long revision(){return revision;}
    synchronized int groupSize(String group){var items=groups.get(group);return items==null?0:items.size();}
    synchronized List<String> groupNames() { return List.copyOf(groups.keySet()); }
    synchronized boolean inGroup(String group,String key){
        Set<String> list=groups.get(group);return list!=null && list.contains(key);
    }
    synchronized String createGroup(String group) {
        if(!validGroupName(group))return "Название: 1–24 символа, буквы и цифры";
        if(groups.keySet().stream().anyMatch(existing->existing.equalsIgnoreCase(group)))return "Такая группа уже есть";
        if(groups.size()>=MAX_GROUPS)return "Лимит групп: "+MAX_GROUPS;
        groups.put(group,new LinkedHashSet<>());saveLater();return "";
    }
    synchronized String renameGroup(String oldName,String newName) {
        if(!groups.containsKey(oldName))return "Группа не найдена";
        if(!validGroupName(newName))return "Название: 1–24 символа, буквы и цифры";
        if(groups.keySet().stream().anyMatch(name->!name.equals(oldName)&&name.equalsIgnoreCase(newName)))return "Такая группа уже есть";
        if(oldName.equals(newName))return "";
        var renamed=new LinkedHashMap<String,LinkedHashSet<String>>();
        groups.forEach((name,members)->renamed.put(name.equals(oldName)?newName:name,members));
        groups.clear();groups.putAll(renamed);saveLater();return "";
    }
    synchronized boolean removeGroup(String group) {
        if(groups.remove(group)==null)return false;saveLater();return true;
    }
    synchronized boolean toggleGroup(String group,String key) {
        if(!validKey(key))return false;
        var list=groups.get(group);if(list==null)return false;
        if(!list.remove(key)) {
            if(list.size()>=MAX_GROUP_MEMBERS) { warning="Лимит эмотов в группе";return false; }
            list.add(key);
        }
        saveLater();return list.contains(key);
    }
    synchronized boolean favorite(String key){return favorites.contains(key);}
    synchronized long lastUsed(String key){return recent.getOrDefault(key,0L);}
    synchronized boolean toggle(String key) {
        if(!validKey(key))return false;
        if(!favorites.remove(key)) {
            if(favorites.size()>=MAX_FAVORITES){warning="Достигнут лимит избранного: "+MAX_FAVORITES;return false;}
            favorites.add(key);
        }
        saveLater();return favorites.contains(key);
    }
    synchronized void used(String key) {
        if(!validKey(key))return;
        recent.remove(key);
        recent.put(key,++clock);
        while(recent.size()>MAX_RECENT) {
            String oldest=recent.entrySet().stream().min(Map.Entry.comparingByValue()).orElseThrow().getKey();
            recent.remove(oldest);
        }
        saveLater();
    }
    String warning(){return warning;}
    private void saveLater(){
        revision++;
        if(!savePending.compareAndSet(false,true))return;
        WRITER.execute(()->{
            synchronized(this){
                savePending.set(false);
                try{saveNow();}catch(IOException ex){warning="Не удалось сохранить избранное на диск.";}
            }
        });
    }
    synchronized void saveNow() throws IOException {
        Data data=new Data();data.favorites=List.copyOf(favorites);data.recent=new LinkedHashMap<>(recent);
        data.groups=new LinkedHashMap<>();groups.forEach((group,members)->data.groups.put(group,List.copyOf(members)));
        byte[] serialized=JSON.toJson(data).getBytes(StandardCharsets.UTF_8);
        if(serialized.length>MAX_FILE_BYTES)throw new IOException("Размер настроек превышает лимит");
        Files.createDirectories(path.toAbsolutePath().getParent());
        if(preserveUnreadable && Files.exists(path,java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            if(!Files.isRegularFile(path,java.nio.file.LinkOption.NOFOLLOW_LINKS)||Files.size(path)>MAX_FILE_BYTES)
                throw new IOException("Исходный файл настроек сохранён без изменений");
            Files.copy(path,path.resolveSibling(path.getFileName()+".backup-"+System.currentTimeMillis()),java.nio.file.LinkOption.NOFOLLOW_LINKS);
            preserveUnreadable=false;
        }
        Path temp=Files.createTempFile(path.toAbsolutePath().getParent(),"moonotes-library-",".tmp");
        try {
            Files.write(temp,serialized);
            try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ex){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
            warning="";
        } finally {Files.deleteIfExists(temp);}
    }
}
