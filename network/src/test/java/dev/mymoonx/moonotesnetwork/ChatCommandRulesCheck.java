package dev.mymoonx.moonotesnetwork;

import java.nio.file.Files;
import java.nio.file.Path;

/** Regression for editable and durable private command-prefix exceptions. */
public final class ChatCommandRulesCheck {
    private static int count;
    private static void require(boolean ok, String what) {
        count++;
        if (!ok) throw new AssertionError(what);
    }

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("moonotes-commands-check");
        Path file = folder.resolve("custom-commands.json");
        var config = new ChatCommandRules(file);
        require(config.list().size() == 7, "seven built-in private commands");
        require(config.mode("/msg") == ChatCommandRules.Mode.RECIPIENT, "msg requires recipient");
        require(config.mode("R") == ChatCommandRules.Mode.MESSAGE, "reply has no recipient");
        require(PrivateMessageSyntax.bodyStart("/msg Steve ca", config) == 11,
                "ignore command and recipient in the default list");
        require(PrivateMessageSyntax.bodyStart("/r ca", config) == 3,
                "reply text starts directly after command");
        require(PrivateMessageSyntax.bodyStart("/other Steve ca", config) == -1,
                "unknown commands remain excluded");

        require(config.put("/custompm", ChatCommandRules.Mode.RECIPIENT),
                "add server-specific recipient command");
        require(config.put("/ESSENTIALS:msg", ChatCommandRules.Mode.RECIPIENT),
                "namespaced server command");
        require(config.put("chatreply", ChatCommandRules.Mode.MESSAGE),
                "add no-recipient reply command");
        require(PrivateMessageSyntax.bodyStart("/custompm Alex cat", config) == 15,
                "custom recipient command body");
        require(PrivateMessageSyntax.bodyStart("/chatreply cat", config) == 11,
                "custom reply command body");
        require(PrivateMessageSyntax.bodyStart("/essentials:msg Sam cat", config) == 20,
                "plugin namespace is matched case-insensitively");
        require(config.remove("/msg"), "remove default msg command");
        require(PrivateMessageSyntax.bodyStart("/msg Steve cat", config) == -1,
                "removed default command immediately excluded");
        require(config.mode("msg") == null, "removed rules no longer present");

        var restored = new ChatCommandRules(file);
        require(restored.mode("/msg") == null, "deleted built-in is not restored on restart");
        require(restored.mode("custompm") == ChatCommandRules.Mode.RECIPIENT,
                "custom rule survives reload");
        require(restored.mode("chatreply") == ChatCommandRules.Mode.MESSAGE,
                "custom reply survives reload");
        ChatCommandRules.initialize(file);
        require(ChatTextEdit.activePrefix("/custompm Alex ca", 17).equals("ca"),
                "live suggestion logic uses saved custom command rules");
        require(ChatTextEdit.activePrefix("/msg Steve ca", 13).isEmpty(),
                "live suggestion logic respects removed default");
        require(ChatTextEdit.insert("/custompm Alex ca", 17, "catJAM", true, false)
                .text().equals("/custompm Alex catJAM "),
                "custom command text replacement leaves header intact");
        require(ChatTextEdit.activePrefix("/other Alex ca", 14).isEmpty(),
                "arbitrary command remains untouched");

        require(!restored.put("/bad command", ChatCommandRules.Mode.RECIPIENT),
                "invalid command names denied");
        require(!restored.put("/tp @a", ChatCommandRules.Mode.RECIPIENT),
                "multi-token aliases denied");
        require(!restored.put("/foo;", ChatCommandRules.Mode.RECIPIENT),
                "punctuation injections denied");
        require(!restored.put("/msg", null), "missing mode denied");
        require(restored.remove("r"), "remove default no-recipient command");
        require(restored.put("r", ChatCommandRules.Mode.RECIPIENT),
                "change default mode to recipient");
        require(PrivateMessageSyntax.bodyStart("/r Alex ca", restored) == 8,
                "changed mode applies immediately");
        require(PrivateMessageSyntax.bodyStart("/r ca", restored) == -1,
                "changed mode must have a recipient");
        restored.reset();
        require(restored.list().size() == 7, "reset restores defaults");
        require(restored.mode("msg") == ChatCommandRules.Mode.RECIPIENT
                && restored.mode("r") == ChatCommandRules.Mode.MESSAGE,
                "reset restores both types");

        for (var rule : restored.list()) require(restored.remove(rule.name()), "remove rule");
        require(restored.list().isEmpty(), "empty list allowed");
        require(new ChatCommandRules(file).list().isEmpty(),
                "intentional empty list survives restart");
        System.out.println("ChatCommandRulesCheck: " + count + " checks passed");
    }
}
