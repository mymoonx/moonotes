package dev.mymoonx.moonotesnetwork;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Fixed-origin metadata reads. GQL search resolves the exact connected Twitch account,
 * then the connection endpoint supplies its current active set, never a user's first set. */
final class CatalogTransport {
    private static final String WORKER = "https://moonotes-emotes.thefreelom.workers.dev";
    private static final String SEARCH_QUERY = "query FindChannel($name: String!) { users(query: $name, limit: 20) { connections { id platform username } } }";
    private static final Set<String> REMOTE_CODES = Set.of("channel_not_found", "channel_lookup_ambiguous",
        "channel_lookup_unavailable", "channel_lookup_mismatch", "channel_has_no_set", "not_found_on_7tv",
        "upstream_rate_limited", "upstream_timeout", "upstream_unavailable", "upstream_unreachable",
        "invalid_emote_set", "invalid_emote_entry", "set_lookup_mismatch", "catalogue_busy", "catalogue_retry_later",
        "r2_not_bound", "gateway_unavailable", "not_found", "cached_manifest_too_large", "manifest_too_large",
        "upstream_body_too_large", "upstream_empty_body", "upstream_invalid_json_type", "upstream_invalid_json", "invalid_upstream_body");
    record Result(CatalogStore.Parsed parsed, String source) { }
    record Request(URI uri, String body, int limit, Duration timeout) {
        boolean post() { return body != null; }
    }
    @FunctionalInterface interface Fetch { String request(Request request) throws IOException, InterruptedException; }
    record Failure(String source, String stage, String reason, int status, int upstreamStatus) {
        String display() {
            String detail = switch (reason) {
                case "timeout", "upstream_timeout" -> "истекло время ожидания";
                case "dns" -> "ошибка DNS";
                case "tls" -> "ошибка TLS";
                case "connection", "upstream_unreachable" -> "нет соединения";
                case "channel_not_found" -> "Twitch-канал не найден в 7TV";
                case "channel_has_no_set" -> "для Twitch-канала не выбран набор 7TV";
                case "not_found_on_7tv" -> "канал или набор не найден в 7TV";
                case "channel_lookup_ambiguous" -> "найдено несколько разных Twitch ID";
                case "channel_lookup_mismatch", "set_lookup_mismatch" -> "ответ относится к другому каналу/набору";
                case "upstream_rate_limited" -> "лимит запросов 7TV; повторите позднее";
                case "catalogue_busy", "catalogue_retry_later" -> "сервер занят; повторите позднее";
                case "r2_not_bound" -> "Worker не подключён к R2";
                case "not_found" -> "маршрут не найден; проверьте версию Worker";
                case "invalid_catalog", "invalid_json", "invalid_emote_set", "invalid_emote_entry", "upstream_empty_body", "upstream_invalid_json_type", "upstream_invalid_json", "invalid_upstream_body" -> "некорректный ответ";
                case "size_limit", "cached_manifest_too_large", "manifest_too_large", "upstream_body_too_large" -> "превышен допустимый размер";
                case "http" -> "ответ сервера";
                default -> "каталог недоступен";
            };
            return source + " · " + stage + (status > 0 ? " · HTTP " + status : "")
                + (upstreamStatus > 0 ? " · 7TV HTTP " + upstreamStatus : "") + " · " + detail;
        }
    }
    static final class LookupException extends IOException {
        private final List<Failure> failures;
        LookupException(Failure failure) { this(List.of(failure)); }
        LookupException(List<Failure> failures) {
            super(failures.stream().map(Failure::display).reduce((a, b) -> a + "; " + b).orElse("Каталог недоступен"));
            this.failures = List.copyOf(failures);
        }
        List<Failure> failures() { return failures; }
        String details() { return failures.stream().map(Failure::display).reduce((a,b) -> a + "\n" + b).orElse(""); }
    }
    static final class HttpFailure extends IOException {
        final int status, upstreamStatus;
        final String code, stage;
        HttpFailure(int status, String code, String stage, int upstreamStatus) {
            super("HTTP " + status); this.status = status; this.code = code; this.stage = stage; this.upstreamStatus = upstreamStatus;
        }
    }
    private final Fetch fetch;
    CatalogTransport() { this(CatalogTransport::request); }
    CatalogTransport(Fetch fetch) { this.fetch = fetch; }

