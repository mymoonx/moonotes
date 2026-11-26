package dev.mymoonx.moonotesnetwork;

/** Safe, cursor-aware chat draft editing. Never sends a message or command. */
final class ChatTextEdit {
    record Result(String text, int cursor) { }
    private ChatTextEdit() { }

    static String activePrefix(String draft, int cursor) {
        if (draft == null || draft.isEmpty()) return "";
        int pos = Math.max(0, Math.min(cursor, draft.length()));
        int body = PrivateMessageSyntax.bodyStart(draft);
        if (body < 0 || pos < body) return "";
        int begin = pos;
        while (begin > 0 && !Character.isWhitespace(draft.charAt(begin - 1))) begin--;
        String token = draft.substring(begin, pos);
        // !WW is a global-chat prefix plus an emote only when !WW is NOT
        // itself a registered emote name. This does not change the draft.
        int first = 0;
        while (first < draft.length() && Character.isWhitespace(draft.charAt(first))) first++;
        if (body == 0 && begin == first) {
            token = GlobalChatPrefix.suggestion(token, WheelSettings.activePrefix(),
                value -> StreamotesBridge.registeredEmoteName(value)
                    || ChatPalette.snapshot().stream().anyMatch(e -> e.name().equals(value)));
        }
        if (token.startsWith(":")) token = token.substring(1);
        // BTTV control codes are not useful completion candidates.
        if (BttvModifierSyntax.isOperatorToken(token)) return "";
        if (token.length() > 80) return "";
        // Ordinary one-letter chat should not flood the suggestion strip.
        // After an explicit BTTV operator however, the player deliberately
        // asked for an emote, and one-letter aliases like q are valid.
        if (token.length() < 2 && !afterBttvOperator(draft, begin)) return "";
        return token;
    }

    private static boolean afterBttvOperator(String draft, int wordStart) {
        if (draft == null || wordStart <= 0) return false;
        int end = wordStart;
        while (end > 0 && Character.isWhitespace(draft.charAt(end - 1))) end--;
        int begin = end;
        while (begin > 0 && !Character.isWhitespace(draft.charAt(begin - 1))) begin--;
        String previous = draft.substring(begin, end);
        if (BttvModifierSyntax.isOperatorToken(previous)) return true;
        return previous.startsWith("!")
            && BttvModifierSyntax.isOperatorToken(previous.substring(1));
    }

    static Result insert(String draft, int cursor, String name, boolean replaceToken, boolean colonRequired) {
        String original = draft == null ? "" : draft;
        int messageStart = PrivateMessageSyntax.bodyStart(original);
        if (messageStart < 0 || cursor < messageStart)
            throw new IllegalArgumentException("Эмоты доступны только в тексте личного сообщения, не в команде или нике");
        // Keep Streamotes' supported names, including Cyrillic, but never accept control or formatting codes.
        if (name == null || name.isBlank() || name.startsWith("/") || name.length() > 80 || name.chars().anyMatch(c ->
            Character.isWhitespace(c) || Character.isISOControl(c) || Character.getType(c) == Character.FORMAT || c == '§'))
            throw new IllegalArgumentException("Недопустимое название эмота");
        int pos = Math.max(0, Math.min(cursor, original.length()));
        int start = pos, end = pos;
        if (replaceToken) {
            while (start > messageStart && !Character.isWhitespace(original.charAt(start - 1))) start--;
            while (end < original.length() && !Character.isWhitespace(original.charAt(end))) end++;
        }
        if (!PrivateMessageSyntax.editable(original, start, end))
            throw new IllegalArgumentException("Имя получателя и команда не должны изменяться");
        boolean keepGlobalPrefix = replaceToken && messageStart == 0
            && GlobalChatPrefix.preservePrefix(original, start, name, WheelSettings.activePrefix());
        if (keepGlobalPrefix) start += WheelSettings.activePrefix().length();
        String token = colonRequired ? ":" + name + ":" : name;
        String before = original.substring(0, start);
        String after = original.substring(end);
        String prefix = keepGlobalPrefix ? ""
            : !before.isEmpty() && !Character.isWhitespace(before.charAt(before.length()-1)) ? " " : "";
        String suffix = !after.isEmpty() && Character.isWhitespace(after.charAt(0)) ? "" : " ";
        String value = before + prefix + token + suffix + after;
        if (value.length() > 256) throw new IllegalArgumentException("Не хватает места в сообщении");
        int nextCursor=(before+prefix+token+suffix).length();
        if(suffix.isEmpty()&&!after.isEmpty()&&Character.isWhitespace(after.charAt(0)))nextCursor++;
        return new Result(value,nextCursor);
    }
}
