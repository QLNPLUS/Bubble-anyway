package com.bubbleanyway.client.mixin;

import com.bubbleanyway.client.BubbleOverlay;
import com.bubbleanyway.data.BubbleSpec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParentElement.class)
public abstract class ParentElementMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void bubbleAnyway$mouseClicked(double mouseX, double mouseY, int button,
                                           CallbackInfoReturnable<Boolean> callbackInfo) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft.world != null && (Object) this instanceof Screen screen
                && BubbleOverlay.handleMouseClick(mouseX, mouseY, button, screen.width, screen.height,
                screen instanceof GameMenuScreen ? BubbleSpec.RenderLayer.ABOVE_PAUSE : null)) {
            callbackInfo.setReturnValue(true);
        }
    }
}
