package dev.mymoonx.moonotesnetwork;

import java.util.Map;

/** A native Streamotes emote must be visible before Network's Api is ready. */
public final class StreamotesRegistryDirectLookupCheck {
    public static void main(String[] args) {
        require(StreamotesBridge.registeredEmoteName("WW"), "native WW independent of Network API");
        require(StreamotesBridge.registeredEmoteName("!vanish"), "exact !vanish available");
        require(!StreamotesBridge.registeredEmoteName("!WW"), "no exact !WW");
        require(!StreamotesBridge.registeredEmoteName(""), "empty invalid");
        var matches = GlobalChatPrefix.matches("[G] !WW", "!", StreamotesBridge::registeredEmoteName);
        require(matches.size() == 1 && matches.getFirst().name().equals("WW"),
            "chat rendering resolves WW through direct registry lookup");
        require(GlobalChatPrefix.matches("!vanish", "!",
            StreamotesBridge::registeredEmoteName).isEmpty(), "registered !vanish stays intact");
        System.out.println("StreamotesRegistryDirectLookupCheck: PASS");
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
