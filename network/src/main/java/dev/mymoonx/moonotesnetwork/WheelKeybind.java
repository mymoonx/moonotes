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

    static void register() {
        if (registered) return;
        registered = true;
        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("moonotes_network", "gameplay"));
        KeyMapping wheel = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.moonotes_network.wheel", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (wheel.consumeClick()) {
                if (client.player != null && client.gui.screen() == null
                    && ExtrasSettings.local().wheel()) {
                    client.gui.setScreen(new EmoteWheelScreen());
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LocalReactions.clear());
    }
}
