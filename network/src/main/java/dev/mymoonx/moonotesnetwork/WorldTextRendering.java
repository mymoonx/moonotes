package dev.mymoonx.moonotesnetwork;

import net.minecraft.network.chat.Component;

/** Optional item-name adapter. Signs and books use their dedicated image renderers. */
public final class WorldTextRendering {
    private WorldTextRendering() {}

    public static Component decorateItem(Component value) {
        return ExtrasSettings.local().itemNames() ? WorldEmoteText.decorate(value) : value;
    }

}
