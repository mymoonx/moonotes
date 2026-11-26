package dev.mymoonx.moonotesnetwork;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** A global ! router must not steal the first character from a real !emote. */
public final class GlobalChatPrefixCheck {
    private static int checks;
    private static void check(boolean actual, String what) {
        checks++;
        if (!actual) throw new AssertionError(what);
    }

    public static void main(String[] args) {
        var names = Map.of(
            "WW", "ww", "vanish", "v", "!vanish", "!v",
            "setgame", "s", "!setgame", "!s",
            "catJAM", "c");
        Predicate<String> known = names::containsKey;
        var ordinary = GlobalChatPrefix.matches("!WW", "!", known);
        check(ordinary.size() == 1, "!WW needs one split");
        var match = ordinary.getFirst();
        check(match.prefixStart() == 0 && match.contentStart() == 1
            && match.end() == 3 && match.name().equals("WW"), "prefix is separate from WW");
        check(GlobalChatPrefix.matches("! WW", "!", known).isEmpty(),
            "! WW already renders WW normally");
        check(GlobalChatPrefix.matches("!vanish", "!", known).isEmpty(),
            "exact !vanish must have priority over vanish");
        check(GlobalChatPrefix.matches("!setgame", "!", known).isEmpty(),
            "exact !setgame must have priority over setgame");
        check(GlobalChatPrefix.matches("!unknown", "!", known).isEmpty(),
            "unknown names stay ordinary text");
        var embedded = GlobalChatPrefix.matches("<Steve> !WW", "!", known);
        check(embedded.size() == 1 && embedded.getFirst().contentStart() == 9,
            "displayed chat with player prefix");
        var custom = GlobalChatPrefix.matches("#WW", "#", known);
        check(custom.size() == 1 && custom.getFirst().name().equals("WW"),
            "user-defined global prefix");
        check(GlobalChatPrefix.suggestion("!WW", "!", known).equals("WW"),
            "suggest the unprefixed emote");
        check(GlobalChatPrefix.suggestion("!vanish", "!", known).equals("!vanish"),
            "suggest full exact alias");
        check(GlobalChatPrefix.preservePrefix("!WW", 0, "WW", "!"),
            "keep global prefix on selected WW");
        check(!GlobalChatPrefix.preservePrefix("!vanish", 0, "!vanish", "!"),
            "do not double-prefix literal !vanish");

        ChatPalette.publish(List.of(
            new ChatPalette.Emote("a", "1234567890123456", "WW", "set"),
            new ChatPalette.Emote("b", "1234567890123457", "vanish", "set"),
            new ChatPalette.Emote("c", "1234567890123458", "!vanish", "set")));
        check(ChatTextEdit.activePrefix("!WW", 3).equals("WW"),
            "chat suggestions ignore the global router");
        check(ChatTextEdit.activePrefix("!vanish", 7).equals("!vanish"),
            "chat suggestions respect exact !alias");
        check(ChatTextEdit.insert("!WW", 3, "WW", true, false).text().equals("!WW "),
            "Tab completion preserves ! without adding a space");
        check(ChatTextEdit.insert("!WW then", 3, "WW", true, false).text().equals("!WW then"),
            "Tab completion preserves following text");
        check(ChatTextEdit.insert("!WW", 3, "WW", true, true).text().equals("!:WW: "),
            "required-colon Streamotes mode retains global !");
        check(ChatTextEdit.insert("!vanish", 7, "!vanish", true, false)
            .text().equals("!vanish "), "full !alias is never doubled");
        check(ChatTextEdit.insert("! WW", 4, "WW", true, false).text().equals("! WW "),
            "space-separated global form unchanged");
        check(ChatTextEdit.activePrefix("/say !WW", 8).isEmpty(),
            "unrelated commands remain untouched");
        Map<String, ChatPalette.Emote> forReactions = Map.of(
            "WW", new ChatPalette.Emote("ww", "1234567890123456", "WW", "set"),
            "!vanish", new ChatPalette.Emote("v", "1234567890123457", "!vanish", "set"),
            "vanish", new ChatPalette.Emote("v2", "1234567890123458", "vanish", "set"),
            "!setgame", new ChatPalette.Emote("s", "1234567890123459", "!setgame", "set"));
        check(ChatReactionObserver.findEmote("!WW", forReactions).name().equals("WW"),
            "chat reaction observes global !WW as WW");
        check(ChatReactionObserver.findEmote("!vanish", forReactions).name().equals("!vanish"),
            "chat reaction prefers exact !vanish");
        check(ChatReactionObserver.findEmote("!setgame", forReactions).name().equals("!setgame"),
            "chat reaction preserves !setgame");
        check(ChatReactionObserver.findEmote("! WW", forReactions).name().equals("WW"),
            "chat reaction supports legacy spaced global prefix");
        System.out.println("GlobalChatPrefixCheck: " + checks + " checks passed");
    }
}
