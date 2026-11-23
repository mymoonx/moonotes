package dev.mymoonx.moonotesnetwork.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exact selection endpoint; avoids guessing the direction from duplicate text. */
@Mixin(value=EditBox.class,remap=false)
public interface EditBoxSelectionAccessor {
    @Accessor("highlightPos") int moonotes$highlightPosition();
}
