package dev.mymoonx.moonotesnetwork;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * An opt-in side channel for reaction events only. No server plugin, chat content,
 * server address or room code is transmitted. A shared code allows trusted
 * clients to rendezvous but does NOT authenticate Minecraft account ownership.
 */
final class NetworkReactionSession {
    private static final String DEFAULT_ENDPOINT = "wss://moonotes-reactions.thefreelom.workers.dev";
    private static final NetworkReactionSession INSTANCE = new NetworkReactionSession();
    static NetworkReactionSession local() { return INSTANCE; }

    private volatile String code = "", status = "Не подключено";
    private volatile int peers;
    private volatile boolean connected;
    private volatile long generation;
    private volatile WebSocket socket;
    private volatile String world = "";
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8)).build();

    private NetworkReactionSession() { }
    String status() { return status; }
    String code() { return code; }
    int peers() { return peers; }
    boolean connected() { return connected; }

    /** Explicit action from the network-reactions screen; no auto-join. */
    void start(String enteredCode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getCurrentServer() == null) {
            status = "Нужен многопользовательский сервер";
            return;
        }
        String normalized = ReactionRoomCode.normalize(enteredCode);
        if (normalized.isEmpty()) {
            status = "Код должен содержать 32 символа 0–9, A–F";
            return;
        }
        stop();
        String server = mc.getCurrentServer().ip;
        world = ServerCatalogProfiles.worldKey(server);
        code = normalized;
        status = "Подключение к комнате…";
        final long attempt = ++generation;
        String room = ReactionRoomCode.roomKey(server, normalized);
        String endpoint = endpoint();
        if (endpoint == null) {
            status = "Некорректный адрес сервиса реакций";
            return;
        }
        String playerId = mc.player.getUUID().toString();
        String playerName = mc.player.getGameProfile().name();
        URI url = URI.create(endpoint + "/v1/room/" + room);
        http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(9))
            .buildAsync(url, new Handler(attempt, playerId, playerName))
            .whenComplete((created, failure) -> {
                if (attempt != generation && created != null) created.abort();
                if (attempt == generation && failure != null)
                    status = "Сервис недоступен. Проверь адрес и развёртывание Worker";
            });
    }

    void startNew() { start(ReactionRoomCode.create()); }

    void stop() {
        generation++;
        WebSocket previous = socket;
        socket = null;
        connected = false;
        peers = 0;
        code = "";
        world = "";
        status = "Не подключено";
        if (previous != null) previous.abort();
    }

    /** Existing local reaction must always work even when the relay is offline. */
    void send(ChatPalette.Emote emote) {
        WebSocket live = socket;
        if (!connected || live == null || emote == null || !ExtrasSettings.local().reactions()
                || !LocalReactions.valid(emote.name())
                || emote.id() == null || !emote.id().matches("[A-Za-z0-9]{16,40}"))
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getCurrentServer() == null
                || !world.equals(ServerCatalogProfiles.worldKey(mc.getCurrentServer().ip))) return;
        JsonObject message = new JsonObject();
        message.addProperty("type", "reaction");
        message.addProperty("id", emote.id());
        message.addProperty("name", emote.name());
        if (message.toString().getBytes(StandardCharsets.UTF_8).length > 512) return;
        live.sendText(message.toString(), true)
            .exceptionally(ex -> { status = "Потеряна связь с комнатой"; return null; });
    }

    private static String endpoint() {
        String custom = System.getProperty("moonotes.reactions.endpoint", DEFAULT_ENDPOINT).strip();
        if (custom.equals(DEFAULT_ENDPOINT)) return custom;
        // Local Wrangler development only. Do not allow insecure remote origins.
        return custom.matches("ws://(?:127\\.0\\.0\\.1|localhost):[0-9]{2,5}") ? custom : null;
    }

    private void incoming(long attempt, String raw) {
        if (attempt != generation || raw.length() > 1024) return;
        try {
            JsonObject data = JsonParser.parseString(raw).getAsJsonObject();
            String kind = data.get("type").getAsString();
            if ("ready".equals(kind)) {
                connected = true;
                status = "Подключено";
                peers = data.has("peers") ? Math.max(0, Math.min(12, data.get("peers").getAsInt())) : 0;
                return;
            }
            if ("peers".equals(kind)) {
                peers = Math.max(0, Math.min(12, data.get("count").getAsInt() - 1));
                return;
            }
            if ("error".equals(kind)) {
                String reason = data.get("reason").getAsString();
                if ("duplicate_player".equals(reason)) status = "Игрок уже в комнате";
                // Ignore individual rate-limit errors to avoid chat/UI spam.
                return;
            }
            if (!"reaction".equals(kind) || !connected) return;
            String playerId = data.get("uuid").getAsString();
            String playerName = data.get("player").getAsString();
            String emoteId = data.get("id").getAsString();
            String emoteName = data.get("name").getAsString();
            if (!playerId.matches("[a-fA-F0-9-]{36}")
                || !playerName.matches("[A-Za-z0-9_]{3,16}")
                || !emoteId.matches("[A-Za-z0-9]{16,40}")
                || !LocalReactions.valid(emoteName)) return;
            UUID id = UUID.fromString(playerId);
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (attempt != generation || mc.level == null || mc.player == null
                        || mc.getCurrentServer() == null || id.equals(mc.player.getUUID())
                        || !world.equals(ServerCatalogProfiles.worldKey(mc.getCurrentServer().ip)))
                    return;
                Player target = mc.level.getPlayerByUUID(id);
                if (target == null || target.isInvisible()
                        || !playerName.equals(target.getGameProfile().name())) return;
                ChatPalette.Emote emote = new ChatPalette.Emote(
                    "remote:" + emoteId, emoteId, emoteName, "reaction");
                if (LocalReactions.showRemote(id, emote))
                    RemoteReactionImages.request(emoteId);
            });
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException ignored) {
            // Untrusted relay frames never affect Minecraft state.
        }
    }

    private final class Handler implements WebSocket.Listener {
        private final long attempt;
        private final String playerId, playerName;
        private final StringBuilder pending = new StringBuilder();
        Handler(long attempt, String playerId, String playerName) {
            this.attempt = attempt; this.playerId = playerId; this.playerName = playerName;
        }
        @Override public void onOpen(WebSocket connection) {
            if (attempt != generation) { connection.abort(); return; }
            socket = connection;
            JsonObject join = new JsonObject();
            join.addProperty("type", "join");
            join.addProperty("uuid", playerId);
            join.addProperty("name", playerName);
            connection.sendText(join.toString(), true);
            connection.request(1);
        }
        @Override public CompletionStage<?> onText(WebSocket connection,
                CharSequence data, boolean last) {
            synchronized (pending) {
                if (pending.length() + data.length() > 1024) {
                    connection.abort();
                    status = "Получен слишком большой пакет";
                    return CompletableFuture.completedFuture(null);
                }
                pending.append(data);
                if (last) {
                    incoming(attempt, pending.toString());
                    pending.setLength(0);
                }
            }
            connection.request(1);
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onClose(WebSocket connection, int code, String reason) {
            if (attempt == generation) {
                connected = false;
                peers = 0;
                socket = null;
                status = "Соединение закрыто";
            }
            return CompletableFuture.completedFuture(null);
        }
        @Override public void onError(WebSocket connection, Throwable error) {
            if (attempt == generation) {
                connected = false;
                peers = 0;
                socket = null;
                status = "Ошибка соединения";
            }
        }
    }
}
