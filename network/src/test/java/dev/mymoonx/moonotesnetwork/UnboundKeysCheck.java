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
        if (WheelKeybind.canDispatch(true))
            throw new AssertionError("Unbound shortcut must never open a GUI screen");
        if (!WheelKeybind.canDispatch(false))
            throw new AssertionError("Explicitly assigned shortcut must be dispatchable");
        System.out.println("UnboundKeysCheck: PASS (SDL key index "+unbound+")");
    }
}
