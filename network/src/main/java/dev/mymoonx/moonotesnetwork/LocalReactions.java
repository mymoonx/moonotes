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

    /** Returns whether the action produced an actual local reaction, not merely a click. */
    static boolean show(ChatPalette.Emote emote) {
        Minecraft mc = Minecraft.getInstance();
        if (emote == null || mc.player == null || !ExtrasSettings.local().reactions()) return false;
        if (!showFor(mc.player.getUUID(), emote, true)) return false;
        MoonotesTelemetry.count(MoonotesTelemetry.Event.LOCAL_REACTION);
        LibraryPreferences.local().used(emote.key());
        return true;
    }

    static boolean showFor(UUID player, ChatPalette.Emote emote) {
        return showFor(player, emote, false);
    }

    private static boolean showFor(UUID player, ChatPalette.Emote emote, boolean manual) {
        if (player == null || emote == null || !valid(emote.name())
                || emote.id() == null || !emote.id().matches("[a-zA-Z0-9]{16,40}")
                || !ExtrasSettings.local().reactions()) return false;
        long now = System.currentTimeMillis();
        Visual previous = ACTIVE.get(player);
        // A deliberate right-click should always replace/refresh your reaction.
        // Incoming chat still has a cooldown to prevent a flood of billboards.
        if (!manual && previous != null && now < previous.until() - 1200) return false;
        if (ACTIVE.size() >= MAX) ACTIVE.entrySet().removeIf(e -> e.getValue().until() <= now);
        if (ACTIVE.size() >= MAX && previous == null) return false;
        ACTIVE.put(player, new Visual(player, emote.name(), emote.id(),
            now + ExtrasSettings.local().reactionSeconds() * 1000L));
        return true;
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
