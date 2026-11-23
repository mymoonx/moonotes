package dev.mymoonx.moonotesnetwork;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerCatalogProfilesCheck {
    public static void main(String[] args) throws Exception {
        Path folder=Files.createTempDirectory("moonotes-server-profile-");
        try {
            Path file=folder.resolve("server-profiles.json");
            String world=ServerCatalogProfiles.worldKey("my.server.invalid:25565");
            check(world.length()==64,"hashed address");
            check(world.equals(ServerCatalogProfiles.worldKey("MY.SERVER.INVALID:25565")),"case insensitive");
            check(!world.equals(ServerCatalogProfiles.worldKey("other.server.invalid")),"separate profiles");
            var store=new ServerCatalogProfiles(file);
            check(store.get(world).equals("all"),"default selection");
            store.remember(world,"channel:v0kky");
            check(store.get(world).equals("channel:v0kky"),"saved selection");
            check(!Files.readString(file).contains("my.server.invalid"),"address not stored as plain text");
            check(new ServerCatalogProfiles(file).get(world).equals("channel:v0kky"),"reload");
            try {store.remember("unsafe", "all");throw new AssertionError("accepted unsafe profile key");}
            catch(java.io.IOException expected) { }
        } finally {
            try(var walk=Files.walk(folder)) {walk.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}
        }
        System.out.println("ServerCatalogProfilesCheck: 8 cases passed");
    }
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
