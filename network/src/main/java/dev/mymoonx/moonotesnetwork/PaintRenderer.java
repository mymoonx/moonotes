package dev.mymoonx.moonotesnetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.util.UUID;

/** Narrow safe entry point for client-only paint mixins. */
public final class PaintRenderer {
    private PaintRenderer() { }
    public static Component decorateTab(Component name, UUID profileId) {
        Minecraft mc = Minecraft.getInstance();
        if (name == null || mc.player == null || profileId == null || !profileId.equals(mc.player.getUUID())) return name;
        if (!TwitchLink.local().linkedFor(mc.player.getUUID().toString())) return name;
        PaintSettings settings = PaintSettings.local();
        PaintStyle paint = settings.paintForTwitch(TwitchLink.local().twitchId());
        if (!settings.tab() || paint == null) return name;
        String text = name.getString();
        if (text.isEmpty() || text.length() > 128) return name;
        return paint.colored(text, settings.animation(), System.currentTimeMillis());
    }
    public static Component decorateLocalChat(Component original) {
        Minecraft mc = Minecraft.getInstance();
        if (original == null || mc.player == null || !original.getSiblings().isEmpty()) return original;
        if (!TwitchLink.local().linkedFor(mc.player.getUUID().toString())) return original;
        PaintSettings settings = PaintSettings.local();
        PaintStyle paint = settings.paintForTwitch(TwitchLink.local().twitchId());
        if (!settings.chat() || paint == null) return original;
        String player = mc.player.getGameProfile().name();
        String prefix = "<" + player + "> ";
        String body = original.getString();
        if (!body.startsWith(prefix) || body.length() > 512) return original;
        MutableComponent result = Component.literal("<");
        result.append(paint.colored(player, false, System.currentTimeMillis()));
        result.append(Component.literal("> " + body.substring(prefix.length())));
        return result;
    }
}
