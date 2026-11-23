package dev.mymoonx.moonotesnetwork;

import java.util.regex.Pattern;

/** Small policy shared by the compatibility hook and regression checks. */
final class StreamotesNoticePolicy {
    private static final Pattern COMPLETED = Pattern.compile("Finished loading, ([0-9]{1,9}) emotes from ([0-9]{1,7}) channels");
    record Notice(String text, boolean error) { }

    private StreamotesNoticePolicy() { }

    static boolean shouldRoute(boolean ready, boolean enabled, String originalReporting) {
        // None, null and future reporting modes retain Streamotes' own behavior.
        return ready && enabled && ("Toast".equals(originalReporting) || "Chat".equals(originalReporting));
    }

    static Notice describe(String raw, boolean loggedError) {
        String text = NotificationQueue.clean(raw, 300);
        if (loggedError) return new Notice(text, true);
        return switch (text) {
            case "Received emote config, starting loading" ->
                new Notice("Получены настройки сервера. Обновляется список эмотов.", false);
            case "Force cache clear requested, clearing cache" ->
                new Notice("Сервер запросил очистку кеша Streamotes.", false);
            case "Received invalid emote config! Contact server admin." ->
                new Notice("Сервер прислал некорректные настройки эмотов. Обратитесь к администратору.", true);
            default -> {
                var completed = COMPLETED.matcher(text);
                if (completed.matches()) yield new Notice("Список обновлён: " + completed.group(1)
                    + " названий из " + completed.group(2) + " каналов.", false);
                yield new Notice(text, false);
            }
        };
    }
}
