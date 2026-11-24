package dev.mymoonx.moonotesnetwork;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Pages, group-linked pages and hashed-server channel settings.
 * Accepts the legacy alpha4 {slots, channel} structure transparently.
 */
final class WheelSettings {
    static final int SLOTS=8, MAX_PAGES=20;
    private static final int MAX_BYTES=48*1024, MAX_WORLDS=128;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();

    enum Channel {
        LOCAL("Локальный",""), GLOBAL("Глобальный","!");
        final String label,prefix;
        Channel(String label,String prefix){this.label=label;this.prefix=prefix;}
    }
    static final class Page {
        String title="Основные",group="";
        List<String> slots=emptySlots();
        Page(){}
        Page(String t,String g,List<String> s){title=t;group=g;slots=normalized(s);}
    }
    private static final class World {
        String prefix="!";int page;
    }
    private static final class Stored {
        List<String> slots; String channel; // legacy alpha4
        List<Page> pages; int page; Map<String,World> worlds;
    }
    private static final class Holder {
        static final WheelSettings LOCAL=new WheelSettings(
            net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("moonotes-wheel.json"));
    }
    static WheelSettings local(){return Holder.LOCAL;}

    private static volatile String activeGlobalPrefix="!";
    static String activePrefix(){return activeGlobalPrefix;}

    private final Path file;
    private final List<Page> pages=new ArrayList<>();
    private final Map<String,World> worlds=new LinkedHashMap<>();
    private Channel channel=Channel.LOCAL;
    private int page;
    private String currentWorld=ServerCatalogProfiles.worldKey("singleplayer");
    private boolean configured,preserveUnreadable;
    private String error="";

    WheelSettings(Path path){file=path;pages.add(new Page());load();}
    synchronized Channel channel(){return channel;}
    synchronized int page(){return page;}
    synchronized int pageCount(){return pages.size();}
    synchronized String pageTitle(){return pages.get(page).title;}
    synchronized String pageGroup(){return pages.get(page).group;}
    synchronized String error(){return error;}
    synchronized boolean configured(){return configured;}
    synchronized String prefix(){return profile().prefix;}

    synchronized void setWorld(String worldKey) {
        if(worldKey==null||!worldKey.matches("[a-f0-9]{64}"))return;
        currentWorld=worldKey;
        World world=worlds.get(worldKey);
        page=world==null?0:Math.clamp(world.page,0,pages.size()-1);
        activeGlobalPrefix=world==null?"!":world.prefix;
    }

    synchronized boolean setPrefix(String value){
        if(!validPrefix(value)){error="Префикс: от 1 до 4 символов ! # @ $ % & * + . ~ -";return false;}
        if(!worlds.containsKey(currentWorld)&&worlds.size()>=MAX_WORLDS){error="Лимит профилей: 128";return false;}
        profile().prefix=value;
        activeGlobalPrefix=value;
        return save();
    }
    static boolean validPrefix(String value){
        return value!=null&&value.matches("[!#@$%&*+.~-]{1,4}");
    }

    synchronized void setChannel(Channel value){
        if(value==null)throw new IllegalArgumentException("Channel");
        channel=value;save();
    }
    synchronized void setPage(int index){
        page=Math.floorMod(index,pages.size());
        profile().page=page;
        save();
    }
    synchronized void stepPage(int direction){setPage(page+direction);}

    synchronized boolean addPage(String name){
        if(pages.size()>=MAX_PAGES){error="Лимит: 20 страниц";return false;}
        String title=cleanName(name,"Страница "+(pages.size()+1));
        pages.add(new Page(title,"",emptySlots()));
        page=pages.size()-1;profile().page=page;configured=true;
        return save();
    }
    synchronized boolean removePage(){
        if(pages.size()==1){error="Нельзя удалить последнюю страницу";return false;}
        pages.remove(page);page=Math.min(page,pages.size()-1);
        for(World world:worlds.values())world.page=Math.min(world.page,pages.size()-1);
        return save();
    }
    synchronized boolean renamePage(String title){
        pages.get(page).title=cleanName(title,pages.get(page).title);
        return save();
    }
    synchronized boolean linkGroup(String group){
        if(group==null||!LibraryPreferences.local().groupNames().contains(group)){
            error="Сначала создай группу в мини-библиотеке";return false;
        }
        pages.get(page).group=group;pages.get(page).title=cleanName(group,group);
        return save();
    }
    synchronized boolean unlinkGroup(){
        if(pages.get(page).group.isBlank())return true;
        pages.get(page).slots=slots();pages.get(page).group="";
        return save();
    }
    synchronized boolean linkNextGroup(){
        var names=LibraryPreferences.local().groupNames();
        if(names.isEmpty()){error="Пока нет пользовательских групп";return false;}
        String group=pages.get(page).group;
        int index=names.indexOf(group);
        return linkGroup(names.get((index+1)%names.size()));
    }

    /** Reads first eight emotes in the group, preserving the mini-library source of truth.
     * Additional pages can reference different groups or manual sets.
     */
    synchronized List<String> slots(){
        Page current=pages.get(page);
        if(current.group.isBlank())return current.slots;
        var out=new ArrayList<>(emptySlots());
        int i=0;
        for(ChatPalette.Emote emote:ChatPalette.snapshot()){
            if(LibraryPreferences.local().inGroup(current.group,emote.key())){
                if(i>=SLOTS)break;
                out.set(i++,emote.key());
            }
        }
        return List.copyOf(out);
    }

