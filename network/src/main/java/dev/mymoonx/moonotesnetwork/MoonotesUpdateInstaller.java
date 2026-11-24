package dev.mymoonx.moonotesnetwork;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Standalone JVM helper copied outside mods. Launched only from an explicit
 * player-authorized update action or a player-selected auto-install policy.
 * Runs after the Minecraft process exits; never replaces an in-use JAR.
 * No Fabric/Minecraft classes are loaded in this process.
 */
public final class MoonotesUpdateInstaller {
    private MoonotesUpdateInstaller(){}
    static String checksum(Path file) throws Exception {
        MessageDigest dig=MessageDigest.getInstance("SHA-256");
        try(InputStream stream=Files.newInputStream(file)){
            byte[] chunk=new byte[32768];int n;
            while((n=stream.read(chunk))!=-1)dig.update(chunk,0,n);
        }
        return HexFormat.of().formatHex(dig.digest());
    }
    static boolean validJarName(String name){
        return name.matches("moonotes-network-[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}(?:-(?:alpha|beta|rc)\\.[0-9]{1,3})?\\.jar");
    }
    public static void main(String[] args){
        // Arguments are written by the local mod, never received over the network.
        if(args.length!=7)return;
        try {
            String mode=args[0],version=args[6];
            long pid=Long.parseLong(args[1]);
            Path current=Path.of(args[2]).toAbsolutePath().normalize();
            Path staged=Path.of(args[3]).toAbsolutePath().normalize();
            Path updates=Path.of(args[4]).toAbsolutePath().normalize();
            String expected=args[5];
            if(!mode.equals("INSTALL")&&!mode.equals("ROLLBACK"))return;
            if(!validJarName(current.getFileName().toString())
                ||!version.matches("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}(?:-(?:alpha|beta|rc)\\.[0-9]{1,3})?")
                ||!expected.matches("[a-f0-9]{64}")
                ||!staged.startsWith(updates)||!Files.isRegularFile(staged,LinkOption.NOFOLLOW_LINKS)
                ||!Files.isDirectory(current.getParent(),LinkOption.NOFOLLOW_LINKS))return;
            Path target=current.getParent().resolve("moonotes-network-"+version+".jar");
            if(!validJarName(target.getFileName().toString())
                ||(!target.equals(current)&&Files.exists(target,LinkOption.NOFOLLOW_LINKS)))return;
            // Do not modify the currently running game. Parent can be gone by now.
            var parent=ProcessHandle.of(pid);
            if(parent.isPresent()){
                long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
                while(parent.get().isAlive()&&System.nanoTime()<limit)Thread.sleep(250);
                if(parent.get().isAlive())return;
            }
            if(!checksum(staged).equals(expected))return;
            if(mode.equals("INSTALL"))install(current,staged,target,updates);
            else rollback(current,staged,target,updates);
        }catch(Exception ex){
            // The updater must not create crash loops or touch arbitrary files.
            // A failed atomic move is restored by install() where possible.
        }
    }
    private static void move(Path from,Path to)throws IOException{
        try{Files.move(from,to,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException ex){Files.move(from,to);}
    }
    private static void install(Path current,Path staged,Path target,Path updates)throws Exception{
        if(current.equals(target)||!Files.isRegularFile(current,LinkOption.NOFOLLOW_LINKS))return;
        Files.createDirectories(updates);
        Path backup=updates.resolve("previous-"+java.util.UUID.randomUUID()+".bak");
        move(current,backup);
        boolean installed=false;
        try{
            move(staged,target);
            installed=true;
            Properties p=new Properties();
            p.setProperty("oldName",current.getFileName().toString());
            p.setProperty("newName",target.getFileName().toString());
            p.setProperty("backup",backup.getFileName().toString());
            p.setProperty("sha256",checksum(backup));
            p.setProperty("updatedAt",Instant.now().toString());
            Path record=updates.resolve("last-update.properties");
            Path tmp=Files.createTempFile(updates,"moonotes-update-",".tmp");
            try(OutputStream out=Files.newOutputStream(tmp)){p.store(out,"Moonotes update rollback record");}
            Files.move(tmp,record,StandardCopyOption.REPLACE_EXISTING);
        }finally{
            if(!installed && Files.isRegularFile(backup,LinkOption.NOFOLLOW_LINKS))
                move(backup,current); // Restore prior working JAR on failed move.
        }
    }
    private static void rollback(Path current,Path staged,Path target,Path updates)throws Exception{
        if(!Files.isRegularFile(current,LinkOption.NOFOLLOW_LINKS))return;
        if(!staged.getFileName().toString().startsWith("previous-")
            ||!staged.getFileName().toString().endsWith(".bak"))return;
        Path shelved=updates.resolve("rolled-back-"+java.util.UUID.randomUUID()+".bak");
        move(current,shelved);
        boolean restored=false;
        try{
            move(staged,target);
            restored=true;
            Files.deleteIfExists(updates.resolve("last-update.properties"));
        }finally{
            if(!restored&&Files.exists(shelved,LinkOption.NOFOLLOW_LINKS))
                move(shelved,current);
        }
    }
}
