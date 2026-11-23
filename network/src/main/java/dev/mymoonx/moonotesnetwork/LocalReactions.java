package dev.mymoonx.moonotesnetwork;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** Short-lived, local-only emote reactions. Never transmits server packets. */
final class LocalReactions {
    private LocalReactions() {}
    private record Reaction(String name, long until) { }
    private static final Map<UUID, Reaction> ACTIVE = new ConcurrentHashMap<>();

    static boolean valid(String name) {
        return name != null && name.length() >= 2 && name.length() <= 80
            && name.matches("[^\\s\\p{Cntrl}\\p{Cf}§/]{2,80}");
    }

    static void show(ChatPalette.Emote emote) {
        Minecraft minecraft = Minecraft.getInstance();
        if (emote == null || minecraft.player == null || !ExtrasSettings.local().reactions()) return;
        showFor(minecraft.player.getUUID(), emote.name());
        LibraryPreferences.local().used(emote.key());
    }

    static void showFor(UUID player, String name) {
        if (player == null || !valid(name) || !ExtrasSettings.local().reactions()) return;
        long now = System.currentTimeMillis();
        Reaction previous = ACTIVE.get(player);
        if (previous != null && now < previous.until() - 1500) return; // per-player cooldown
        if (ACTIVE.size() >= 48) ACTIVE.entrySet().removeIf(e -> e.getValue().until() <= now);
        if (ACTIVE.size() >= 48) return;
        ACTIVE.put(player, new Reaction(name, now + ExtrasSettings.local().reactionSeconds() * 1000L));
    }

    static String current(UUID player, long now) {
        if (player == null || !ExtrasSettings.local().reactions()) return "";
        Reaction reaction = ACTIVE.get(player);
        if (reaction == null || now >= reaction.until() || now < reaction.until() - 7000) return "";
        return reaction.name();
    }

    static void clear() { ACTIVE.clear(); }
}
