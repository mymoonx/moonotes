package dev.mymoonx.moonotesnetwork;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HexFormat;
import java.util.Locale;

/** Per-world library selection; server addresses are hashed before saving locally. */
final class ServerCatalogProfiles {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX = 128;
    private final Path path;
    private final LinkedHashMap<String,String> selections = new LinkedHashMap<>();
    private record Data(int version, Map<String,String> worlds) { }
    ServerCatalogProfiles(Path path) { this.path = path; load(); }

    static String worldKey(String address) {
        String raw = address == null || address.isBlank() ? "singleplayer" : address.strip().toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    synchronized String get(String world) { return selections.getOrDefault(world, "all"); }
    synchronized String getOrDefault(String world, String fallback) { return selections.getOrDefault(world, fallback); }
    synchronized void remember(String world, String catalog) throws IOException {
        if (world == null || !world.matches("[a-f0-9]{64}") || catalog == null || catalog.length()>100)
            throw new IOException("Некорректный профиль");
        if (selections.size()>=MAX && !selections.containsKey(world)) throw new IOException("Слишком много профилей");
        String previous = selections.put(world, catalog);
        try { save(); }
        catch (IOException failure) { if (previous == null) selections.remove(world); else selections.put(world, previous); throw failure; }
    }
    private void load() {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            if (Files.size(path)>48*1024) throw new IOException("Профили слишком большие");
            String json = Files.readString(path, StandardCharsets.UTF_8);
            CatalogStore.checkJson(json, 48*1024);
            Data data = JSON.fromJson(json,Data.class);
            if(data == null || data.version != 1 || data.worlds == null || data.worlds.size()>MAX) return;
            for (var e : data.worlds.entrySet()) {
                if(e.getKey()!=null && e.getKey().matches("[a-f0-9]{64}")
                    && e.getValue()!=null && e.getValue().matches("(?:all|(?:channel|set|import):[a-z0-9_]{1,80})"))
                    selections.put(e.getKey(),e.getValue());
            }
        } catch(IOException | RuntimeException ignored) { /* Keep the original on disk; do not overwrite on parse failure. */ }
    }
    private void save() throws IOException {
        Files.createDirectories(path.getParent());
        Path temp=Files.createTempFile(path.getParent(),"moonotes-profile-",".tmp");
        try {
            Files.writeString(temp,JSON.toJson(new Data(1,Map.copyOf(selections))),StandardCharsets.UTF_8);
            try { Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException error) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
