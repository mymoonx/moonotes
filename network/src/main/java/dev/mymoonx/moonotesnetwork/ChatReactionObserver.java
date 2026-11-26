package dev.mymoonx.moonotesnetwork;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Visual reactions from displayed chat. Player packets supply the author UUID,
 * independently of server colours, ranks and local/global chat decoration.
 * System/plugin messages have only a strict vanilla-format fallback; arbitrary
 * announcements are never attributed by finding a player name in their text.
 */
public final class ChatReactionObserver {
    private static final Pattern VANILLA = Pattern.compile("^<([A-Za-z0-9_]{3,16})> (.{1,256})$");
    private static volatile long cachedRevision = -1;
    private static volatile Map<String,ChatPalette.Emote> names = Map.of();
    private static boolean registered;

    private ChatReactionObserver() { }

    static void register() {
        if (registered) return;
        registered = true;
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, parameters, timestamp) -> {
            if (sender == null || message == null) {
                observeVanilla(message);
                return;
            }
            String body;
            if (signed != null) {
                if (signed.isFullyFiltered()) return;
                body = signed.filterMask().apply(signed.signedContent());
            } else {
                body = message.getString();
                String[] vanilla = parseVanillaLine(body);
                if (vanilla != null && sender.name().equals(vanilla[0])) body = vanilla[1];
            }
            observe(sender.id(), body);
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) observeVanilla(message);
        });
    }

    static String[] parseVanillaLine(String text) {
        if (text == null || text.length() > 300) return null;
        Matcher matched = VANILLA.matcher(text);
        return matched.matches() ? new String[]{matched.group(1), matched.group(2)} : null;
    }

    private static void observeVanilla(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ExtrasSettings.local().reactions()
                || message == null) return;
        String[] parsed = parseVanillaLine(message.getString());
        if (parsed == null) return;
        Player author = null;
        for (Player player : mc.level.players()) {
            if (player.getGameProfile().name().equals(parsed[0])) {
                author = player;
                break;
            }
        }
        if (author != null) observe(author.getUUID(), parsed[1]);
    }

    private static void observe(UUID authorId, String body) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ExtrasSettings.local().reactions()
                || authorId == null || body == null || body.length() > 256) return;
        Player author = mc.level.getPlayerByUUID(authorId);
        if (author == null || author.isInvisible()) return;
        // Visibility and perspective are checked from the actual camera by the renderer.
        long revision = ChatPalette.revision();
        if (revision != cachedRevision) {
            Map<String,ChatPalette.Emote> current = new HashMap<>();
            for (ChatPalette.Emote emote : ChatPalette.snapshot()) {
                if (current.size() >= 4096) break;
                current.putIfAbsent(emote.name(), emote);
            }
            names = Map.copyOf(current);
            cachedRevision = revision;
        }
        ChatPalette.Emote emote = findEmote(body, names);
        if (emote != null) LocalReactions.showFor(authorId, emote);
    }

    static ChatPalette.Emote findEmote(String body, Map<String,ChatPalette.Emote> available) {
        if (body == null || body.length() > 256) return null;
        String prefix = WheelSettings.activePrefix();
        boolean routed = prefix != null && !prefix.isEmpty() && body.startsWith(prefix);
        int used = 0;
        for (String word : body.split("\\s+")) {
            if (++used > 32) break;
            if (word.startsWith(":") && word.endsWith(":") && word.length() > 2)
                word = word.substring(1, word.length() - 1);
            // Preserve real emotes named !vanish or !setgame ahead of the
            // global-chat prefix interpretation.
            ChatPalette.Emote exact = available.get(word);
            if (exact != null) return exact;
            if (routed && used == 1 && word.startsWith(prefix)) {
                String suffix = word.substring(prefix.length());
                if (suffix.startsWith(":") && suffix.endsWith(":") && suffix.length() > 2)
                    suffix = suffix.substring(1, suffix.length() - 1);
                ChatPalette.Emote stripped = available.get(suffix);
                if (stripped != null) return stripped;
            }
        }
        return null;
    }
}
