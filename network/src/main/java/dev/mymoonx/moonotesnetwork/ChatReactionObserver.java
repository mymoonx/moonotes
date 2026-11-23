package dev.mymoonx.moonotesnetwork;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Best-effort visual reactions from ordinary player chat. Only the already
 * displayed PLAYER chat channel is observed; the message remains unchanged.
 * A decorated/renamed server chat format may not match and is ignored.
 */
public final class ChatReactionObserver {
    private static final Pattern VANILLA = Pattern.compile("^<([A-Za-z0-9_]{3,16})> (.{1,256})$");
    private static volatile long cachedRevision = -1;
    private static volatile Map<String,ChatPalette.Emote> names = Map.of();

    private ChatReactionObserver() { }

    static String[] parseVanillaLine(String text) {
        if (text == null || text.length() > 300) return null;
        Matcher matched = VANILLA.matcher(text);
        return matched.matches() ? new String[]{matched.group(1), matched.group(2)} : null;
    }

    public static void observe(Component message) {
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
        if (author == null || author.isInvisible()) return;
        if (author.distanceToSqr(mc.player) > 32 * 32) return;
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
        String body = parsed[1];
        int used = 0;
        for (String word : body.split("\\s+")) {
            if (++used > 32) break;
            if (word.startsWith(":") && word.endsWith(":") && word.length() > 2)
                word = word.substring(1, word.length() - 1);
            ChatPalette.Emote emote = names.get(word);
            if (emote != null) {
                LocalReactions.showFor(author.getUUID(), emote.name());
                return;
            }
        }
    }
}
