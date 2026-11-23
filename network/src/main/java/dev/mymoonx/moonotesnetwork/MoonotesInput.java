package dev.mymoonx.moonotesnetwork;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;

/** Use the game's input constants: Minecraft 26.3 moved from GLFW to SDL. */
final class MoonotesInput {
    private MoonotesInput() {}
    static boolean isPrimary(MouseButtonEvent event) {
        return event.button() == InputConstants.MOUSE_BUTTON_LEFT;
    }
}

