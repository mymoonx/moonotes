package dev.mymoonx.moonotesnetwork;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Per-client opt-in settings. No account, server or private data is stored. */
final class ExtrasSettings {
    private static final Gson JSON = new Gson();
    private static final class Holder {
        static final ExtrasSettings INSTANCE = new ExtrasSettings(
            net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("moonotes-extras.json"));
    }
    static ExtrasSettings local() { return Holder.INSTANCE; }
    private static final class Stored {
        Boolean wheel, reactions, signText, bookText, itemNames;
        Integer reactionSeconds;
    }
    private final Path file;
    private boolean wheel = true, reactions = true, signText, bookText, itemNames;
    private int reactionSeconds = 4;
    private String error = "";

    ExtrasSettings(Path file) { this.file = file; load(); }
    synchronized boolean wheel() { return wheel; }
    synchronized boolean reactions() { return reactions; }
    synchronized boolean signText() { return signText; }
    synchronized boolean bookText() { return bookText; }
    synchronized boolean itemNames() { return itemNames; }
    synchronized int reactionSeconds() { return reactionSeconds; }
    synchronized String error() { return error; }

    synchronized String value(int option) {
        return switch (option) {
            case 15 -> on(wheel);
            case 16 -> on(reactions);
            case 17 -> reactionSeconds + " сек.";
            case 18 -> on(signText);
            case 19 -> on(bookText);
            case 20 -> on(itemNames);
            default -> "—";
        };
    }
    private static String on(boolean flag) { return flag ? "Включено" : "Выключено"; }

    synchronized void cycle(int option) {
        switch (option) {
            case 15 -> wheel = !wheel;
            case 16 -> reactions = !reactions;
            case 17 -> reactionSeconds = reactionSeconds == 2 ? 4 : reactionSeconds == 4 ? 6 : 2;
            case 18 -> signText = !signText;
            case 19 -> bookText = !bookText;
            case 20 -> itemNames = !itemNames;
            default -> throw new IllegalArgumentException("Unknown extra option");
        }
        save();
    }

    private synchronized void load() {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 8192) return;
            Stored saved = JSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Stored.class);
            if (saved == null) return;
            if (saved.wheel != null) wheel = saved.wheel;
            if (saved.reactions != null) reactions = saved.reactions;
            if (saved.signText != null) signText = saved.signText;
            if (saved.bookText != null) bookText = saved.bookText;
            if (saved.itemNames != null) itemNames = saved.itemNames;
            if (saved.reactionSeconds != null && (saved.reactionSeconds == 2
                    || saved.reactionSeconds == 4 || saved.reactionSeconds == 6))
                reactionSeconds = saved.reactionSeconds;
        } catch (IOException | RuntimeException ex) {
            error = "Настройки дополнений не прочитаны; исходный файл сохранён";
        }
    }
    private synchronized void save() {
        try {
            Stored data = new Stored();
            data.wheel = wheel;
            data.reactions = reactions;
            data.signText = signText;
            data.bookText = bookText;
            data.itemNames = itemNames;
            data.reactionSeconds = reactionSeconds;
            byte[] serialized = JSON.toJson(data).getBytes(StandardCharsets.UTF_8);
            if (serialized.length > 8192) throw new IOException("Settings too large");
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path tmp = Files.createTempFile(file.toAbsolutePath().getParent(), "moonotes-extras-", ".tmp");
            try {
                Files.write(tmp, serialized);
                try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                error = "";
            } finally { Files.deleteIfExists(tmp); }
        } catch (IOException ex) { error = "Не удалось сохранить настройки дополнений"; }
    }
}
