package dev.mymoonx.moonotesnetwork;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure, deterministic name precedence. Existing Streamotes providers always keep their names. */
final class RegistrationPlan {
    record Item(String catalogKey, String channel, String id, String name, boolean zeroWidth,
                boolean enabled) { }
    record Plan(Map<String, Item> names, int duplicateNames) { }

    static Plan create(List<Item> entries, Set<String> worldChannels) {
        var names = new LinkedHashMap<String, Item>();
        int duplicates = 0;
        for (Item entry : entries) {
            if (!entry.enabled() || (!entry.channel().isEmpty()
                    && !worldChannels.contains(entry.channel().toLowerCase(Locale.ROOT)))) continue;
            Item previous = names.putIfAbsent(entry.name(), entry);
            if (previous != null && !previous.id().equalsIgnoreCase(entry.id())) duplicates++;
        }
        return new Plan(java.util.Collections.unmodifiableMap(names), duplicates);
    }

    static boolean sameImage(Object loadData, String id) {
        return loadData instanceof String existing && existing.equalsIgnoreCase(id);
    }

    /** Caller owns the registry lock. Emoticon.equals uses the name, not object identity. */
    static boolean removeIdentity(Map<?, ?> registry, Object key, Object expected) {
        if (registry.get(key) != expected) return false;
        registry.remove(key);
        return true;
    }

    private RegistrationPlan() { }
}
