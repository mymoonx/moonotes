package dev.mymoonx.moonotesnetwork;

/** Only message bodies of explicitly supported commands are eligible for emote completion. */
public final class PrivateMessageSyntaxCheck {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void blocked(String input) {
        check(PrivateMessageSyntax.bodyStart(input) < 0, "must not autocomplete: " + input);
        check(ChatTextEdit.activePrefix(input, input.length()).isEmpty(), "no suggestions: " + input);
    }
    private static void reject(String draft, int cursor) {
        try {
            ChatTextEdit.insert(draft, cursor, "catJAM", true, false);
            throw new AssertionError("unsafe edit accepted: " + draft);
        } catch (IllegalArgumentException correct) { checks++; }
    }
    public static void main(String[] args) {
        check(ChatTextEdit.activePrefix("/msg Steve ca", 13).equals("ca"), "msg text prefix");
        check(ChatTextEdit.activePrefix("/tell Steve :ca", 15).equals("ca"), "colon prefix in tell");
        check(ChatTextEdit.activePrefix("/w Alex ca", 10).equals("ca"), "w message prefix");
        check(ChatTextEdit.activePrefix("/pm Steve ca", 12).equals("ca"), "pm message prefix");
        check(ChatTextEdit.activePrefix("/r ca", 5).equals("ca"), "reply prefix");
        check(ChatTextEdit.activePrefix("/reply ca", 9).equals("ca"), "long reply prefix");
        check(ChatTextEdit.activePrefix("/msg Steve", 10).isEmpty(), "recipient not completed as emote");
        check(ChatTextEdit.activePrefix("/msg Ste", 8).isEmpty(), "partial nickname not completed");
        check(ChatTextEdit.activePrefix("/r", 2).isEmpty(), "reply alias not completed");
        check(ChatTextEdit.activePrefix("/msg Steve ca", 5).isEmpty(), "inside command blocked");
        check(ChatTextEdit.activePrefix("/msg Steve ca", 9).isEmpty(), "inside recipient blocked");

        var message = ChatTextEdit.insert("/msg Steve ca", 13, "catJAM", true, false);
        check(message.text().equals("/msg Steve catJAM "), "replace only message token");
        check(message.text().startsWith("/msg Steve "), "keep original slash and recipient");
        var colon = ChatTextEdit.insert("/msg Steve :ca", 14, "catJAM", true, true);
        check(colon.text().equals("/msg Steve :catJAM: "), "colon Streamotes emote in private message");
        var atEnd = ChatTextEdit.insert("/msg Steve ", 11, "catJAM", false, false);
        check(atEnd.text().equals("/msg Steve catJAM "), "insert into empty message body");
        var reply = ChatTextEdit.insert("/r ca", 5, "catJAM", true, false);
        check(reply.text().equals("/r catJAM "), "reply command body");
        reject("/msg Steve", 10);
        reject("/msg Steve ca", 7);
        reject("/tp Steve ca", 12);
        reject("/say ca", 7);
        reject("/msg @a ca", 10);
        blocked("/msg Steve");
        blocked("/r");
        blocked("/tp Steve ca");
        blocked("/say ca");
        blocked("/moonotes channel ca");
        blocked("/msg @a ca");
        check(!PrivateMessageSyntax.editable("/msg Steve ca", 5, 11),
            "selected command and recipient protected");
        check(PrivateMessageSyntax.editable("/msg Steve ca", 11, 13),
            "selected message body can be replaced");
        check(PrivateMessageSyntax.bodyStart("/r ") == 3, "empty reply body is allowed");
        System.out.println("PrivateMessageSyntaxCheck: " + checks + " checks passed");
    }
}
