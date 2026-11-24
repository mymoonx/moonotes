package dev.mymoonx.moonotesnetwork;

import java.util.concurrent.atomic.AtomicBoolean;

/** One-shot handoff from an in-game hotkey to the ChatScreen mixin. */
public final class ChatPickerShortcut {
    private static final AtomicBoolean REQUESTED=new AtomicBoolean();
    private ChatPickerShortcut(){}
    public static void request(){REQUESTED.set(true);}
    public static boolean consume(){return REQUESTED.getAndSet(false);}
}
