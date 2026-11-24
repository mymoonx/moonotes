package dev.mymoonx.moonotesnetwork;

import java.util.List;
import java.util.Map;

/** Regression cases for numeric names, punctuation and exact token boundaries. */
public final class EmoteTextTokensCheck {
    public static void main(String[] args) {
        Map<String, ChatPalette.Emote> names = Map.of(
                "42", new ChatPalette.Emote("set:42", "1234567890123456", "42", "set"),
                "67", new ChatPalette.Emote("set:67", "1234567890123457", "67", "set"),
                "Kappa", new ChatPalette.Emote("set:Kappa", "1234567890123458", "Kappa", "set"));
        var numeric = EmoteTextTokens.find("42 :67: (42)!", names);
        require(numeric.stream().map(EmoteTextTokens.Match::name).toList().equals(List.of("42", "67", "42")),
                "numeric/plain/colon tokens");
        require(numeric.get(1).start() == 3 && numeric.get(1).end() == 7, "UTF-16 source bounds include colons");
        require(EmoteTextTokens.find("142 420 x42 42x _42 42_ :42 42: ::42::", names).isEmpty(),
                "no partial identifier or unmatched colon matches");
        require(EmoteTextTokens.find("kappa KAPPA Kappa", names).size() == 1, "case-sensitive catalog names");
        require(EmoteTextTokens.find("😀 42", names).getFirst().start() == 3, "UTF-16 index after emoji");
        require(EmoteTextTokens.find("42 ".repeat(100), names).size() == 64, "bounded substitutions");
        require(EmoteTextTokens.find("42 ".repeat(2000), names).isEmpty(), "bounded input");
        require(EmoteTextTokens.find("42", Map.of()).isEmpty(), "disabled or absent catalog leaves text");
        System.out.println("EmoteTextTokensCheck OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
