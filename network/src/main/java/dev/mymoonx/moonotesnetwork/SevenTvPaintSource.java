package dev.mymoonx.moonotesnetwork;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * On-demand 7TV paint importer using its documented public user connection URL.
 * This does NOT connect to or impersonate Chatterino+.
 */
final class SevenTvPaintSource {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Moonotes-7TV-Paint");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean FETCHING = new AtomicBoolean();
    private static volatile String status = "";
    private SevenTvPaintSource() { }
    static String status() { return status; }
    static boolean busy() { return FETCHING.get(); }

    static void loadMyPaint() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !TwitchLink.local().linkedFor(client.player.getUUID().toString())) {
            status = "Сначала привяжите Twitch к текущему профилю";
            return;
        }
        String id = TwitchLink.local().twitchId();
        if (!id.matches("[0-9]{1,24}") || !FETCHING.compareAndSet(false, true)) return;
        status = "Проверяем пейнт 7TV…";
        IO.execute(() -> {
            try {
                URI url = URI.create("https://7tv.io/v3/users/twitch/" + id);
                var response = BoundedHttp.get(url, "application/json", 512 * 1024,
                    Duration.ofSeconds(12), () -> false, bytes -> {});
                if (response.statusCode() == 404) { status = "7TV-профиль не найден"; return; }
                if (response.statusCode() != 200)
                    throw new IOException("7TV HTTP " + response.statusCode());
                JsonObject payload;
                try {
                    payload = JsonParser.parseString(
                        new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
                } catch (RuntimeException e) { throw new IOException("Bad 7TV JSON", e); }
                JsonObject paint = selectedPaint(payload);
                if (paint == null) {
                    status = "У Twitch-профиля нет активного пейнта 7TV";
                    return;
                }
                if (!id.equals(TwitchLink.local().twitchId())) {
                    status = "Twitch-аккаунт изменён, загрузка отменена";
                    return;
                }
                PaintSettings.local().importVerified(id, paint);
                status = "Пейнт 7TV загружен: " + PaintSettings.local().paint().name;
            } catch (IOException | InterruptedException | RuntimeException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                status = "Пейнт 7TV недоступен; сохранённый кеш не изменён";
            } finally {
                FETCHING.set(false);
            }
        });
    }

    static JsonObject selectedPaint(JsonObject response) {
        if (response == null) return null;
        JsonObject user = response;
        if (response.has("user") && response.get("user").isJsonObject())
            user = response.getAsJsonObject("user");
        if (!user.has("style") || !user.get("style").isJsonObject()) return null;
        JsonObject style = user.getAsJsonObject("style");
        if (!style.has("paint") || !style.get("paint").isJsonObject()) return null;
        return style.getAsJsonObject("paint");
    }
}
