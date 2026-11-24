package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Configurable hotkey in Minecraft Controls; only opens from gameplay. */
final class WheelKeybind {
    private static boolean registered;
    private WheelKeybind() {}
    static int unboundKeyCode() { return InputConstants.UNKNOWN.getValue(); }
    static boolean canDispatch(boolean isUnbound) { return !isUnbound; }

    static void register() {
        if (registered) return;
        registered = true;
        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("moonotes_network", "gameplay"));
        KeyMapping wheel = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.moonotes_network.wheel", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, category));
        // SDL (Minecraft 26.3) uses key 0 for "Not Bound". The old GLFW -1
        // crashes KeyMapping.setAll() on entering a world because it indexes
        // the SDL key-state buffer at -1. Use the vanilla sentinel instead.
        KeyMapping manager = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.moonotes_network.manager", InputConstants.Type.KEYBOARD, unboundKeyCode(), category));
        KeyMapping picker = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.moonotes_network.picker", InputConstants.Type.KEYBOARD, unboundKeyCode(), category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Minecraft 26.3 maps an unbound key to SDL UNKNOWN (code 0).
            // Never consume an unbound shortcut: the zero-code key can receive
            // synthetic events when a screen closes or the mouse is captured.
            // Otherwise the shortcut keeps reopening ChatScreen / Moonotes,
            // leaving the pointer visible and the camera unable to turn.
            if (canDispatch(manager.isUnbound())) {
                while (manager.consumeClick()) {
                    if (client.player != null && client.gui.screen() == null) {
                        client.gui.setScreen(new MoonotesScreen());
                        MoonotesTelemetry.count(MoonotesTelemetry.Event.MANAGER_OPEN);
                    }
                }
            }
            if (canDispatch(picker.isUnbound())) {
                while (picker.consumeClick()) {
                    if (client.player != null && client.gui.screen() == null) {
                        MoonotesTelemetry.count(MoonotesTelemetry.Event.PICKER_OPEN);
                        ChatPickerShortcut.request();
                        client.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
                    }
                }
            }
            while (wheel.consumeClick()) {
                if (client.player != null && client.gui.screen() == null
                    && ExtrasSettings.local().wheel()) {
                    client.gui.setScreen(new EmoteWheelScreen());
                    MoonotesTelemetry.count(MoonotesTelemetry.Event.WHEEL_OPEN);
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LocalReactions.clear());
    }
}
