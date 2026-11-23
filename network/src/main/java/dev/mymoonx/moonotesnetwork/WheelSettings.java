package dev.mymoonx.moonotesnetwork;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Eight stable positions and the explicitly selected chat channel. */
final class WheelSettings {
    static final int SLOTS = 8;
    private static final int MAX_BYTES = 16 * 1024;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    enum Channel {
        LOCAL("Локальный", ""), GLOBAL("Глобальный", "!");
        final String label, prefix;
        Channel(String label, String prefix) { this.label = label; this.prefix = prefix; }
    }
    private static final class Holder {
        static final WheelSettings INSTANCE = new WheelSettings(
            net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("moonotes-wheel.json"));
    }
    private static final class Stored { List<String> slots; String channel; }
    static WheelSettings local() { return Holder.INSTANCE; }

    private final Path file;
    private List<String> slots = emptySlots();
    private Channel channel = Channel.LOCAL;
    private boolean configured, preserveUnreadable;
    private String error = "";

    WheelSettings(Path file) { this.file = file; load(); }
    synchronized Channel channel() { return channel; }
    synchronized List<String> slots() { return slots; }
    synchronized String error() { return error; }
    synchronized boolean configured() { return configured; }

    /** Seed once, after catalogs become available. Later recent activity cannot reorder the wheel. */
    synchronized void seed(List<ChatPalette.Emote> defaults) {
        if (configured || defaults.isEmpty()) return;
        List<String> initial = new ArrayList<>(emptySlots());
        for (int i = 0; i < Math.min(SLOTS, defaults.size()); i++) initial.set(i, defaults.get(i).key());
        slots = normalized(initial);
        configured = true;
        save();
    }

    synchronized void setChannel(Channel value) {
        if (value == null) throw new IllegalArgumentException("Channel required");
        channel = value;
        save();
    }

    synchronized boolean saveSlots(List<String> value) {
        List<String> previous = slots;
        boolean wasConfigured = configured;
        slots = normalized(value);
        configured = true;
        if (save()) return true;
        slots = previous;
        configured = wasConfigured;
        return false;
    }

    /** Keeps unavailable/disabled catalog entries in their original positions. */
    synchronized ChatPalette.Emote[] resolve(List<ChatPalette.Emote> source) {
        var indexed = new java.util.HashMap<String, ChatPalette.Emote>();
        for (ChatPalette.Emote emote : source) indexed.putIfAbsent(emote.key(), emote);
        ChatPalette.Emote[] result = new ChatPalette.Emote[SLOTS];
        for (int i = 0; i < SLOTS; i++) result[i] = indexed.get(slots.get(i));
        return result;
    }

    static List<String> emptySlots() { return Collections.nCopies(SLOTS, ""); }

    static List<String> normalized(List<String> input) {
        List<String> output = new ArrayList<>(emptySlots());
        var seen = new HashSet<String>();
        if (input != null) for (int i = 0; i < Math.min(SLOTS, input.size()); i++) {
            String key = input.get(i);
            if (LibraryPreferences.validKey(key) && seen.add(key)) output.set(i, key);
        }
        return List.copyOf(output);
    }

    /** Picking an emote already on the wheel swaps the two positions. */
    static List<String> assign(List<String> input, int slot, String key) {
        if (slot < 0 || slot >= SLOTS || key == null || !key.isEmpty() && !LibraryPreferences.validKey(key))
            throw new IllegalArgumentException("Invalid wheel slot");
        List<String> output = new ArrayList<>(normalized(input));
        int previous = key.isEmpty() ? -1 : output.indexOf(key);
        if (previous >= 0 && previous != slot) output.set(previous, output.get(slot));
        output.set(slot, key);
        return List.copyOf(output);
    }

    private void load() {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES)
                throw new IOException("Invalid settings file");
            Stored data = JSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Stored.class);
            if (data == null) throw new IOException("Empty settings");
            if (data.slots != null) { slots = normalized(data.slots); configured = true; }
            if ("GLOBAL".equals(data.channel)) channel = Channel.GLOBAL;
        } catch (IOException | RuntimeException ex) {
            preserveUnreadable = true;
            error = "Настройки колеса не прочитаны; исходный файл сохранён";
        }
    }

    private boolean save() {
        try {
            Stored data = new Stored();
            data.slots = configured ? slots : null;
            data.channel = channel.name();
            byte[] bytes = JSON.toJson(data).getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) throw new IOException("Settings too large");
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            if (preserveUnreadable && Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES)
                    throw new IOException("Original settings preserved");
                Files.copy(file, file.resolveSibling(file.getFileName() + ".backup-" + System.currentTimeMillis()),
                    LinkOption.NOFOLLOW_LINKS);
                preserveUnreadable = false;
            }
            Path temp = Files.createTempFile(parent, "moonotes-wheel-", ".tmp");
            try {
                Files.write(temp, bytes);
                try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temp); }
            error = "";
            return true;
        } catch (IOException | RuntimeException ex) {
            error = "Не удалось сохранить колесо на диск";
            return false;
        }
    }
}
