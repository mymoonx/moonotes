package dev.mymoonx.moonotesnetwork;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;

/** Offline regression tests: no server, OAuth or live network required. */
public final class ExtrasCheck {
    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("moonotes-extras-check");
        Path config = folder.resolve("extras.json");
        ExtrasSettings settings = new ExtrasSettings(config);
        require(settings.wheel(), "wheel on by default");
        require(settings.reactions(), "local reactions enabled by default");
        require(!settings.signText() && !settings.bookText() && !settings.itemNames(),
                "world text must be opt-in");

        for (int option : List.of(15,16,18,19,20)) settings.cycle(option);
        settings.cycle(17); // 4 -> 6 seconds
        ExtrasSettings saved = new ExtrasSettings(config);
        require(!saved.wheel() && !saved.reactions(), "saved wheel/reactions");
        require(saved.signText() && saved.bookText() && saved.itemNames(), "saved text toggles");
        require(saved.reactionSeconds() == 6, "saved duration");
        saved.cycle(17);
        require(saved.reactionSeconds() == 2, "duration wraps to two seconds");

        Path oldTwitch = folder.resolve("moonotes-twitch-link.json");
        Path oldPaint = folder.resolve("moonotes-plus-paint-cache.json");
        Path safeLibrary = folder.resolve("moonotes-library.json");
        Files.writeString(oldTwitch, "legacy twitch id");
        Files.writeString(oldPaint, "old cosmetics");
        Files.writeString(safeLibrary, "do not delete");
        require(LegacyIdentityCleanup.clean(folder) == 2, "clean obsolete files");
        require(!Files.exists(oldTwitch) && !Files.exists(oldPaint), "legacy files removed");
        require(Files.exists(safeLibrary), "emote library untouched");
        require(LegacyIdentityCleanup.clean(folder) == 0, "repeat safe");

        String[] parsed = ChatReactionObserver.parseVanillaLine("<Steve> KEKW");
        require(parsed != null && "Steve".equals(parsed[0]) && "KEKW".equals(parsed[1]),
                "vanilla chat reaction parser");
        require(ChatReactionObserver.parseVanillaLine("[Admin] broadcast") == null,
                "ignore server announcements");
        require(ChatReactionObserver.parseVanillaLine("<Steve> " + "x".repeat(300)) == null,
                "limit incoming chat text");
        require(ChatReactionObserver.parseVanillaLine("<> KEKW") == null,
                "reject empty sender");

        require(WheelGeometry.slot(0, -95, 40, 120) == 0, "top wedge");
        require(WheelGeometry.slot(95, 0, 40, 120) == 2, "right wedge");
        require(WheelGeometry.slot(0, 95, 40, 120) == 4, "bottom wedge");
        require(WheelGeometry.slot(-95, 0, 40, 120) == 6, "left wedge");
        require(WheelGeometry.slot(0, 0, 40, 120) == -1, "center not selectable");
        require(WheelGeometry.slot(140, 0, 40, 120) == -1, "outside ring not selectable");
        var segments = WheelGeometry.spans(40, 120);
        require(!segments.isEmpty() && segments.size() < 4000, "bounded wheel geometry");
        require(segments.stream().map(WheelGeometry.Span::slot).distinct().count() == 8,
                "all eight wheel slices present");
        require(segments.stream().allMatch(segment -> segment.to() > segment.from()),
                "nonempty scanline spans");
        require("KEKW ".equals(ChatInsertion.append("", "KEKW")), "standalone emote token");
        require(LocalReactions.valid("KEKW"), "valid reaction");
        require(!LocalReactions.valid("/tp"), "no commands in reaction");
        require(!LocalReactions.valid("bad name"), "no whitespace");
        require(!LocalReactions.valid("x".repeat(81)), "bounded label");

        Component colored = Component.literal("DoNotTouch").withStyle(s ->
            s.withClickEvent(new ClickEvent.CopyToClipboard("other")));
        require(WorldEmoteText.decorate(colored) == colored, "clickable content preserved");
        Component plain = Component.literal("normal sentence");
        require(WorldEmoteText.decorate(plain) == plain, "no false emote detection");

        String id = "01F5Z4B9700008KDXA2TJQBNGW";
        Path prefsPath = folder.resolve("library.json");
        LibraryPreferences prefs = new LibraryPreferences(prefsPath);
        List<ChatPalette.Emote> list = List.of(
            new ChatPalette.Emote(id+":Pog", id,"Pog","Test"),
            new ChatPalette.Emote(id+":KEKW",id,"KEKW","Test"),
            new ChatPalette.Emote(id+":LUL",id,"LUL","Test"));
        prefs.toggle(id+":KEKW");
        List<ChatPalette.Emote> chosen = EmoteWheelScreen.choose(list, prefs);
        require(chosen.size()==3, "wheel uses available emotes");
        require("KEKW".equals(chosen.getFirst().name()), "favorite prioritization");
        require(EmoteWheelScreen.choose(List.of(),prefs).isEmpty(), "empty wheel safe");

        System.out.println("ExtrasCheck: PASS");
    }
    private static void require(boolean valid,String message) {
        if (!valid) throw new AssertionError(message);
    }
}