    private static String request(Request request) throws IOException, InterruptedException {
        URI uri = request.uri();
        // Requests are constructed internally; defend the transport boundary too.
        if (!"https".equals(uri.getScheme()) || uri.getPort() != -1 || uri.getUserInfo() != null
                || !Set.of("7tv.io", "moonotes-emotes.thefreelom.workers.dev").contains(uri.getHost()))
            throw new IOException("Invalid fixed origin");
        var response = request.post()
            ? BoundedHttp.postJson(uri, request.body(), request.limit(), request.timeout(), Thread.currentThread()::isInterrupted, count -> {})
            : BoundedHttp.get(uri, "application/json", request.limit(), request.timeout(), Thread.currentThread()::isInterrupted, count -> {});
        String raw = new String(response.body(), StandardCharsets.UTF_8);
        if (response.statusCode() != 200) {
            String code = "http", stage = ""; int upstreamStatus = 0;
            // Only our fixed Worker may return structured diagnostics; never display arbitrary response text.
            if (uri.getHost().endsWith("workers.dev") && raw.length() < 8192) try {
                JsonObject object = jsonObject(raw, 8192);
                String candidate = string(object, "error");
                if (REMOTE_CODES.contains(candidate)) code = candidate;
                String remoteStage = string(object, "stage");
                stage = switch (remoteStage) { case "search" -> "поиск Twitch-ника"; case "connection" -> "набор канала";
                    case "set" -> "загрузка набора"; case "cache" -> "кеш сервера"; default -> ""; };
                if (object.has("upstreamStatus") && object.get("upstreamStatus").isJsonPrimitive()
                        && object.getAsJsonPrimitive("upstreamStatus").isNumber()) {
                    int value = object.get("upstreamStatus").getAsInt(); if (value >= 100 && value <= 599) upstreamStatus = value;
                }
            } catch (IOException | RuntimeException ignored) { }
            throw new HttpFailure(response.statusCode(), code, stage, upstreamStatus);
        }
        String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).split(";", 2)[0].strip();
        if (!type.equals("application/json") && !type.endsWith("+json")) throw new IOException("Invalid JSON content-type");
        return raw;
    }

    Result fetch(String kind, String reference, String knownSet, String preference) throws IOException, InterruptedException {
        if (!Set.of("channel", "set").contains(kind)) throw new IOException("Импорт JSON обновляется вручную");
        String ref = kind.equals("channel") ? CatalogStore.channel(reference) : CatalogStore.id(reference);
        if ("worker".equals(preference)) return worker(kind, ref);
        try {
            return kind.equals("channel") ? directChannel(ref) : directSet(ref);
        } catch (LookupException directError) {
            if ("direct".equals(preference)) throw directError;
            try { return worker(kind, ref); }
            catch (LookupException workerError) {
                var failures = new java.util.ArrayList<>(directError.failures()); failures.addAll(workerError.failures());
                throw new LookupException(failures);
            }
        }
    }
    private String read(URI uri, String body, int limit, long deadline, String source, String stage)
            throws LookupException, InterruptedException {
        try {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new HttpTimeoutException("Catalogue deadline");
            return fetch.request(new Request(uri, body, limit, Duration.ofNanos(remaining)));
        } catch (HttpFailure error) {
            throw new LookupException(new Failure(source, error.stage.isEmpty() ? stage : error.stage,
                error.code, error.status, error.upstreamStatus));
        } catch (IOException error) {
            String category = DiagnosticsReport.errorKind(error);
            if (error.getMessage() != null && error.getMessage().equals("Invalid JSON content-type")) category = "invalid_json";
            throw new LookupException(new Failure(source, stage, category, 0, 0));
        }
    }
    private Result worker(String kind, String ref) throws LookupException, InterruptedException {
        String stage = "каталог " + (kind.equals("channel") ? "канала" : "набора");
        String raw = read(URI.create(WORKER + "/v1/7tv/" + kind + "/" + ref), null, CatalogStore.MAX_JSON,
            System.nanoTime() + Duration.ofSeconds(16).toNanos(), "Cloudflare", stage);
        try {
            CatalogStore.Parsed parsed = CatalogStore.parse(raw, ref);
            if (kind.equals("set") && !parsed.setId().equalsIgnoreCase(ref))
                throw failure("Cloudflare", stage, "set_lookup_mismatch");
            if (kind.equals("channel") && !parsed.name().equalsIgnoreCase(ref))
                throw failure("Cloudflare", stage, "channel_lookup_mismatch");
            return new Result(parsed, "Cloudflare");
        } catch (LookupException error) { throw error; }
        catch (IOException error) { throw failure("Cloudflare", stage, "invalid_catalog"); }
    }
    private Result directSet(String setId) throws LookupException, InterruptedException {
        String raw = read(URI.create("https://7tv.io/v3/emote-sets/" + setId), null, 8 * CatalogStore.MAX_JSON,
            System.nanoTime() + Duration.ofSeconds(14).toNanos(), "7TV", "загрузка набора");
        try {
            CatalogStore.Parsed parsed = CatalogStore.parse(raw, "Набор " + setId, 8 * CatalogStore.MAX_JSON);
            if (!parsed.setId().equalsIgnoreCase(setId)) throw failure("7TV", "загрузка набора", "set_lookup_mismatch");
            return new Result(parsed, "7TV");
        } catch (LookupException error) { throw error; }
        catch (IOException error) { throw failure("7TV", "загрузка набора", "invalid_catalog"); }
    }
    private Result directChannel(String login) throws LookupException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(14).toNanos();
        JsonObject body = new JsonObject(); body.addProperty("operationName", "FindChannel"); body.addProperty("query", SEARCH_QUERY);
        JsonObject variables = new JsonObject(); variables.addProperty("name", login); body.add("variables", variables);
        String raw = read(URI.create("https://7tv.io/v3/gql"), body.toString(), CatalogStore.MAX_JSON, deadline, "7TV", "поиск Twitch-ника");
        String twitchId;
        try { twitchId = resolveTwitchId(raw, login); }
        catch (LookupException error) { throw error; }
        catch (IOException error) { throw failure("7TV", "поиск Twitch-ника", "invalid_json"); }
        String connection = read(URI.create("https://7tv.io/v3/users/twitch/" + twitchId), null,
            8 * CatalogStore.MAX_JSON, deadline, "7TV", "набор канала");
        try { return new Result(parseConnection(connection, login, twitchId), "7TV"); }
        catch (LookupException error) { throw error; }
        catch (IOException error) { throw failure("7TV", "набор канала", "invalid_catalog"); }
    }
    static String resolveTwitchId(String raw, String login) throws IOException {
        JsonObject root = jsonObject(raw, CatalogStore.MAX_JSON);
        if (root.has("errors") && !root.get("errors").isJsonNull()
                && (!root.get("errors").isJsonArray() || !root.getAsJsonArray("errors").isEmpty()))
            throw failure("7TV", "поиск Twitch-ника", "channel_lookup_unavailable");
        JsonObject data = object(root, "data");
        JsonArray users = data == null ? null : array(data, "users");
        if (users == null || users.size() > 20) throw failure("7TV", "поиск Twitch-ника", "invalid_json");
        var matches = new LinkedHashSet<String>();
        for (JsonElement item : users) {
            if (!item.isJsonObject()) continue;
            JsonArray connections = array(item.getAsJsonObject(), "connections");
            if (connections == null || connections.size() > 32) continue;
            for (JsonElement connection : connections) {
                if (!connection.isJsonObject()) continue;
                JsonObject c = connection.getAsJsonObject();
                if ("TWITCH".equals(string(c, "platform")) && login.equalsIgnoreCase(string(c, "username"))) {
                    String id = string(c, "id"); if (id.matches("[0-9]{1,25}")) matches.add(id);
                }
            }
        }
        if (matches.size() != 1) throw failure("7TV", "поиск Twitch-ника", matches.isEmpty() ? "channel_not_found" : "channel_lookup_ambiguous");
        return matches.getFirst();
    }
    static CatalogStore.Parsed parseConnection(String raw, String login, String twitchId) throws IOException {
        JsonObject connection = jsonObject(raw, 8 * CatalogStore.MAX_JSON);
        if (!"TWITCH".equals(string(connection, "platform")) || !twitchId.equals(string(connection, "id"))
                || !login.equalsIgnoreCase(string(connection, "username")))
            throw failure("7TV", "набор канала", "channel_lookup_mismatch");
        JsonObject set = object(connection, "emote_set");
        if (set == null) throw failure("7TV", "набор канала", "channel_has_no_set");
        CatalogStore.Parsed parsed = CatalogStore.parse(set.toString(), login, 8 * CatalogStore.MAX_JSON);
        String expected = string(connection, "emote_set_id");
        if (parsed.setId().isEmpty() || (!expected.isBlank() && !parsed.setId().equalsIgnoreCase(expected)))
            throw failure("7TV", "набор канала", "set_lookup_mismatch");
        return new CatalogStore.Parsed(login, parsed.setId(), parsed.emotes());
    }
    private static LookupException failure(String source, String stage, String reason) {
        return new LookupException(new Failure(source, stage, reason, 0, 0));
    }
    private static JsonObject jsonObject(String raw, int limit) throws IOException {
        CatalogStore.checkJson(raw, limit);
        try { JsonElement value = JsonParser.parseString(raw); if (value.isJsonObject()) return value.getAsJsonObject(); }
        catch (RuntimeException ignored) { }
        throw new IOException("Некорректный JSON каталога");
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }
    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key); return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }
    private static JsonObject object(JsonObject object, String key) {
        JsonElement value = object.get(key); return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
}
