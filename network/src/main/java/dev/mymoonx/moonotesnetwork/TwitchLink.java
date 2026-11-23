package dev.mymoonx.moonotesnetwork;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Twitch device authorization for a local profile only.
 * Never persists passwords, access/refresh tokens or client secrets.
 * A local link must NOT be treated as proof for other Minecraft players.
 */
final class TwitchLink {
    private static final URI DEVICE = URI.create("https://id.twitch.tv/oauth2/device");
    private static final URI TOKEN = URI.create("https://id.twitch.tv/oauth2/token");
    private static final URI VALIDATE = URI.create("https://id.twitch.tv/oauth2/validate");
    private static final int MAX_JSON = 16384;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Moonotes-Twitch-OAuth");
        thread.setDaemon(true);
        return thread;
    });
    private static final class Holder {
        static final TwitchLink LOCAL = new TwitchLink(
            FabricLoader.getInstance().getConfigDir().resolve("moonotes-twitch-link.json"));
    }
    private final Path path;
    private final AtomicLong revision = new AtomicLong();
    private volatile String clientId = "", twitchId = "", twitchLogin = "", minecraftUuid = "";
    private volatile String code = "", verification = "", message = "Не подключено";
    private volatile long expiresAt;
    private volatile boolean connecting;

    private static final class Data { String clientId, twitchId, twitchLogin, minecraftUuid; }
    record Device(String deviceCode, String userCode, URI verification, int expires, int interval) { }
    record Identity(String id, String login) { }
    private record Reply(int status, String body) { }

    static TwitchLink local() { return Holder.LOCAL; }
    private TwitchLink(Path path) { this.path = path; load(); }
    String clientId() { return clientId; }
    String login() { return twitchLogin; }
    String twitchId() { return twitchId; }
    boolean linkedFor(String uuid) { return linked() && uuid != null && uuid.equals(minecraftUuid); }
    String message() { return message; }
    String code() { return System.currentTimeMillis() < expiresAt ? code : ""; }
    String verification() { return System.currentTimeMillis() < expiresAt ? verification : ""; }
    boolean connecting() { return connecting; }
    boolean linked() { return !twitchId.isEmpty(); }

    static boolean validMinecraftUuid(String id) {
        return id != null && id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
    }

    static boolean validClientId(String id) {
        return id != null && id.matches("[a-zA-Z0-9]{10,64}");
    }

    synchronized boolean setClientId(String value) {
        String id = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (!validClientId(id)) {
            message = "Нужен публичный Client ID Twitch (не Client Secret)";
            return false;
        }
        if (!id.equals(clientId)) {
            revision.incrementAndGet();
            clientId = id;
            twitchId = twitchLogin = minecraftUuid = code = verification = "";
            connecting = false;
            save();
        }
        message = linked() ? "Привязан: " + twitchLogin : "Client ID сохранён. Начните привязку.";
        return true;
    }

    synchronized void unlink() {
        revision.incrementAndGet();
        connecting = false;
        twitchId = twitchLogin = minecraftUuid = code = verification = "";
        message = "Twitch отвязан";
        save();
    }

    synchronized void start(String uuid) {
        if (connecting) return;
        if (!validMinecraftUuid(uuid)) {
            message = "Нужен запущенный профиль Minecraft";
            return;
        }
        if (!validClientId(clientId)) {
            message = "Сначала введите Client ID Twitch";
            return;
        }
        long id = revision.incrementAndGet();
        String app = clientId;
        connecting = true;
        code = verification = "";
        message = "Получаем код Twitch…";
        IO.execute(() -> authorize(id, app, uuid));
    }

    private boolean current(long id) { return revision.get() == id; }
    private void authorize(long attempt, String app, String minecraftProfile) {
        try {
            // Twitch Device Code Flow; no chat, moderation or email scopes.
            Reply first = send(DEVICE, "client_id=" + encode(app) + "&scopes=", null);
            if (first.status != 200) throw new IOException("Сервис Twitch вернул HTTP " + first.status);
            Device device = parseDevice(first.body);
            if (!current(attempt)) return;
            code = device.userCode();
            verification = device.verification().toString();
            expiresAt = System.currentTimeMillis() + device.expires() * 1000L;
            message = "Откройте ссылку и введите код";
            int poll = device.interval();
            while (current(attempt) && System.currentTimeMillis() < expiresAt) {
                Thread.sleep(1000L * poll);
                if (!current(attempt)) return;
                String form = "client_id=" + encode(app) + "&device_code=" + encode(device.deviceCode())
                    + "&grant_type=" + encode("urn:ietf:params:oauth:grant-type:device_code");
                Reply response = send(TOKEN, form, null);
                JsonObject json = json(response.body);
                if (response.status == 200) {
                    String token = field(json, "access_token");
                    if (token.isEmpty()) throw new IOException("Twitch не прислал токен");
                    Reply checked = send(VALIDATE, null, token);
                    if (checked.status != 200) throw new IOException("Twitch не подтвердил аккаунт");
                    Identity identity = parseIdentity(checked.body, app);
                    synchronized (this) {
                        if (!current(attempt)) return;
                        twitchId = identity.id();
                        twitchLogin = identity.login();
                        minecraftUuid = minecraftProfile;
                        code = verification = "";
                        message = "Twitch подтверждён: " + twitchLogin;
                        save(); // Token intentionally stays only in a local variable.
                    }
                    return;
                }
                String error = field(json, "message").toLowerCase(Locale.ROOT);
                if (error.isEmpty()) error = field(json, "error").toLowerCase(Locale.ROOT);
                if (error.contains("authorization_pending")) continue;
                if (error.contains("slow_down")) { poll = Math.min(30, poll + 5); continue; }
                if (error.contains("access_denied")) throw new IOException("Пользователь отклонил вход");
                if (error.contains("expired_token")) break;
                throw new IOException("Не удалось проверить Twitch (HTTP " + response.status + ")");
            }
            if (current(attempt)) message = "Код истёк. Повторите попытку.";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (current(attempt)) message = "Привязка прервана";
        } catch (IOException | RuntimeException e) {
            if (current(attempt)) message = "Ошибка авторизации Twitch";
        } finally {
            if (current(attempt)) { connecting = false; code = verification = ""; }
        }
    }

    static Device parseDevice(String raw) throws IOException {
        JsonObject data = json(raw);
        String deviceCode = field(data, "device_code"), userCode = field(data, "user_code");
        int expires = number(data, "expires_in"), interval = number(data, "interval");
        URI verification;
        try { verification = URI.create(field(data, "verification_uri")); }
        catch (IllegalArgumentException e) { throw new IOException("Неверная ссылка Twitch"); }
        if (deviceCode.length() < 8 || deviceCode.length() > 1024
            || userCode.length() < 4 || userCode.length() > 40 || expires < 30 || expires > 3600
            || interval < 1 || interval > 60 || !"https".equals(verification.getScheme())
            || !("twitch.tv".equals(verification.getHost()) || "www.twitch.tv".equals(verification.getHost())))
            throw new IOException("Неверный ответ Twitch");
        return new Device(deviceCode, userCode, verification, expires, interval);
    }

    static Identity parseIdentity(String raw, String expectedClientId) throws IOException {
        JsonObject data = json(raw);
        String id = field(data, "user_id");
        String login = field(data, "login").toLowerCase(Locale.ROOT);
        if (!expectedClientId.equalsIgnoreCase(field(data, "client_id"))
            || !id.matches("[0-9]{1,24}") || !login.matches("[a-z0-9_]{3,25}"))
            throw new IOException("Twitch вернул неподтверждённую личность");
        return new Identity(id, login);
    }

    private static JsonObject json(String raw) throws IOException {
        if (raw == null || raw.length() > MAX_JSON) throw new IOException("Неверный размер JSON");
        try {
            var element = JsonParser.parseString(raw);
            if (!element.isJsonObject()) throw new IOException("Ожидался объект JSON");
            return element.getAsJsonObject();
        } catch (com.google.gson.JsonParseException | IllegalStateException e) {
            throw new IOException("Неверный ответ JSON", e);
        }
    }
    private static String field(JsonObject obj, String key) {
        try { return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : ""; }
        catch (RuntimeException e) { return ""; }
    }
    private static int number(JsonObject obj, String key) {
        try { return obj.get(key).getAsInt(); }
        catch (RuntimeException e) { return -1; }
    }
    private static String encode(String data) { return URLEncoder.encode(data, StandardCharsets.UTF_8); }

    private static Reply send(URI target, String form, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) target.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (form != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (var output = connection.getOutputStream()) {
                    output.write(form.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "OAuth " + token);
            }
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) throw new IOException("Redirect rejected");
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (input == null) return new Reply(status, "{}");
            try (input) {
                byte[] body = input.readNBytes(MAX_JSON + 1);
                if (body.length > MAX_JSON) throw new IOException("Twitch response too large");
                return new Reply(status, new String(body, StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    private synchronized void load() {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > 4096) return;
            Data saved = new com.google.gson.Gson().fromJson(Files.readString(path), Data.class);
            if (saved == null) return;
            if (validClientId(saved.clientId)) clientId = saved.clientId.toLowerCase(Locale.ROOT);
            if (validMinecraftUuid(saved.minecraftUuid) && saved.twitchId != null && saved.twitchId.matches("[0-9]{1,24}")
                && saved.twitchLogin != null && saved.twitchLogin.matches("[a-zA-Z0-9_]{3,25}")) {
                twitchId = saved.twitchId;
                twitchLogin = saved.twitchLogin.toLowerCase(Locale.ROOT);
                minecraftUuid = saved.minecraftUuid;
                message = "Ранее подтверждён: " + twitchLogin;
            }
        } catch (IOException | RuntimeException e) { message = "Ошибка чтения привязки Twitch"; }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Data stored = new Data();
            stored.clientId = clientId; stored.twitchId = twitchId; stored.twitchLogin = twitchLogin;
            stored.minecraftUuid = minecraftUuid;
            Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), "moonotes-twitch-", ".tmp");
            try {
                Files.writeString(temp, new com.google.gson.Gson().toJson(stored), StandardCharsets.UTF_8);
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException e) { message = "Не удалось сохранить Twitch-привязку"; }
    }
}
