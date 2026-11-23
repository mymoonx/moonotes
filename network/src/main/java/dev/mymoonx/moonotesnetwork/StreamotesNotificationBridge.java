package dev.mymoonx.moonotesnetwork;

/**
 * Routes only Streamotes.msg, its automatic notification entry point.
 * The original mod, its logging and all other Minecraft toasts stay independent.
 */
public final class StreamotesNotificationBridge {
    private static final StackWalker CALLER = StackWalker.getInstance();

    private StreamotesNotificationBridge() { }

    /** Return true only after the shared queue accepts responsibility for this notice. */
    public static boolean intercept(String message, String originalReporting) {
        if (!StreamotesNoticePolicy.shouldRoute(MoonotesNotifications.isRegistered(),
            MoonotesNetworkClient.unifiedStreamotesNotifications(), originalReporting)) return false;
        try {
            // Resolve severity synchronously, before dispatching to the client thread.
            // No stack, account, world or network data is saved by this compatibility hook.
            boolean loggedError = CALLER.walk(frames -> frames.limit(16).anyMatch(frame ->
                frame.getClassName().equals("xeed.mc.streamotes.Streamotes") && frame.getMethodName().equals("loge")));
            var notice = StreamotesNoticePolicy.describe(message, loggedError);
            if (!notice.text().isBlank()) MoonotesNotifications.notifyUser("Streamotes", notice.text(), notice.error());
            return true;
        } catch (LinkageError | RuntimeException unavailable) {
            // A future Streamotes API change must leave its original notification intact.
            return false;
        }
    }

}