    synchronized void seed(List<ChatPalette.Emote> defaults){
        if(configured||defaults==null||defaults.isEmpty())return;
        List<String> initial=new ArrayList<>(emptySlots());
        for(int i=0;i<Math.min(SLOTS,defaults.size());i++)initial.set(i,defaults.get(i).key());
        pages.getFirst().slots=normalized(initial);configured=true;save();
    }
    synchronized boolean saveSlots(List<String> value){
        if(!pages.get(page).group.isEmpty()){error="Страница связана с группой. Сначала отвяжи её";return false;}
        pages.get(page).slots=normalized(value);configured=true;return save();
    }
    synchronized ChatPalette.Emote[] resolve(List<ChatPalette.Emote> source){
        var indexed=new HashMap<String,ChatPalette.Emote>();
        for(ChatPalette.Emote emote:source)indexed.putIfAbsent(emote.key(),emote);
        List<String> active=slots();
        ChatPalette.Emote[] result=new ChatPalette.Emote[SLOTS];
        for(int i=0;i<SLOTS;i++)result[i]=indexed.get(active.get(i));
        return result;
    }

    static List<String> emptySlots(){return Collections.nCopies(SLOTS,"");}
    static List<String> normalized(List<String> input){
        List<String> result=new ArrayList<>(emptySlots());
        Set<String> seen=new HashSet<>();
        if(input!=null)for(int i=0;i<Math.min(SLOTS,input.size());i++){
            String key=input.get(i);
            if(LibraryPreferences.validKey(key)&&seen.add(key))result.set(i,key);
        }
        return List.copyOf(result);
    }
    static List<String> assign(List<String> input,int slot,String key){
        if(slot<0||slot>=SLOTS||key==null||(!key.isEmpty()&&!LibraryPreferences.validKey(key)))
            throw new IllegalArgumentException("Invalid slot");
        List<String> output=new ArrayList<>(normalized(input));
        int existing=key.isEmpty()?-1:output.indexOf(key);
        if(existing>=0&&existing!=slot)output.set(existing,output.get(slot));
        output.set(slot,key);
        return List.copyOf(output);
    }
    private World profile(){
        return worlds.computeIfAbsent(currentWorld,k->new World());
    }
    private static String cleanName(String raw,String fallback){
        if(raw==null)return fallback;
        String trimmed=raw.strip();
        return trimmed.isBlank()?fallback:trimmed.length()>24?trimmed.substring(0,24):trimmed;
    }
    private void load(){
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return;
        try{
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>MAX_BYTES)
                throw new IOException("Invalid wheel file");
            Stored data=JSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),Stored.class);
            if(data==null)throw new IOException("Empty settings");
            if(data.pages!=null&&!data.pages.isEmpty()){
                if(data.pages.size()>MAX_PAGES)throw new IOException("Too many pages");
                var restored=new ArrayList<Page>();
                for(Page p:data.pages){
                    if(p==null)continue;
                    String title=cleanName(p.title,"Страница");
                    String group=p.group==null||p.group.length()>24?"":p.group;
                    restored.add(new Page(title,group,p.slots));
                }
                if(!restored.isEmpty()){pages.clear();pages.addAll(restored);configured=true;}
            }else if(data.slots!=null){
                pages.getFirst().slots=normalized(data.slots);configured=true;
            }
            if("GLOBAL".equals(data.channel))channel=Channel.GLOBAL;
            page=Math.clamp(data.page,0,pages.size()-1);
            if(data.worlds!=null){
                for(var entry:data.worlds.entrySet()){
                    if(worlds.size()>=MAX_WORLDS)break;
                    if(entry.getKey()==null||!entry.getKey().matches("[a-f0-9]{64}")
                       ||entry.getValue()==null)continue;
                    World w=new World();
                    w.prefix=validPrefix(entry.getValue().prefix)?entry.getValue().prefix:"!";
                    w.page=Math.clamp(entry.getValue().page,0,pages.size()-1);
                    worlds.put(entry.getKey(),w);
                }
            }
        }catch(IOException|RuntimeException e){
            preserveUnreadable=true;
            error="Настройки колеса не прочитаны; исходный файл сохранён";
        }
    }
    private boolean save(){
        try{
            Stored data=new Stored();
            data.pages=new ArrayList<>(pages);data.channel=channel.name();
            data.page=page;data.worlds=new LinkedHashMap<>(worlds);
            byte[] bytes=JSON.toJson(data).getBytes(StandardCharsets.UTF_8);
            if(bytes.length>MAX_BYTES)throw new IOException("Too large");
            Path parent=file.toAbsolutePath().getParent();Files.createDirectories(parent);
            if(preserveUnreadable&&Files.exists(file,LinkOption.NOFOLLOW_LINKS)){
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>MAX_BYTES)
                    throw new IOException("Original preferences preserved");
                Files.copy(file,file.resolveSibling(file.getFileName()+".backup-"+System.currentTimeMillis()),
                    LinkOption.NOFOLLOW_LINKS);
                preserveUnreadable=false;
            }
            Path tmp=Files.createTempFile(parent,"moonotes-wheel-",".tmp");
            try{
                Files.write(tmp,bytes);
                try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(tmp);}
            error="";return true;
        }catch(IOException|RuntimeException e){error="Не удалось сохранить настройки колеса";return false;}
    }
}
