package dev.mymoonx.moonotesnetwork;

import java.util.UUID;
import net.minecraft.client.Minecraft;

/** Short-lived, local-only emote reactions. Never transmits server packets. */
final class LocalReactions {
    private LocalReactions() {}
    private static UUID activePlayer;
    private static String emoteName = "";
    private static long expires;

    static boolean valid(String name) {
        return name != null && name.length() >= 2 && name.length() <= 80
            && name.matches("[^\\s\\p{Cntrl}\\p{Cf}§/]{2,80}");
    }

    static void show(ChatPalette.Emote emote) {
        Minecraft minecraft = Minecraft.getInstance();
        if (emote == null || minecraft.player == null || !ExtrasSettings.local().reactions()) return;
        if (!valid(emote.name())) return;
        activePlayer = minecraft.player.getUUID();
        emoteName = emote.name();
        expires = System.currentTimeMillis() + ExtrasSettings.local().reactionSeconds() * 1000L;
        LibraryPreferences.local().used(emote.key());
    }

    static String current(UUID player, long now) {
        if (player == null || !ExtrasSettings.local().reactions() || !player.equals(activePlayer)
                || now >= expires || now < expires - 7000L) return "";
        return emoteName;
    }

    static void clear() {
        activePlayer = null;
        emoteName = "";
        expires = 0;
    }
}
