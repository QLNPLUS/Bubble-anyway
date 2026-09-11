package com.bubbleanyway.client.mixin;

import net.minecraft.advancement.Advancement;
import net.minecraft.client.toast.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementToast.class)
public interface AdvancementToastAccessor {
    @Accessor("advancement")
    Advancement bubbleAnyway$getAdvancement();
}
