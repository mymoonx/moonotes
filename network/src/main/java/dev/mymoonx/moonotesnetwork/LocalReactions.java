package dev.mymoonx.moonotesnetwork;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

/**
 * Temporary, client-side reaction visuals. Never sends an entity packet.
 * Each reaction remembers the actual cached emote ID for drawing a sprite.
 */
final class LocalReactions {
    record Visual(UUID player, String name, String id, long until) { }
    private static final int MAX = 24;
    private static final Map<UUID, Visual> ACTIVE = new ConcurrentHashMap<>();
    private LocalReactions() { }

    static boolean valid(String name) {
        return name != null && name.length() >= 2 && name.length() <= 80
            && name.matches("[^\\s\\p{Cntrl}\\p{Cf}§/]{2,80}");
    }

    static void show(ChatPalette.Emote emote) {
        Minecraft mc = Minecraft.getInstance();
        if (emote == null || mc.player == null || !ExtrasSettings.local().reactions()) return;
        showFor(mc.player.getUUID(), emote);
        MoonotesTelemetry.count(MoonotesTelemetry.Event.LOCAL_REACTION);
        LibraryPreferences.local().used(emote.key());
    }

    static void showFor(UUID player, ChatPalette.Emote emote) {
        if (player == null || emote == null || !valid(emote.name())
                || emote.id() == null || !emote.id().matches("[a-zA-Z0-9]{16,40}")
                || !ExtrasSettings.local().reactions()) return;
        long now = System.currentTimeMillis();
        Visual previous = ACTIVE.get(player);
        if (previous != null && now < previous.until() - 1200) return; // avoid chat floods
        if (ACTIVE.size() >= MAX) ACTIVE.entrySet().removeIf(e -> e.getValue().until() <= now);
        if (ACTIVE.size() >= MAX) return;
        ACTIVE.put(player, new Visual(player, emote.name(), emote.id(),
            now + ExtrasSettings.local().reactionSeconds() * 1000L));
    }

    static List<Visual> active(long now) {
        if (!ExtrasSettings.local().reactions()) return List.of();
        ACTIVE.entrySet().removeIf(e -> e.getValue().until() <= now);
        return List.copyOf(ACTIVE.values());
    }

    static String current(UUID player, long now) {
        if (player == null) return "";
        Visual item = ACTIVE.get(player);
        return item != null && ExtrasSettings.local().reactions() && item.until() > now
            ? item.name() : "";
    }

    static void clear() { ACTIVE.clear(); }
}
