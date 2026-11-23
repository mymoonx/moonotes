package dev.mymoonx.moonotesnetwork;

import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Visual-only opt-in adapters; no changes to stored text or server content. */
public final class WorldTextRendering {
    private WorldTextRendering() {}

    public static Component decorateBook(Component value) {
        return ExtrasSettings.local().bookText() ? WorldEmoteText.decorate(value, true) : value;
    }

    public static Component decorateItem(Component value) {
        return ExtrasSettings.local().itemNames() ? WorldEmoteText.decorate(value) : value;
    }

    public static void decorateSign(SignText sign, boolean filtered,
            Function<Component,FormattedCharSequence> prepare,
            CallbackInfoReturnable<FormattedCharSequence[]> result) {
        if (!ExtrasSettings.local().signText() || sign == null || prepare == null) return;
        FormattedCharSequence[] original = result.getReturnValue();
        if (original == null || original.length > 8) return;
        List<Component> messages = sign.getMessages(filtered);
        if (messages == null || messages.size() != original.length) return;
        FormattedCharSequence[] updated = null;
        for (int i = 0; i < original.length; i++) {
            Component source = messages.get(i);
            Component tagged = WorldEmoteText.decorate(source, true);
            if (tagged != source) {
                if (updated == null) updated = original.clone();
                updated[i] = prepare.apply(tagged);
            }
        }
        if (updated != null) result.setReturnValue(updated);
    }
}
