package dev.mymoonx.moonotesnetwork.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The vanilla line-view record is protected; expose offsets without reflection. */
@Mixin(targets = "net.minecraft.client.gui.components.MultilineTextField$StringView", remap = false)
public interface BookLineViewAccessor {
    @Accessor("beginIndex") int moonotes$beginIndex();
    @Accessor("endIndex") int moonotes$endIndex();
}
