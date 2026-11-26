package dev.mymoonx.moonotesnetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The first character of !WW is both a global-chat router and a possible
 * emote name character. Full, registered aliases (e.g. !vanish) always win.
 * Parsing affects rendering/suggestions ONLY; the Minecraft chat payload
 * stays byte-for-byte unchanged.
 */
public final class GlobalChatPrefix {
    private static final Pattern WORD = Pattern.compile("[^\\s:]{2,}|:?[^\\s:]+:?");
    private static final int MAX_INPUT = 4096;
    private static final int MAX_MATCHES = 32;
    public record Match(int prefixStart, int contentStart, int end, String name) { }

    private GlobalChatPrefix() { }

    public static List<Match> matches(String content, String globalPrefix, Predicate<String> registered) {
        if (content == null || globalPrefix == null || globalPrefix.isEmpty()
                || content.length() > MAX_INPUT || registered == null) return List.of();
        Matcher words = WORD.matcher(content);
        List<Match> found = new ArrayList<>();
        while (words.find() && found.size() < MAX_MATCHES) {
            String full = words.group();
            if (!full.startsWith(globalPrefix) || full.length() <= globalPrefix.length()
                    || full.indexOf(':') >= 0) continue;
            // An emote whose *actual name* begins with ! is not a global message.
            if (registered.test(full)) continue;
            String suffix = full.substring(globalPrefix.length());
            // ! is the server's global-chat router. !w!/!h!/!v!/!z!
            // are NOT regular !<emote> tokens. Keep them intact for
            // BttvChatText, which will style the subsequent emote.
            if (BttvModifierSyntax.isOperatorToken(suffix)) continue;
            if (registered.test(suffix)) {
                found.add(new Match(words.start(), words.start() + globalPrefix.length(),
                        words.end(), suffix));
            }
        }
        return List.copyOf(found);
    }

    static String suggestion(String token, String globalPrefix,
            Predicate<String> registered) {
        if (token == null || token.isEmpty() || globalPrefix == null
                || globalPrefix.isEmpty() || registered == null) return token;
        if (!token.startsWith(globalPrefix) || token.length() <= globalPrefix.length()
                || registered.test(token)) return token;
        String suffix = token.substring(globalPrefix.length());
        return BttvModifierSyntax.isOperatorToken(suffix) ? "" : suffix;
    }

    static boolean preservePrefix(String draft, int wordStart, String replacement,
            String globalPrefix) {
        if (draft == null || globalPrefix == null || globalPrefix.isBlank()
                || replacement == null || replacement.startsWith(globalPrefix)
                || wordStart < 0 || wordStart + globalPrefix.length() > draft.length())
            return false;
        int first = 0;
        while (first < draft.length() && Character.isWhitespace(draft.charAt(first))) first++;
        return first == wordStart && draft.startsWith(globalPrefix, wordStart);
    }
}
