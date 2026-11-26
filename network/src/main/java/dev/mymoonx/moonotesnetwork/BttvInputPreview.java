package dev.mymoonx.moonotesnetwork;

import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Optional bridge for the existing Moonotes core EditBox formatter.
 * When no BTTV syntax is present, core handles the normal input unchanged.
 * When syntax exists, this renders the effects while still decorating
 * unrelated registered Streamotes emotes in the same chat draft.
 */
public final class BttvInputPreview {
    private static final Pattern WORD = Pattern.compile("[^\\s:]{2,}|:?[^\\s:]+:?");
    private BttvInputPreview() { }

    /** Null means core must use its original non-modifier formatting. */
    public static FormattedCharSequence format(String draft, Integer ignoredOffset) {
        // First milestone: ordinary chat only. Commands have a separate
        // Brigadier syntax formatter and will be handled in a later stage.
        if (draft == null || draft.isEmpty() || draft.length() > 256
                || draft.stripLeading().startsWith("/")) return null;
        if (BttvModifierSyntax.find(draft,
                StreamotesBridge::registeredEmoteName).isEmpty()) return null;
        FormattedText transformed = BttvChatText.decorate(FormattedText.of(draft));
        var chunks = new ArrayList<FormattedCharSequence>();
        transformed.visit((style, value) -> {
            if (style.getClickEvent() instanceof ClickEvent.CopyToClipboard || value.isEmpty()) {
                chunks.add(FormattedCharSequence.forward(value, style));
            } else {
                ordinary(chunks, value, style);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return chunks.isEmpty() ? FormattedCharSequence.EMPTY
            : FormattedCharSequence.composite(chunks);
    }

    private static void ordinary(ArrayList<FormattedCharSequence> out, String value,
            Style base) {
        var matcher = WORD.matcher(value);
        int cursor = 0;
        while (matcher.find()) {
            String token = matcher.group();
            if (token.startsWith(":") || token.endsWith(":")) continue;
            String emote = token;
            int start = matcher.start();
            if (!StreamotesBridge.registeredEmoteName(emote)) {
                String global = StreamotesBridge.activeChatPrefix();
                if (global == null || global.isEmpty() || !token.startsWith(global)
                        || token.length() <= global.length()) continue;
                emote = token.substring(global.length());
                if (!StreamotesBridge.registeredEmoteName(emote)) continue;
                start += global.length();
            }
            if (start > cursor)
                out.add(FormattedCharSequence.forward(value.substring(cursor, start), base));
            out.add(FormattedCharSequence.forward(emote,
                base.withClickEvent(new ClickEvent.CopyToClipboard(emote))));
            cursor = matcher.end();
        }
        if (cursor < value.length())
            out.add(FormattedCharSequence.forward(value.substring(cursor), base));
    }
}
