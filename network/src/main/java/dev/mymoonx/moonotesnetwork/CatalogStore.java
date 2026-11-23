package dev.mymoonx.moonotesnetwork;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** Validated metadata only. Image files are owned by Streamotes and never deleted here. */
final class CatalogStore {
    static final int MAX_CATALOGS = 16;
    static final int MAX_EMOTES = 2000;
    static final int MAX_JSON = 1024 * 1024;
    private static final int MAX_STORE = 8 * MAX_JSON;
    private static final Pattern ID = Pattern.compile("(?:[0-9A-HJKMNP-TV-Z]{26}|[a-f0-9]{16,40})", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHANNEL = Pattern.compile("[a-z0-9_]{1,25}");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    record Entry(String id, String name, boolean zeroWidth, Boolean animated) {
        Entry(String id, String name) { this(id, name, false, null); }
        Entry(String id, String name, boolean zeroWidth) { this(id, name, zeroWidth, null); }
        String key() { return id + ":" + name; }
    }
    record Parsed(String name, String setId, List<Entry> emotes) { }
    record Catalog(String key, String name, String kind, String reference, String setId,
                   boolean enabled, long updatedAt, String source, List<Entry> emotes,
                   int added, int removed, String error) { }
    private record Saved(int version, String selected, List<Catalog> catalogs) { }
    private final Path path;
    private final LinkedHashMap<String, Catalog> catalogs = new LinkedHashMap<>();
    private String selected = "all";
    private long revision;

    CatalogStore(Path path) { this.path = path; }
    synchronized long revision() { return revision; }
    synchronized List<Catalog> catalogs() { return List.copyOf(catalogs.values()); }
    synchronized Catalog get(String key) { return catalogs.get(key); }
    synchronized String selected() { return selected; }
    synchronized void select(String key) throws IOException {
        if (!"all".equals(key) && !catalogs.containsKey(key)) throw new IOException("Каталог не найден");
        if (!selected.equals(key)) { selected = key; revision++; }
    }
    synchronized List<Entry> visible() {
        var result = new LinkedHashMap<String, Entry>();
        for (Catalog c : catalogs.values()) {
            if (!c.enabled || (!selected.equals("all") && !selected.equals(c.key))) continue;
            for (Entry e : c.emotes) result.putIfAbsent(e.key(), e);
        }
        return List.copyOf(result.values());
    }
    synchronized List<Entry> allEnabled() {
        var result = new LinkedHashMap<String, Entry>();
        for (Catalog c : catalogs.values()) if (c.enabled) for (Entry e : c.emotes) result.putIfAbsent(e.key(), e);
        return List.copyOf(result.values());
    }
    synchronized String catalogNameFor(Entry entry) {
        for (Catalog c : catalogs.values()) if (c.enabled && (selected.equals("all") || selected.equals(c.key)))
            if (c.emotes.stream().anyMatch(e -> e.key().equals(entry.key()))) return c.name;
        return "";
    }
    synchronized void bootstrap(String json) throws IOException {
        if (catalogs.containsKey("channel:v0kky")) return;
        Parsed parsed = parse(json, "v0kky");
        catalogs.put("channel:v0kky", new Catalog("channel:v0kky", "v0kky", "channel", "v0kky",
            parsed.setId, true, 0, "Встроенный каталог", parsed.emotes, 0, 0, ""));
        revision++;
    }
    synchronized void load() throws IOException {
        if (!Files.isRegularFile(path)) return;
        if (Files.size(path) > MAX_STORE) throw new IOException("Файл каталогов слишком велик");
        final String text;
        try (var in = Files.newInputStream(path)) {
            byte[] bytes = in.readNBytes(MAX_STORE + 1);
            if (bytes.length > MAX_STORE) throw new IOException("Файл каталогов слишком велик");
            text = new String(bytes, StandardCharsets.UTF_8);
        }
        checkJson(text, MAX_STORE);
        final Saved saved;
        try { saved = JSON.fromJson(text, Saved.class); }
        catch (RuntimeException e) { throw new IOException("Повреждён файл каталогов", e); }
        if (saved == null || saved.version != 1 || saved.catalogs == null || saved.catalogs.size() > MAX_CATALOGS)
            throw new IOException("Неизвестный формат каталогов");
        var valid = new LinkedHashMap<String, Catalog>();
        for (Catalog raw : saved.catalogs) {
            if (raw == null) throw new IOException("Пустой каталог");
            String reference = switch (raw.kind == null ? "" : raw.kind) {
                case "channel" -> channel(raw.reference);
                case "set" -> id(raw.reference);
                case "import" -> {
                    if (raw.reference == null || !raw.reference.matches("[a-f0-9]{16}")) throw new IOException("Некорректный импорт");
                    yield raw.reference;
                }
                default -> throw new IOException("Неизвестный источник каталога");
            };
            String key = raw.kind + ":" + reference;
            if (!key.equals(raw.key) || valid.containsKey(key)) throw new IOException("Повтор ключа каталога");
            List<Entry> entries = validateEntries(raw.emotes);
            String setId = raw.setId == null || raw.setId.isBlank() ? "" : id(raw.setId);
            Catalog c = new Catalog(key, title(raw.name, reference), raw.kind, reference, setId, raw.enabled,
                Math.max(0, Math.min(raw.updatedAt, System.currentTimeMillis() + 60_000)), "Локальная копия", entries,
                Math.max(0, Math.min(MAX_EMOTES, raw.added)), Math.max(0, Math.min(MAX_EMOTES, raw.removed)), "");
            valid.put(key, c);
        }
        catalogs.clear(); catalogs.putAll(valid);
        selected = saved.selected != null && catalogs.containsKey(saved.selected) ? saved.selected : "all";
        revision++;
    }
    synchronized Catalog add(String kind, String value, Parsed parsed, String source) throws IOException {
        String ref = switch (kind) {
            case "channel" -> channel(value);
            case "set" -> id(value);
            case "import" -> digest(parsed);
            default -> throw new IOException("Неизвестный источник каталога");
        };
        String key = kind + ":" + ref;
        if (catalogs.containsKey(key)) throw new IOException("Этот каталог уже добавлен");
        if (catalogs.size() >= MAX_CATALOGS) throw new IOException("Допустимо не больше " + MAX_CATALOGS + " каталогов");
        List<Entry> entries = validateEntries(parsed.emotes);
        Catalog c = new Catalog(key, title(parsed.name, ref), kind, ref, parsed.setId,
            true, System.currentTimeMillis(), source, entries, entries.size(), 0, "");
        String oldSelection = selected;
        catalogs.put(key, c); selected = key; revision++;
        try { save(); } catch (IOException e) { catalogs.remove(key); selected = oldSelection; revision++; throw e; }
        return c;
    }
    synchronized Catalog update(String key, Parsed parsed, String source) throws IOException {
        Catalog old = catalogs.get(key);
        if (old == null) throw new IOException("Каталог уже удалён");
        List<Entry> entries = validateEntries(parsed.emotes);
        Set<String> before = new HashSet<>(), after = new HashSet<>();
        for (Entry e : old.emotes) before.add(e.key());
        for (Entry e : entries) after.add(e.key());
        int added = 0, removed = 0;
        for (String e : after) if (!before.contains(e)) added++;
        for (String e : before) if (!after.contains(e)) removed++;
        Catalog c = new Catalog(old.key, old.kind.equals("channel") ? old.reference : title(parsed.name, old.name),
            old.kind, old.reference, parsed.setId.isBlank() ? old.setId : parsed.setId, old.enabled,
            System.currentTimeMillis(), source, entries, added, removed, "");
        catalogs.put(key, c); revision++;
        try { save(); } catch (IOException e) { catalogs.put(key, old); revision++; throw e; }
        return c;
    }
    synchronized void failure(String key, String error) {
        Catalog c = catalogs.get(key); if (c == null) return;
        catalogs.put(key, new Catalog(c.key, c.name, c.kind, c.reference, c.setId, c.enabled, c.updatedAt,
            "Локальная копия", c.emotes, c.added, c.removed, error));
        revision++;
    }
    synchronized void toggle(String key) throws IOException {
        Catalog c = catalogs.get(key); if (c == null) throw new IOException("Каталог не найден");
        Catalog next = new Catalog(c.key, c.name, c.kind, c.reference, c.setId, !c.enabled, c.updatedAt,
            c.source, c.emotes, c.added, c.removed, c.error);
        catalogs.put(key, next); revision++;
        try { save(); } catch (IOException e) { catalogs.put(key, c); revision++; throw e; }
    }
    synchronized void remove(String key) throws IOException {
        Catalog removed = catalogs.remove(key); if (removed == null) return;
        if (selected.equals(key)) selected = "all";
        revision++;
        try { save(); } catch (IOException e) { catalogs.put(key, removed); revision++; throw e; }
    }
    /** Enrich only unknown image facts. Names, selection, deleted catalogues and sync diffs are unchanged. */
    synchronized boolean enrichMissingAnimation(Map<String, Boolean> known) throws IOException {
        if (known.size() > MAX_EMOTES) throw new IOException("Слишком много подсказок анимации");
        var original = new LinkedHashMap<>(catalogs);
        boolean changed = false;
        for (Catalog c : original.values()) {
            boolean changedCatalog = false;
            var enriched = new ArrayList<Entry>(c.emotes.size());
            for (Entry e : c.emotes) {
                Boolean hint = known.get(e.id);
                if (e.animated == null && hint != null) {
                    enriched.add(new Entry(e.id, e.name, e.zeroWidth, hint));
                    changedCatalog = true;
                } else enriched.add(e);
            }
            if (changedCatalog) {
                catalogs.put(c.key, new Catalog(c.key, c.name, c.kind, c.reference, c.setId, c.enabled,
                    c.updatedAt, c.source, List.copyOf(enriched), c.added, c.removed, c.error));
                changed = true;
            }
        }
        if (changed) {
            revision++;
            try { save(); } catch (IOException failure) { catalogs.clear(); catalogs.putAll(original); revision++; throw failure; }
        }
        return changed;
    }
    synchronized void save() throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), "moonotes-catalogs-", ".tmp");
        try {
            Files.writeString(temp, JSON.toJson(new Saved(1, selected, List.copyOf(catalogs.values()))), StandardCharsets.UTF_8);
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    static String channel(String raw) throws IOException {
        String result = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (!CHANNEL.matcher(result).matches()) throw new IOException("Введите Twitch-ник: латинские буквы, цифры и _ (до 25)");
        return result;
    }
    static String id(String raw) throws IOException {
        String result = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (!ID.matcher(result).matches()) throw new IOException("Некорректный ID 7TV");
        return result;
    }
    static Parsed parse(String text, String fallbackName) throws IOException {
        return parse(text, fallbackName, MAX_JSON);
    }
    static Parsed parse(String text, String fallbackName, int limit) throws IOException {
        checkJson(text, limit);
        try {
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) throw new IOException("В JSON нужен объект с массивом emotes");
            JsonObject object = root.getAsJsonObject();
            if (object.has("emote_set") && object.get("emote_set").isJsonObject()) object = object.getAsJsonObject("emote_set");
            JsonElement value = object.get("emotes");
            if (value == null || !value.isJsonArray()) throw new IOException("В JSON нет массива emotes");
            JsonArray array = value.getAsJsonArray();
            if (array.size() > MAX_EMOTES) throw new IOException("В каталоге больше " + MAX_EMOTES + " смайликов");
            List<Entry> entries = new ArrayList<>(array.size());
            for (JsonElement el : array) {
                if (!el.isJsonObject()) throw new IOException("Некорректная запись смайлика");
                JsonObject e = el.getAsJsonObject();
                Boolean animated = optionalBoolean(e, "animated");
                boolean zeroWidth = booleanValue(e, "zeroWidth") || booleanValue(e, "zero_width");
                if (e.has("data") && e.get("data").isJsonObject()) {
                    JsonObject data = e.getAsJsonObject("data");
                    if (animated == null) animated = optionalBoolean(data, "animated");
                    if (data.has("flags") && data.get("flags").isJsonPrimitive()
                            && data.getAsJsonPrimitive("flags").isNumber()) zeroWidth |= (data.get("flags").getAsInt() & 256) != 0;
                }
                entries.add(new Entry(string(e, "id", ""), string(e, "name", ""), zeroWidth, animated));
            }
            String name = string(object, "channel", string(object, "name", fallbackName));
            String setId = string(object, "setId", string(object, "emote_set_id", string(object, "id", "")));
            return new Parsed(title(name, fallbackName), setId.isBlank() ? "" : id(setId), validateEntries(entries));
        } catch (RuntimeException e) {
            throw new IOException("Некорректный JSON каталога", e);
        }
    }
    private static List<Entry> validateEntries(List<Entry> entries) throws IOException {
        if (entries == null || entries.size() > MAX_EMOTES) throw new IOException("Некорректный список смайликов");
        var result = new LinkedHashMap<String, Entry>();
        for (Entry entry : entries) {
            if (entry == null) throw new IOException("Пустая запись смайлика");
            String id = id(entry.id), name = entry.name;
            if (name == null || name.isBlank() || name.startsWith("/") || name.length() > 80 || name.matches("(?s).*[\\p{Cc}\\p{Cf}\\s§].*"))
                throw new IOException("Некорректное имя смайлика");
            Entry valid = new Entry(id, name, entry.zeroWidth, entry.animated);
            result.putIfAbsent(valid.key(), valid);
        }
        return List.copyOf(result.values());
    }
    private static String title(String value, String fallback) throws IOException {
        String name = value == null || value.isBlank() ? fallback : value.strip();
        if (name == null || name.isBlank() || name.length() > 80 || name.matches("(?s).*[\\p{Cc}\\p{Cf}].*"))
            throw new IOException("Некорректное имя каталога");
        return name;
    }
    private static boolean booleanValue(JsonObject object, String key) throws IOException {
        return Boolean.TRUE.equals(optionalBoolean(object, key));
    }
    private static Boolean optionalBoolean(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IOException("Некорректный флаг смайлика");
        return value.getAsBoolean();
    }
    private static String string(JsonObject object, String key, String fallback) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return fallback;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IOException("Поле " + key + " должно быть текстом");
        return value.getAsString();
    }
    static void checkJson(String text, int maxBytes) throws IOException {
        if (text == null || text.length() > maxBytes || text.getBytes(StandardCharsets.UTF_8).length > maxBytes)
            throw new IOException("JSON слишком велик");
        int depth = 0; boolean quoted = false, escape = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) { if (escape) escape = false; else if (ch == '\\') escape = true; else if (ch == '"') quoted = false; }
            else if (ch == '"') quoted = true;
            else if (ch == '{' || ch == '[') { if (++depth > 24) throw new IOException("JSON имеет слишком глубокую вложенность"); }
            else if (ch == '}' || ch == ']') depth--;
        }
    }
    private static String digest(Parsed parsed) throws IOException {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(JSON.toJson(parsed).getBytes(StandardCharsets.UTF_8)), 0, 8); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
    }
}
