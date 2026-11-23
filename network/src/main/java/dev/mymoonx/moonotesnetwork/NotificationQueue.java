package dev.mymoonx.moonotesnetwork;

import java.util.ArrayDeque;
import java.util.List;

/** Bounded, memory-only notice queue. Does not read account, world or network information. */
final class NotificationQueue {
    record Event(String title, String message, boolean error, long created) { }
    private static final int HISTORY_LIMIT = 40;
    private static final int PENDING_LIMIT = 3;
    private final ArrayDeque<Event> history = new ArrayDeque<>();
    private final ArrayDeque<Event> pending = new ArrayDeque<>();
    private Event current;
    private long expiresAt;
    private String lastKey = "";
    private long lastAt;

    static String mode(String mode) {
        return "chat".equals(mode) || "important".equals(mode) || "off".equals(mode) ? mode : "toast";
    }
    static boolean visible(String mode, boolean error) {
        return "toast".equals(mode(mode)) || "important".equals(mode(mode)) && error;
    }
    static String clean(String value, int maxCodePoints) {
        if (value == null || maxCodePoints <= 0) return "";
        String clean = value.replaceAll("[\\p{Cc}\\p{Cf}§]", " ").strip();
        int count = clean.codePointCount(0, clean.length());
        return count <= maxCodePoints ? clean
            : clean.substring(0, clean.offsetByCodePoints(0, maxCodePoints - 1)) + "…";
    }

    synchronized Event add(String title, String text, boolean error, long now, String mode, boolean suppressVisual) {
        String label = clean(title, 48), message = clean(text, 300);
        String key = label + '\u0000' + message + '\u0000' + error;
        if (key.equals(lastKey) && now >= lastAt && now - lastAt < 3500) return null;
        lastKey = key; lastAt = now;
        Event entry = new Event(label, message, error, now);
        history.addLast(entry);
        while (history.size() > HISTORY_LIMIT) history.removeFirst();
        current(now, mode);
        if (!suppressVisual && visible(mode, error)) {
            if (current == null || error && !current.error()
                || current.title().equals(label) && (!current.error() || error)) {
                current = entry;
                expiresAt = now + (error ? 6500 : 4200);
            } else {
                // A burst of background status updates must not bury an error.
                pending.removeIf(item -> item.title().equals(label) && !item.error());
                if (pending.size() >= PENDING_LIMIT) {
                    Event routine = pending.stream().filter(item -> !item.error()).findFirst().orElse(null);
                    if (routine != null) pending.remove(routine);
                    else pending.removeFirst();
                }
                pending.addLast(entry);
            }
        }
        return entry;
    }

    synchronized Event current(long now, String mode) {
        pending.removeIf(entry -> !visible(mode, entry.error()) || now - entry.created() > 30_000);
        if (current != null && (now > expiresAt || !visible(mode, current.error()))) current = null;
        if (current == null && !pending.isEmpty()) {
            current = pending.removeFirst();
            expiresAt = now + (current.error() ? 6500 : 4200);
        }
        return current;
    }
    synchronized List<Event> history() { return List.copyOf(history); }
    synchronized void clearTransient() { current = null; pending.clear(); }
}
