package dev.mymoonx.moonotesnetwork;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Last-stage styling hook for the chat EditBox. Minecraft command syntax
 * highlighting can supersede Moonotes' earlier addFormatter(), so PM emotes
 * need to be applied after EditBox.applyFormat() returns.
 *
 * Only instances registered by ChatPickerMixin are eligible; all other text
 * fields, commands, and chat-packet payloads are left untouched.
 */
public final class PrivateMessagePreview {
    private static final Set<EditBox> CHAT_INPUTS =
        Collections.newSetFromMap(new WeakHashMap<>());

    private PrivateMessagePreview() { }

    public static void attach(EditBox chatInput) {
        if (chatInput != null) CHAT_INPUTS.add(chatInput);
    }

    public static void detach(EditBox chatInput) {
        if (chatInput != null) CHAT_INPUTS.remove(chatInput);
    }

    public static FormattedCharSequence decorate(EditBox field, String visible, int offset,
            FormattedCharSequence existing) {
        if (field == null || !CHAT_INPUTS.contains(field)) return existing;
        String draft = field.getValue();
        if (draft == null || !draft.startsWith("/") || visible == null
                || draft.length() > PrivateMessagePreviewTokens.MAX_TEXT)
            return existing;

        int body = PrivateMessageSyntax.bodyStart(draft);
        if (body < 0) return existing;
        List<PrivateMessagePreviewTokens.Span> tokens =
            PrivateMessagePreviewTokens.find(draft, visible, offset, body,
                StreamotesBridge::registeredEmoteName);
        // Even if no message token is present, keep command and recipient
        // as ordinary command text rather than a misleading emote glyph.
        if (tokens.isEmpty() && body <= offset) return existing;
        return apply(existing, visible, offset, body, tokens);
    }

    /** Pure render-only composition, separately exercised by regression tests. */
    static FormattedCharSequence apply(FormattedCharSequence existing, String visible,
            int offset, int body, List<PrivateMessagePreviewTokens.Span> tokens) {
        FormattedCharSequence underlying = existing != null ? existing
            : FormattedCharSequence.forward(visible, Style.EMPTY);
        return sink -> underlying.accept((index, style, codepoint) -> {
            Style drawn = style;
            int absolute = index + offset;
            if (absolute < body && style.getClickEvent() instanceof ClickEvent.CopyToClipboard) {
                // A broad existing formatter may have highlighted a recipient
                // whose nickname happens to equal an emote name.
                drawn = style.withClickEvent(null);
            } else if (index >= 0 && index < visible.length()) {
                for (var token : tokens) {
                    if (index >= token.start() && index < token.end()) {
                        drawn = style.withClickEvent(new ClickEvent.CopyToClipboard(token.name()));
                        break;
                    }
                }
            }
            return sink.accept(index, drawn, codepoint);
        });
    }
}
