package dev.mymoonx.moonotesnetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;

/** Only edits a local chat draft; this class never invokes a send/command API. */
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
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.connection == null)
            throw new IllegalArgumentException("No active Minecraft connection");
        append("", name); // Reject commands, whitespace and control characters.
        String token = StreamotesBridge.chatText(name);
        if (token.isBlank() || token.length() > 256 || token.charAt(0) == '/'
            || token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Invalid chat text");
        long now = System.currentTimeMillis();
        if (now - lastSent < 600) throw new IllegalArgumentException("Too many emotes");
        lastSent = now;
        client.player.connection.sendChat(token);
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
}
