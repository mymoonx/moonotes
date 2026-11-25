package dev.mymoonx.moonotesnetwork;

import java.util.List;
import java.util.Map;

public final class WorldAliasCoverageCheck {
    private static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args){
        Map<String,ChatPalette.Emote> aliases=Map.of(
            "Улыбка",new ChatPalette.Emote("test:1","1234567890123456","Улыбка","test"),
            "pepe-laugh",new ChatPalette.Emote("test:2","1234567890123457","pepe-laugh","test"),
            "cat.wow",new ChatPalette.Emote("test:3","1234567890123458","cat.wow","test"),
            "😺",new ChatPalette.Emote("test:4","1234567890123459","😺","test"));
        var found=EmoteTextTokens.find("Улыбка pepe-laugh :cat.wow: 😺",aliases);
        require(found.stream().map(EmoteTextTokens.Match::name).toList()
            .equals(List.of("Улыбка","pepe-laugh","cat.wow","😺")),"catalog aliases on signs/books");
        require(EmoteTextTokens.find("xУлыбка Улыбкаx pepe-laughish",aliases).isEmpty(),
            "no partial word replacements");
        System.out.println("WorldAliasCoverageCheck: PASS");
    }
}
