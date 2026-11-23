package dev.mymoonx.moonotesnetwork;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Opt-in client-side paint preferences. No Twitch identity or tokens stored. */
final class PaintPreferences {
    private static final Gson JSON = new Gson();
    private static final int FILE_LIMIT = 8192;
    private static final class Holder {
        static final PaintPreferences LOCAL = new PaintPreferences(
            net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("moonotes-paints.json"));
    }
    static PaintPreferences local() { return Holder.LOCAL; }

    private final Path path;
    private boolean enabled, chat = true, tab = true, names, animation = true, shadows;
    private int quality = 1; // 0 economy, 1 balanced, 2 high
    private String provider = "none";

    private static final class Data {
        boolean enabled, chat = true, tab = true, names, animation = true, shadows;
        int quality = 1;
        String provider = "none";
    }
    PaintPreferences(Path path) { this.path = path; load(); }
    synchronized boolean enabled() { return enabled; }
    synchronized boolean chat() { return chat; }
    synchronized boolean tab() { return tab; }
    synchronized boolean names() { return names; }
    synchronized boolean animation() { return animation; }
    synchronized boolean shadows() { return shadows; }
    synchronized int quality() { return quality; }
    synchronized String provider() { return provider; }
    synchronized String qualityLabel() {
        return switch (quality) { case 0 -> "Экономия"; case 2 -> "Высокое"; default -> "Сбалансированное"; };
    }
    synchronized void toggle(int setting) {
        switch(setting) {
            case 0 -> enabled = !enabled;
            case 1 -> chat = !chat;
            case 2 -> tab = !tab;
            case 3 -> names = !names;
            case 4 -> animation = !animation;
            case 5 -> shadows = !shadows;
            case 6 -> quality = (quality + 1) % 3;
            default -> throw new IllegalArgumentException("Unknown paint setting");
        }
        persist();
    }
    synchronized String state(int setting) {
        if(setting == 6) return qualityLabel();
        boolean value = switch(setting) {
            case 0 -> enabled;
            case 1 -> chat;
            case 2 -> tab;
            case 3 -> names;
            case 4 -> animation;
            case 5 -> shadows;
            default -> false;
        };
        return value ? "Включено" : "Выключено";
    }
    private synchronized void load() {
        try {
            if(!Files.isRegularFile(path) || Files.size(path) > FILE_LIMIT) return;
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            if(raw.length() > FILE_LIMIT) return;
            Data stored = JSON.fromJson(raw, Data.class);
            if(stored == null) return;
            enabled = stored.enabled;
            chat = stored.chat;
            tab = stored.tab;
            names = stored.names;
            animation = stored.animation;
            shadows = stored.shadows;
            quality = Math.max(0, Math.min(2, stored.quality));
            // No provider is authorised/connected in the first foundation build.
            provider = "none";
        } catch (IOException | RuntimeException ignored) {
            // Keep safe defaults and preserve unreadable config on disk.
        }
    }
    private synchronized void persist() {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Data data = new Data();
            data.enabled = enabled;
            data.chat = chat;
            data.tab = tab;
            data.names = names;
            data.animation = animation;
            data.shadows = shadows;
            data.quality = quality;
            data.provider = provider;
            String raw = JSON.toJson(data);
            if (raw.getBytes(StandardCharsets.UTF_8).length > FILE_LIMIT)
                throw new IOException("Settings exceed limit");
            Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), "moonotes-paint-", ".tmp");
            try {
                Files.writeString(temp, raw, StandardCharsets.UTF_8);
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException ignored) {
            // Preference changes remain in memory if the filesystem is read-only.
        }
    }
}
