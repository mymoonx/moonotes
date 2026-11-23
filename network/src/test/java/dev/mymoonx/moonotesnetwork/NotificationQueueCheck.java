package dev.mymoonx.moonotesnetwork;

/** Notification modes, queue priority and the local-only bounded journal. */
public final class NotificationQueueCheck {
    private static int checks;
    public static void main(String[] args) {
        require("toast".equals(NotificationQueue.mode(null)), "Legacy/null mode broke defaults");
        require("toast".equals(NotificationQueue.mode("invalid")), "Invalid config mode persisted");
        require(!NotificationQueue.visible("off", true), "Off mode shows errors");
        require(!NotificationQueue.visible("chat", true), "Chat mode duplicates a visual notice");
        require(!NotificationQueue.visible("important", false) && NotificationQueue.visible("important", true), "Important mode filter");
        var queue = new NotificationQueue();
        queue.add("Каталог", "Доступен", false, 10000, "toast", false);
        require(queue.current(10000, "toast") != null, "Ordinary visual event lost");
        require(queue.add("Каталог", "Доступен", false, 11000, "toast", false) == null, "Repeated event not deduplicated");
        queue.add("Ошибка", "Нет сети", true, 12000, "toast", false);
        queue.add("Ошибка", "Проверка запущена", false, 13000, "toast", false);
        require(queue.current(13000, "toast").error(), "Routine update hid the current error");
        queue.add("Другой статус", "Готово", false, 14000, "toast", false);
        require(queue.current(14000, "important").error(), "Changing to important hid an error");
        require(queue.current(19000, "important") == null, "Old queued routine message leaked through important mode");
        queue.add("Статус", "Выключено", false, 20000, "off", false);
        require(queue.current(20000, "off") == null, "Off mode retained a visual queue");
        require(queue.history().size() == 5, "Journal missing suppressed/local events");
        queue.add("Загрузка", "Начата", false, 24000, "toast", true);
        require(queue.current(24000, "toast") == null, "HUD download was duplicated by a toast");
        for (int i = 0; i < 100; i++) queue.add("Событие", "Номер " + i, false, 25000 + i, "off", false);
        require(queue.history().size() == 40, "History growth is not bounded");
        require(queue.history().getFirst().message().equals("Номер 60"), "History does not evict oldest events");
        String cleaned = NotificationQueue.clean("§aHello\nworld\u202E", 40);
        require(!cleaned.contains("§") && !cleaned.contains("\n") && !cleaned.contains("\u202E"), "Unsafe display controls survived");
        String unicode = NotificationQueue.clean("😀😀😀", 2);
        require(unicode.equals("😀…"), "Truncation split a Unicode surrogate pair");
        queue.clearTransient();
        require(queue.current(50000, "toast") == null && queue.history().size() == 40, "Clearing overlay deleted journal");
        System.out.println("NotificationQueueCheck: " + checks + " assertions passed (modes, deduplication, priority, bounded journal)");
    }
    private static void require(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
