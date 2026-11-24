package dev.mymoonx.moonotesnetwork;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

/**
 * User-triggered backups. Only Moonotes preference JSON, never Twitch, logs,
 * tokens, Streamotes cache, personal chat or server addresses.
 * The archive may only be restored from our fixed backup directory.
 */
final class LocalBackup {
    private static final int MAX_ONE=1024*1024;
    private static final int MAX_TOTAL=4*1024*1024;
    private static final Set<String> SETTINGS=Set.of(
        "moonotes-network.json","moonotes-wheel.json","moonotes-extras.json",
        "moonotes-library.json","moonotes-catalogs.json",
        "moonotes-network-profiles.json","moonotes-hud.json",
        "moonotes-support.json","moonotes-chat-layout.json","moonotes-server-chat.json");
    private static final DateTimeFormatter NAME=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneOffset.UTC);
    private LocalBackup(){}

    static Path backup(Path config) throws IOException {
        Path directory=config.resolve("moonotes-backups");
        Files.createDirectories(directory);
        Path zip=directory.resolve("moonotes-"+NAME.format(Instant.now())+".zip");
        if(Files.exists(zip,LinkOption.NOFOLLOW_LINKS))
            zip=directory.resolve("moonotes-backup-"+java.util.UUID.randomUUID()+".zip");
        int sum=0;
        try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(zip,
                StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),StandardCharsets.UTF_8)){
            for(String name:new TreeSet<>(SETTINGS)){
                Path file=config.resolve(name);
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
                long size=Files.size(file);
                if(size>MAX_ONE||sum+size>MAX_TOTAL)continue;
                byte[] bytes=Files.readAllBytes(file);
                sum+=bytes.length;
                out.putNextEntry(new ZipEntry(name));
                out.write(bytes);
                out.closeEntry();
            }
        }catch(IOException ex){Files.deleteIfExists(zip);throw ex;}
        return zip;
    }

    static Path newest(Path config) throws IOException {
        Path dir=config.resolve("moonotes-backups");
        if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))return null;
        try(var files=Files.list(dir)){
            return files.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)
                && p.getFileName().toString().matches("moonotes-(?:[0-9]{8}-[0-9]{6}|backup-[A-Za-z0-9-]+)\\.zip"))
                .max(Comparator.comparingLong(p->{
                    try{return Files.getLastModifiedTime(p).toMillis();}
                    catch(IOException ex){return Long.MIN_VALUE;}
                })).orElse(null);
        }
    }

    /**
     * Check every entry BEFORE changing the user's configuration. Restoring
     * creates a safety copy of the previous state. Never extracts arbitrary paths.
     */
    static int restore(Path config,Path archive) throws IOException {
        Path allowed=config.resolve("moonotes-backups").toAbsolutePath().normalize();
        if(archive==null||!archive.toAbsolutePath().normalize().getParent().equals(allowed)
           ||!Files.isRegularFile(archive,LinkOption.NOFOLLOW_LINKS)
           ||Files.size(archive)>MAX_TOTAL)throw new IOException("Недопустимый архив");
        Map<String,byte[]> changes=new LinkedHashMap<>();
        int sum=0;
        try(ZipInputStream in=new ZipInputStream(Files.newInputStream(archive),StandardCharsets.UTF_8)){
            for(ZipEntry entry; (entry=in.getNextEntry())!=null;){
                String name=entry.getName();
                if(entry.isDirectory()||!SETTINGS.contains(name)||changes.containsKey(name))
                    throw new IOException("Недопустимый файл в архиве");
                byte[] bytes=in.readNBytes(MAX_ONE+1);
                sum+=bytes.length;
                if(bytes.length>MAX_ONE||sum>MAX_TOTAL)throw new IOException("Слишком большой архив");
                String raw=new String(bytes,StandardCharsets.UTF_8);
                // Strict parse prevents importing malformed preference files.
                CatalogStore.checkJson(raw,MAX_ONE);
                changes.put(name,bytes);
                in.closeEntry();
            }
        }
        if(changes.isEmpty())throw new IOException("Архив не содержит настроек Moonotes");
        backup(config); // reversible safety snapshot before applying changes
        int count=0;
        for(var entry:changes.entrySet()){
            Path output=config.resolve(entry.getKey());
            if(Files.exists(output,LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(output,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Некорректный исходный файл "+entry.getKey());
            Files.createDirectories(output.toAbsolutePath().getParent());
            Path temp=Files.createTempFile(output.toAbsolutePath().getParent(),"moonotes-restore-",".tmp");
            try{
                Files.write(temp,entry.getValue());
                try{Files.move(temp,output,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                catch(AtomicMoveNotSupportedException ex){Files.move(temp,output,StandardCopyOption.REPLACE_EXISTING);}
                count++;
            }finally{Files.deleteIfExists(temp);}
        }
        // Current runtime keeps preferences in memory; caller must restart Minecraft.
        return count;
    }
}
