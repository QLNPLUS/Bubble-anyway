package com.bubbleanyway.client.mixin;

import com.bubbleanyway.client.BubbleOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenRenderMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void bubbleAnyway$render(DrawContext drawContext, int mouseX, int mouseY, float delta, CallbackInfo callbackInfo) {
        if (MinecraftClient.getInstance().world != null) {
            BubbleOverlay.render(drawContext, delta);
        }
    }
}
