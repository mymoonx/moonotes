package dev.mymoonx.moonotesnetwork;

import dev.mymoonx.moonotesnetwork.mixin.BttvGlyphMixin;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Prevent a repeat of alpha.3's invalid @ModifyArg signature.
 *
 * Does NOT substitute for an in-game Fabric mixin transformation test, but
 * checks the precise, single-Args signature required by @ModifyArgs.
 */
public final class BttvGlyphInjectionCheck {
    public static void main(String[] args) throws Exception {
        Method handler = BttvGlyphMixin.class.getDeclaredMethod(
            "moonotes$transformBttv", Args.class);
        if (handler.getReturnType() != void.class
                || handler.getParameterCount() != 1
                || !Modifier.isPrivate(handler.getModifiers()))
            throw new AssertionError("Invalid @ModifyArgs handler signature");
        ModifyArgs injection = handler.getAnnotation(ModifyArgs.class);
        if (injection == null)
            throw new AssertionError("Glyph hook must use @ModifyArgs, not @ModifyArg");
        if (injection.at().length != 1)
            throw new AssertionError("Expected a single final-glyph invocation");
        String target = injection.at()[0].target();
        if (!target.equals("Lnet/minecraft/client/gui/Font$PreparedTextBuilder;"
                + "accept(ILnet/minecraft/network/chat/Style;"
                + "Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z"))
            throw new AssertionError("Wrong final glyph invocation descriptor: " + target);
        if (injection.require() != 0)
            throw new AssertionError("Experimental draw hook must fail closed");
        System.out.println("BttvGlyphInjectionCheck: valid one-Args handler PASS");
    }
}
