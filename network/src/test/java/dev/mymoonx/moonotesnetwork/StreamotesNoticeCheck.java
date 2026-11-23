package dev.mymoonx.moonotesnetwork;

/** Original opt-outs, error severity and ordering in the shared on-screen stack. */
public final class StreamotesNoticeCheck {
    private static int checks;
    public static void main(String[] args) {
        require(StreamotesNoticePolicy.shouldRoute(true, true, "Toast"), "Original toast is not coordinated");
        require(StreamotesNoticePolicy.shouldRoute(true, true, "Chat"), "Original automatic chat notices are not coordinated");
        for (String mode : new String[]{"None", "", "future", null}) {
            require(!StreamotesNoticePolicy.shouldRoute(true, true, mode), "Original opt-out or unknown setting was overridden");
        }
        require(!StreamotesNoticePolicy.shouldRoute(false, true, "Toast"), "Hook suppressed notification before client initialization");
        require(!StreamotesNoticePolicy.shouldRoute(true, false, "Toast"), "Disabling coordination did not restore original behavior");

        var completed = StreamotesNoticePolicy.describe("Finished loading, 704 emotes from 2 channels", false);
        require(!completed.error() && completed.text().contains("704 названий"), "Registry completion was described as downloaded images");
        require(completed.text().contains("2 каналов"), "Channel count lost");
        var invalid = StreamotesNoticePolicy.describe("Received invalid emote config! Contact server admin.", false);
        require(invalid.error(), "Invalid server configuration disappeared in important-only mode");
        var failure = StreamotesNoticePolicy.describe("Failed to load 7TV global emotes", true);
        require(failure.error(), "Streamotes.loge severity lost while forwarding");
        require(StreamotesNoticePolicy.describe("Finished loading, 1 emotes from 1 channels", true).error(),
            "Recognized routine wording overrode an explicit logged error");
        require(!StreamotesNoticePolicy.describe("Received emote config, starting loading", false).error(), "Ordinary progress became an error");
        require(StreamotesNoticePolicy.describe(null, false).text().isEmpty(), "Null notification is not safe");
        require(!StreamotesNoticePolicy.describe("§aTest\n\u202E", false).text().contains("\u202E"), "Unsafe display control survived");

        var queue = new NotificationQueue();
        queue.add("Moonotes", "Каталог обновлён", false, 10_000, "toast", false);
        queue.add("Streamotes", completed.text(), completed.error(), 10_100, "toast", false);
        require(queue.current(10_100, "toast").title().equals("Moonotes"), "Second mod interrupted the active ordinary notice");
        require(queue.current(14_201, "toast").title().equals("Streamotes"), "Second mod was not queued after the first");
        require(queue.history().size() == 2, "Shared local history lost one source");
        queue.add("Streamotes", failure.text(), true, 14_300, "important", false);
        require(queue.current(14_300, "important").error(), "Original Streamotes error disappeared in important mode");
        require(queue.current(14_301, "off") == null, "Shared off mode left an original Streamotes popup");
        require(queue.history().getLast().title().equals("Streamotes"), "Origin was lost from event history");
        System.out.println("StreamotesNoticeCheck: " + checks + " assertions passed (original opt-outs, severity, shared queue and source labels)");
    }
    private static void require(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
}
