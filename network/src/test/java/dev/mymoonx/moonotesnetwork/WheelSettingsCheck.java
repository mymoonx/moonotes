package dev.mymoonx.moonotesnetwork;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Stable ordering, explicit channel routing, and layout bounds at supported GUI sizes. */
public final class WheelSettingsCheck {
    private static final String ID = "01F5Z4B9700008KDXA2TJQBNGW";
    private static ChatPalette.Emote emote(String name) {
        return new ChatPalette.Emote(ID + ":" + name, ID, name, "Test");
    }
    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("moonotes-wheel-check");
        Path file = folder.resolve("wheel.json");
        var settings = new WheelSettings(file);
        var a = emote("42");
        var b = emote("67");
        var c = emote("LongEmoteName");
        require(settings.channel() == WheelSettings.Channel.LOCAL, "local chat is the initial channel");
        settings.seed(List.of());
        require(!settings.configured(), "empty catalogs must not lock an empty initial wheel");
        settings.seed(List.of(a, b, c));
        require(settings.slots().size() == 8 && settings.slots().get(0).equals(a.key()), "eight persistent positions");
        settings.seed(List.of(c, a, b));
        require(settings.slots().get(0).equals(a.key()), "recent activity cannot reorder the wheel");

        var changed = WheelSettings.assign(settings.slots(), 0, b.key());
        require(changed.get(0).equals(b.key()) && changed.get(1).equals(a.key()), "assigning an existing emote swaps positions");
        changed = WheelSettings.assign(changed, 2, "");
        require(changed.get(2).isEmpty(), "clear retains an empty position");
        require(settings.saveSlots(changed), "save custom wheel");
        settings.setChannel(WheelSettings.Channel.GLOBAL);
        var loaded = new WheelSettings(file);
        require(loaded.slots().equals(changed) && loaded.channel() == WheelSettings.Channel.GLOBAL, "slots and channel survive reload");
        var available = loaded.resolve(List.of(a));
        require(available[0] == null && available[1].equals(a), "disabled catalogs do not shift remaining positions");
        loaded.seed(List.of(c));
        require(loaded.slots().get(2).isEmpty(), "intentional empty positions remain empty");
        var normalized = WheelSettings.normalized(java.util.Arrays.asList(a.key(), a.key(), null, "/command"));
        require(normalized.get(0).equals(a.key()) && normalized.stream().filter(String::isEmpty).count() == 7,
            "invalid or duplicated persisted keys are ignored without shifting");

        require(ChatInsertion.forChannel("42", WheelSettings.Channel.LOCAL).equals("42"), "local plain message");
        require(ChatInsertion.forChannel("67", WheelSettings.Channel.GLOBAL).equals("!67"), "global prefix");
        require(ChatInsertion.forChannel(":42:", WheelSettings.Channel.GLOBAL).equals("!:42:"), "Streamotes activation format preserved");
        require(ChatInsertion.appendForChannel("Привет", "42", WheelSettings.Channel.GLOBAL).equals("!Привет 42 "),
            "global draft preserves text and prefixes the entire message");
        require(ChatInsertion.appendForChannel("!Привет ", ":67:", WheelSettings.Channel.GLOBAL).equals("!Привет :67: "),
            "do not duplicate existing global prefix");
        require(ChatInsertion.appendForChannel("!Привет ", "67", WheelSettings.Channel.LOCAL).equals("Привет 67 "),
            "explicit local selection removes routing prefix");
        require(ChatInsertion.append("!old draft", "42").equals("!old draft 42 "), "ordinary palette insertion stays unchanged");
        for (String invalid : List.of("/tp", "!42", "42\n67", "42 67", "bad\u202ename", "§k42"))
            reject(() -> ChatInsertion.forChannel(invalid, WheelSettings.Channel.GLOBAL), "unsafe token");
        reject(() -> ChatInsertion.appendForChannel("/tp", "42", WheelSettings.Channel.GLOBAL), "command draft must not be rewritten");
        reject(() -> ChatInsertion.appendForChannel("a".repeat(252), "42", WheelSettings.Channel.GLOBAL),
            "global prefix is included in chat length limit");

        for (int[] size : List.of(new int[]{320,240}, new int[]{427,240}, new int[]{640,360}, new int[]{960,540})) {
            WheelLayout layout = WheelLayout.of(size[0], size[1]);
            require(layout.centerY() - layout.outer() >= 38, "ring below controls");
            require(layout.centerY() + layout.outer() < layout.nameY(), "name panel below ring");
            require(layout.centerX() - layout.outer() >= 12 && layout.centerX() + layout.outer() <= size[0] - 12,
                "ring fits width");
            require(layout.nameY() + 28 < size[1] - 26, "name and action rows do not overlap");
            for (int slot = 0; slot < 8; slot++)
                require(layout.hit(layout.iconX(slot) + layout.icon() / 2.0,
                    layout.iconY(slot) + layout.icon() / 2.0) == slot, "every icon belongs to its clickable sector");
        }
        System.out.println("WheelSettingsCheck: PASS");
    }
    private static void reject(Runnable action, String label) {
        try { action.run(); throw new AssertionError(label); }
        catch (IllegalArgumentException expected) { }
    }
    private static void require(boolean value, String label) { if (!value) throw new AssertionError(label); }
}
