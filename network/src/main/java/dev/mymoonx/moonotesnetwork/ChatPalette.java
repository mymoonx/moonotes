package dev.mymoonx.moonotesnetwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.function.Predicate;

/** Immutable searchable view of enabled catalogs. No chat messages or user identifiers are kept. */
final class ChatPalette {
    record Emote(String key, String id, String name, String channel) { }
    private static volatile List<Emote> items = List.of();
    private static final java.util.concurrent.atomic.AtomicLong REVISION=new java.util.concurrent.atomic.AtomicLong();
    private ChatPalette() { }
    static void publish(List<Emote> values) { var next=List.copyOf(values); if(!items.equals(next)){items=next;REVISION.incrementAndGet();} }
    static long revision(){return REVISION.get();}
    static List<Emote> snapshot() { return items; }

    static List<Emote> fromCatalogs(List<CatalogStore.Catalog> catalogs, Set<String> activeChannels) {
        // Use the same case-sensitive names and catalogue order as RegistrationPlan.
        // Inactive channels are not offered as insertable chat emotes.
        Map<String,Emote> indexed = new LinkedHashMap<>();
        for (CatalogStore.Catalog c : catalogs) {
            if (!c.enabled() || c.kind().equals("channel")
                    && !activeChannels.contains(c.reference().toLowerCase(Locale.ROOT))) continue;
            for (CatalogStore.Entry e : c.emotes()) {
                if (!LibraryPreferences.validKey(e.key())) continue;
                indexed.putIfAbsent(e.name(), new Emote(e.key(), e.id(), e.name(), c.name()));
            }
        }
        return List.copyOf(indexed.values());
    }

    static List<Emote> find(List<Emote> source, String query, int limit, Predicate<String> favorite,
                            ToLongFunction<String> lastUsed, String mode) {
        if (limit <= 0) return List.of();
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (needle.length() > 80) return List.of();
        return find(source, query, limit, favorite, lastUsed, mode, key -> false);
    }

    static List<Emote> find(List<Emote> source, String query, int limit, Predicate<String> favorite,
                            ToLongFunction<String> lastUsed, String mode, Predicate<String> inGroup) {
        if (limit <= 0) return List.of();
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (needle.length() > 80) return List.of();
        return source.stream().filter(e -> e.name().toLowerCase(Locale.ROOT).contains(needle))
            .filter(e -> switch (mode) {
                case "favorites" -> favorite.test(e.key());
                case "recent" -> lastUsed.applyAsLong(e.key()) > 0;
                case "group" -> inGroup.test(e.key());
                default -> true;
            })
            .sorted(Comparator.<Emote>comparingInt(e -> mode.equals("recent") ? 0 : favorite.test(e.key()) ? 0 : 1)
                .thenComparingInt(e -> e.name().toLowerCase(Locale.ROOT).startsWith(needle) ? 0 : 1)
                .thenComparingInt(e -> favorite.test(e.key()) ? 0 : 1)
                .thenComparing(Comparator.comparingLong((Emote e) -> lastUsed.applyAsLong(e.key())).reversed())
                .thenComparing(e -> e.name().toLowerCase(Locale.ROOT)))
            .limit(Math.min(limit, 2000)).toList();
    }
}
