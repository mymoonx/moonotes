package dev.mymoonx.moonotesnetwork;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * User-editable, local allowlist of command bodies where emote suggestions are safe.
 * The slash command and recipient are never treated as emote text. This only
 * edits the chat input, never sends, modifies or logs private messages.
 */
final class ChatCommandRules {
    enum Mode { RECIPIENT, MESSAGE }
    record Rule(String name, Mode mode) { }

    private static final Gson JSON = new Gson();
    private static final int MAX_RULES = 32;
    private static final int MAX_FILE_BYTES = 8192;
    private static final Pattern NAME = Pattern.compile(
        "[a-z][a-z0-9_]{0,23}(?::[a-z][a-z0-9_]{0,23})?");
    private static volatile ChatCommandRules active = new ChatCommandRules(null);

    private static final class Stored {
        Map<String, String> rules;
    }

    private final Path file;
    private final LinkedHashMap<String, Mode> entries = new LinkedHashMap<>();
    private String error = "";

    ChatCommandRules(Path file) {
        this.file = file;
        entries.putAll(defaults());
        load();
    }

    static void initialize(Path file) { active = new ChatCommandRules(file); }
    static ChatCommandRules local() { return active; }

    static String normalize(String input) {
        if (input == null) return "";
        String name = input.strip().toLowerCase(Locale.ROOT);
        if (name.startsWith("/")) name = name.substring(1);
        return NAME.matcher(name).matches() ? name : "";
    }

    private static LinkedHashMap<String, Mode> defaults() {
        var result = new LinkedHashMap<String, Mode>();
        for (String name : List.of("msg", "tell", "w", "whisper", "pm"))
            result.put(name, Mode.RECIPIENT);
        for (String name : List.of("r", "reply"))
            result.put(name, Mode.MESSAGE);
        return result;
    }

    synchronized List<Rule> list() {
        var result = new ArrayList<Rule>(entries.size());
        entries.forEach((name, mode) -> result.add(new Rule(name, mode)));
        return List.copyOf(result);
    }
    synchronized Mode mode(String raw) {
        String name = normalize(raw);
        return name.isEmpty() ? null : entries.get(name);
    }
    synchronized String error() { return error; }

    synchronized boolean put(String raw, Mode mode) {
        String name = normalize(raw);
        if (name.isEmpty() || mode == null) {
            error = "Команда: /имя или /плагин:имя без пробелов";
            return false;
        }
        if (!entries.containsKey(name) && entries.size() >= MAX_RULES) {
            error = "Максимум 32 команды";
            return false;
        }
        entries.put(name, mode);
        save();
        return error.isEmpty();
    }

    synchronized boolean remove(String raw) {
        String name = normalize(raw);
        if (name.isEmpty() || !entries.containsKey(name)) {
            error = "Такой команды нет";
            return false;
        }
        entries.remove(name);
        save();
        return error.isEmpty();
    }

    synchronized void reset() {
        entries.clear();
        entries.putAll(defaults());
        save();
    }

    private synchronized void load() {
        if (file == null) return;
        try {
            if (!Files.isRegularFile(file)) return;
            if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("Config too large");
            Stored stored = JSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Stored.class);
            if (stored == null || stored.rules == null || stored.rules.size() > MAX_RULES)
                throw new IOException("Invalid command config");
            var validated = new LinkedHashMap<String, Mode>();
            for (var item : stored.rules.entrySet()) {
                String name = normalize(item.getKey());
                if (name.isEmpty() || item.getValue() == null
                        || !name.equals(item.getKey())) throw new IOException("Invalid command");
                validated.put(name, Mode.valueOf(item.getValue()));
            }
            entries.clear();
            entries.putAll(validated); // An intentionally empty list stays empty.
        } catch (IOException | RuntimeException exception) {
            error = "Список команд не прочитан; использованы стандартные значения";
        }
    }

    private synchronized void save() {
        if (file == null) { error = ""; return; }
        try {
            Path folder = file.toAbsolutePath().getParent();
            Files.createDirectories(folder);
            Stored stored = new Stored();
            stored.rules = new LinkedHashMap<>();
            entries.forEach((name, mode) -> stored.rules.put(name, mode.name()));
            byte[] bytes = JSON.toJson(stored).getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_FILE_BYTES) throw new IOException("Config too large");
            Path temp = Files.createTempFile(folder, "moonotes-commands-", ".tmp");
            try {
                Files.write(temp, bytes);
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temp); }
            error = "";
        } catch (IOException exception) {
            error = "Не удалось сохранить список команд";
        }
    }
}
