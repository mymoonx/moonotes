package dev.mymoonx.moonotesnetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;

/** Draft insertion and the wheel's explicit single-message send action. */
final class ChatInsertion {
    private ChatInsertion() { }
    static String append(String draft,String name) {
        if(name==null || name.isBlank() || name.startsWith("/") || name.length()>80
                || name.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)||Character.getType(c)==Character.FORMAT||c=='§'))
            throw new IllegalArgumentException("Invalid emote name");
        return appendToken(draft,name);
    }
    static String appendToken(String draft,String name) {
        if(name==null || name.length()>82)throw new IllegalArgumentException("Invalid chat token");
        String initial=draft==null?"":draft;
        String text=initial+(initial.isEmpty()||Character.isWhitespace(initial.charAt(initial.length()-1))?"":" ")+name+" ";
        if(text.length()>256)throw new IllegalArgumentException("В черновике недостаточно места. Название можно скопировать.");
        return text;
    }
    private static long lastSent;

    /** Explicit left-click in the emote wheel sends exactly one emote name. */
    static void send(String name) {
        send(name, WheelSettings.Channel.LOCAL);
    }

    static void send(String name, WheelSettings.Channel channel) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.connection == null)
            throw new IllegalArgumentException("Сначала войдите в мир");
        append("", name); // Reject commands, whitespace and control characters.
        String message = forChannel(StreamotesBridge.chatText(name), channel);
        long now = System.currentTimeMillis();
        if (now - lastSent < 600) throw new IllegalArgumentException("Подождите перед следующей отправкой");
        lastSent = now;
        client.player.connection.sendChat(message);
        MoonotesTelemetry.count(MoonotesTelemetry.Event.CHAT_SEND);
    }

    static String forChannel(String token, WheelSettings.Channel channel) {
        if (channel == null || token == null || token.isBlank() || token.length() > 82
            || token.startsWith("/") || token.startsWith("!")
            || token.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c)
                || Character.getType(c) == Character.FORMAT || c == '§'))
            throw new IllegalArgumentException("Некорректное название эмота");
        return (channel == WheelSettings.Channel.GLOBAL ? WheelSettings.activePrefix() : "") + token;
    }

    static String appendForChannel(String draft, String token, WheelSettings.Channel channel) {
        forChannel(token, channel); // Validate the formatted token before editing a draft.
        String initial = draft == null ? "" : draft;
        // The server uses a single leading exclamation mark to route the whole message.
        String configured=WheelSettings.activePrefix();
        if (initial.startsWith(configured)) initial=initial.substring(configured.length());
        if (initial.startsWith("/")) throw new IllegalArgumentException("В черновике команда; сначала завершите её");
        String result = (channel == WheelSettings.Channel.GLOBAL ? configured : "") + appendToken(initial, token);
        if (result.length() > 256) throw new IllegalArgumentException("В черновике недостаточно места");
        return result;
    }

    static void open(String name) {
        Minecraft client=Minecraft.getInstance();
        if(client.player==null)throw new IllegalArgumentException("Сначала войдите в мир.");
        append("",name); // Validate the catalogue name before applying the user's Streamotes format.
        String token=StreamotesBridge.chatText(name);
        ChatScreen chat=client.gui.hud.getChat().createScreen(ChatComponent.ChatMethod.MESSAGE,
            (draft,isDraft)->new ChatScreen(appendToken(draft,token),false));
        client.gui.setScreen(chat);
    }

    static void open(String name, WheelSettings.Channel channel) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) throw new IllegalArgumentException("Сначала войдите в мир");
        append("", name);
        String token = StreamotesBridge.chatText(name);
        ChatScreen chat = client.gui.hud.getChat().createScreen(ChatComponent.ChatMethod.MESSAGE,
            (draft, isDraft) -> new ChatScreen(appendForChannel(draft, token, channel), false));
        client.gui.setScreen(chat);
    }
}
