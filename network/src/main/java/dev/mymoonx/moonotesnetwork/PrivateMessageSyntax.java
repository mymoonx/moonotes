package dev.mymoonx.moonotesnetwork;

/**
 * Conservatively permits emote completion only inside the message argument of
 * known private-message commands. Command names and recipients are never edited.
 * No command packet is sent by this helper.
 */
final class PrivateMessageSyntax {
    private PrivateMessageSyntax() { }

    /**
     * First editable character after the command and recipient; -1 if the
     * string is not a supported chat or private-message draft.
     */
    static int bodyStart(String draft) {
        return bodyStart(draft, ChatCommandRules.local());
    }

    static int bodyStart(String draft, ChatCommandRules rules) {
        if (draft == null || rules == null) return -1;
        int length = draft.length();
        int first = 0;
        while (first < length && Character.isWhitespace(draft.charAt(first))) first++;
        if (first == length) return 0;
        if (draft.charAt(first) != '/') return 0;

        int start = first + 1;
        int end = start;
        while (end < length && !Character.isWhitespace(draft.charAt(end))) end++;
        if (start == end || end == length) return -1;
        ChatCommandRules.Mode mode = rules.mode(draft.substring(start, end));
        if (mode == null) return -1;

        int cursor = end;
        while (cursor < length && Character.isWhitespace(draft.charAt(cursor))) cursor++;
        if (mode == ChatCommandRules.Mode.MESSAGE)
            return cursor < length || cursor > end ? cursor : -1;

        int recipientStart = cursor;
        while (cursor < length && !Character.isWhitespace(draft.charAt(cursor))) cursor++;
        if (recipientStart == cursor || cursor == length) return -1;
        String recipient = draft.substring(recipientStart, cursor);
        if (recipient.length() > 16 || !recipient.matches("[A-Za-z0-9_]{1,16}"))
            return -1;
        int recipientEnd = cursor;
        while (cursor < length && Character.isWhitespace(draft.charAt(cursor))) cursor++;
        return cursor < length || cursor > recipientEnd ? cursor : -1;
    }

    static boolean editable(String draft, int start, int end) {
        int offset = bodyStart(draft);
        return offset >= 0 && start >= offset && end >= start
            && draft != null && end <= draft.length();
    }
}
