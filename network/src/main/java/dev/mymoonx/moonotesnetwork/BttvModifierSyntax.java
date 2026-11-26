package dev.mymoonx.moonotesnetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exact BTTV prefix notation. A modifier only applies to the next registered
 * emote when separated by whitespace. No chat payload or catalog is changed.
 *
 * The display parser is independent of the transport, chat command suggestions
 * and graphical renderer so it can be checked without starting Minecraft.
 */
public final class BttvModifierSyntax {
    private static final Pattern PAIR =
        Pattern.compile("(?<!\\S)!?([whvz]!)\\s+([^\\s:]{1,80})(?=\\s|$)");
    private static final int MAX_TEXT = 4096;
    private static final int MAX_PAIRS = 32;

    public enum Modifier {
        WIDE('w'), HORIZONTAL('h'), VERTICAL('v'), ZERO_SPACE('z');
        private final char code;
        Modifier(char code) { this.code = code; }
        public char code() { return code; }
        public static Modifier fromCode(char value) {
            return switch (value) {
                case 'w' -> WIDE;
                case 'h' -> HORIZONTAL;
                case 'v' -> VERTICAL;
                case 'z' -> ZERO_SPACE;
                default -> null;
            };
        }
    }

    public record Match(int start, int emoteStart, int end, String emote, Modifier modifier) { }

    /** Exact BTTV control tokens: never interpret them as ordinary emote aliases. */
    static boolean isOperatorToken(String token) {
        return "w!".equals(token) || "h!".equals(token)
            || "v!".equals(token) || "z!".equals(token);
    }

    private BttvModifierSyntax() { }

    public static List<Match> find(String text, Predicate<String> registered) {
        if (text == null || text.length() > MAX_TEXT || registered == null) return List.of();
        Matcher matcher = PAIR.matcher(text);
        var matches = new ArrayList<Match>();
        while (matcher.find() && matches.size() < MAX_PAIRS) {
            String emote = matcher.group(2);
            if (!registered.test(emote)) continue;
            Modifier modifier = Modifier.fromCode(matcher.group(1).charAt(0));
            if (modifier == null) continue;
            // Start at the BTTV operator, not the optional leading ! chat router.
            // This preserves the server-owned global prefix in both input and display.
            matches.add(new Match(matcher.start(1), matcher.start(2), matcher.end(), emote, modifier));
        }
        return List.copyOf(matches);
    }

    /** The rendering-only marker lives in Style.insertion, never in sent text. */
    static String marker(Modifier modifier) {
        return modifier == null ? "" : "moonotes:bttv:" + modifier.code();
    }

    static Modifier marked(String insertion) {
        if (insertion == null || !insertion.startsWith("moonotes:bttv:") ||
                insertion.length() != "moonotes:bttv:".length() + 1) return null;
        return Modifier.fromCode(insertion.charAt(insertion.length() - 1));
    }
}
