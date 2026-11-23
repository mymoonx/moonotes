package dev.mymoonx.moonotesnetwork;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * User-enabled visual paints. No automatic external requests and no telemetry.
 * A valid paint descriptor can be placed in config/moonotes-paint.json.
 */
final class PaintSettings {
    private static final class Stored {
        Boolean enabled, chat, tab, animation, demo;
    }
    private static final PaintStyle DEMO_PAINT = PaintStyle.parse(
        "{\"name\":\"Демо Moonotes\",\"stops\":[{\"at\":0,\"color\":\"#54C2FE\"},{\"at\":0.48,\"color\":\"#C689FF\"},{\"at\":1,\"color\":\"#FFD17A\"}]}");
    private static final class Holder { static final PaintSettings INSTANCE = new PaintSettings(
        FabricLoader.getInstance().getConfigDir().resolve("moonotes-paint-settings.json"),
        FabricLoader.getInstance().getConfigDir().resolve("moonotes-paint.json")); }
    static PaintSettings local() { return Holder.INSTANCE; }

    private final Path preferences, paintFile;
    private boolean enabled, chat = true, tab = true, animation = true, demo;
    private volatile PaintStyle paint;
    private volatile String paintStatus = "Пейнт не загружен";
    PaintSettings(Path preferences, Path paintFile) {
        this.preferences = preferences; this.paintFile = paintFile; loadPreferences(); reload();
    }
    boolean enabled() { return enabled; }
    boolean chat() { return enabled && chat; }
    boolean tab() { return enabled && tab; }
    boolean animation() { return animation; }
    PaintStyle paint() {
        return demo ? DEMO_PAINT : paint;
    }
    boolean demo() { return demo; }
    void toggleDemo() { demo = !demo; save(); }
    String status() { return paintStatus; }

    void toggleEnabled() { enabled = !enabled; save(); }
    void toggleChat() { chat = !chat; save(); }
    void toggleTab() { tab = !tab; save(); }
    void toggleAnimation() { animation = !animation; save(); }

    void reload() {
        try {
            if (!Files.isRegularFile(paintFile)) {
                paint = null; paintStatus = "Нет файла config/moonotes-paint.json"; return;
            }
            if (Files.size(paintFile) > 65536) throw new IOException("File too large");
            PaintStyle loaded = PaintStyle.parse(Files.readString(paintFile, StandardCharsets.UTF_8));
            paint = loaded;
            paintStatus = "Загружен: " + loaded.name;
        } catch (IOException | IllegalArgumentException e) {
            paint = null;
            paintStatus = "Не удалось прочитать пейнт";
        }
    }
    private void loadPreferences() {
        try {
            if (!Files.isRegularFile(preferences) || Files.size(preferences) > 16384) return;
            Stored data = new com.google.gson.Gson().fromJson(Files.readString(preferences), Stored.class);
            if (data == null) return;
            enabled = Boolean.TRUE.equals(data.enabled);
            if (data.chat != null) chat = data.chat;
            if (data.tab != null) tab = data.tab;
            if (data.animation != null) animation = data.animation;
            if (data.demo != null) demo = data.demo;
        } catch (IOException | RuntimeException ignored) { /* keep safe defaults */ }
    }
    private void save() {
        try {
            Files.createDirectories(preferences.getParent());
            Stored data = new Stored();
            data.enabled = enabled; data.chat = chat; data.tab = tab; data.animation = animation; data.demo = demo;
            Path tmp = Files.createTempFile(preferences.getParent(), "moonotes-paint-", ".tmp");
            try {
                Files.writeString(tmp, new com.google.gson.Gson().toJson(data), StandardCharsets.UTF_8);
                Files.move(tmp, preferences, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(tmp); }
        } catch (IOException ignored) { /* no-op rather than crash a client */ }
    }
}
