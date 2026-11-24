package dev.mymoonx.moonotesnetwork;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exact active-catalog tokens for non-chat displays. Contains no rendering or glyph markers. */
final class EmoteTextTokens {
    record Match(int start, int end, String id, String name) { }
    private static final Pattern TOKEN = Pattern.compile(
            "(?<![A-Za-z0-9_:])(?::([A-Za-z0-9_]{1,70}):|([A-Za-z0-9_]{1,70}))(?![A-Za-z0-9_:])");
    private static long indexedRevision = Long.MIN_VALUE;
    private static Map<String, ChatPalette.Emote> byName = Map.of();

    private EmoteTextTokens() { }

    static List<Match> find(String text) {
        long revision = ChatPalette.revision();
        if (revision != indexedRevision) {
            Map<String, ChatPalette.Emote> next = new LinkedHashMap<>();
            for (ChatPalette.Emote emote : ChatPalette.snapshot()) next.putIfAbsent(emote.name(), emote);
            byName = Map.copyOf(next);
            indexedRevision = revision;
        }
        return find(text, byName);
    }

    static List<Match> find(String text, Map<String, ChatPalette.Emote> names) {
        if (text == null || text.isEmpty() || text.length() > 4096 || names.isEmpty()) return List.of();
        List<Match> matches = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find() && matches.size() < 64) {
            String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            ChatPalette.Emote emote = names.get(name);
            if (emote != null) matches.add(new Match(matcher.start(), matcher.end(), emote.id(), emote.name()));
        }
        return List.copyOf(matches);
    }
}
