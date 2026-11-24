package dev.mymoonx.moonotesnetwork;

import java.nio.ByteBuffer;

/** Regression for the 26.3 SDL -1 unbound key crash on world join. */
public final class UnboundKeysCheck {
    public static void main(String[] args) {
        int unbound=WheelKeybind.unboundKeyCode();
        if(unbound!=0) throw new AssertionError("Minecraft 26.3 SDL unbound key must be zero, not GLFW -1");
        ByteBuffer keys=ByteBuffer.allocateDirect(512);
        keys.get(unbound); // Would throw IndexOutOfBoundsException for the old -1.
        // An unbound shortcut must NEVER be dispatched as a screen-opening action.
        if (WheelKeybind.shouldOpenUnbound(unbound))
            throw new AssertionError("Default shortcut must not open chat or manager");
        if (!WheelKeybind.shouldOpenUnbound(com.mojang.blaze3d.platform.InputConstants.KEY_G))
            throw new AssertionError("An assigned key can be handled");
        System.out.println("UnboundKeysCheck: PASS (SDL key index "+unbound+")");
    }
}
