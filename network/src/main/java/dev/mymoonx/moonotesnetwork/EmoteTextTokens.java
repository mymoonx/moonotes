package dev.mymoonx.moonotesnetwork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact catalog-name matching for signs and books. A catalog allows many more
 * names than [A-Za-z0-9_]: emoji, Unicode letters and punctuation are valid.
 * This bounded trie reads code-unit offsets (as required by book editing).
 */
final class EmoteTextTokens {
    record Match(int start, int end, String id, String name) { }
    private static final int MAX_NAME_LENGTH = 80;
    private static final int MAX_MATCHES = 64;
    private static long indexedRevision = Long.MIN_VALUE;
    private static Trie indexed = new Trie();

    private EmoteTextTokens() { }

    static List<Match> find(String text) {
        long revision = ChatPalette.revision();
        if (revision != indexedRevision) {
            Map<String, ChatPalette.Emote> names = new LinkedHashMap<>();
            for (ChatPalette.Emote emote : ChatPalette.snapshot()) {
                if (names.size() >= 4096) break;
                names.putIfAbsent(emote.name(), emote);
            }
            indexed = index(names);
            indexedRevision = revision;
        }
        return find(text, indexed);
    }

    static List<Match> find(String text, Map<String, ChatPalette.Emote> names) {
        return find(text, index(names));
    }

    private static List<Match> find(String text, Trie root) {
        if (text == null || text.isEmpty() || text.length() > 4096 || root.children.isEmpty())
            return List.of();
        List<Match> matches = new ArrayList<>();
        for (int start = 0; start < text.length() && matches.size() < MAX_MATCHES;) {
            if (!startBoundary(text, start)) {
                start += Character.charCount(text.codePointAt(start));
                continue;
            }
            // Explicit colon-wrapped names consume both colons, not a partial alias.
            int contentStart = text.charAt(start) == ':' ? start + 1 : start;
            boolean wrapped = contentStart != start;
            Node node = root;
            ChatPalette.Emote winner = null;
            int winnerEnd = -1;
            for (int end = contentStart; end < text.length() && end - contentStart < MAX_NAME_LENGTH; end++) {
                node = node.children.get(text.charAt(end));
                if (node == null) break;
                if (node.emote != null) {
                    int afterName = end + 1;
                    if (wrapped) {
                        if (afterName < text.length() && text.charAt(afterName) == ':'
                                && endBoundary(text, afterName + 1)) {
                            winner = node.emote;
                            winnerEnd = afterName + 1;
                        }
                    } else if (endBoundary(text, afterName)) {
                        winner = node.emote;
                        winnerEnd = afterName;
                    }
                }
            }
            if (winner != null) {
                matches.add(new Match(start, winnerEnd, winner.id(), winner.name()));
                start = winnerEnd;
            } else {
                start += Character.charCount(text.codePointAt(start));
            }
        }
        return List.copyOf(matches);
    }

    private static Trie index(Map<String, ChatPalette.Emote> names) {
        Trie root = new Trie();
        if (names == null) return root;
        int limit = 0;
        for (var item : names.entrySet()) {
            if (++limit > 4096) break;
            String name = item.getKey();
            if (name == null || name.isEmpty() || name.length() > MAX_NAME_LENGTH || item.getValue() == null
                    || name.indexOf(':') >= 0 || name.codePoints().anyMatch(cp ->
                        Character.isWhitespace(cp) || Character.isISOControl(cp))) continue;
            Node node = root;
            for (int i = 0; i < name.length(); i++)
                node = node.children.computeIfAbsent(name.charAt(i), ignored -> new Node());
            if (node.emote == null) node.emote = item.getValue();
        }
        return root;
    }

    private static boolean startBoundary(String text, int at) {
        if (at == 0) return true;
        int previous = text.codePointBefore(at);
        return !Character.isLetterOrDigit(previous) && previous != '_' && previous != ':';
    }

    private static boolean endBoundary(String text, int at) {
        if (at >= text.length()) return true;
        int next = text.codePointAt(at);
        return !Character.isLetterOrDigit(next) && next != '_' && next != ':';
    }

    private static class Node {
        final Map<Character, Node> children = new HashMap<>();
        ChatPalette.Emote emote;
    }
    private static final class Trie extends Node { }
}
