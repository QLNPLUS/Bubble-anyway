package com.bubbleanyway.client.mixin;

import com.bubbleanyway.client.BubbleToastIntegration;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
    @Inject(method = "add(Lnet/minecraft/client/toast/Toast;)V", at = @At("HEAD"), cancellable = true)
    private void bubbleAnyway$replace(Toast toast, CallbackInfo callbackInfo) {
        if (BubbleToastIntegration.handle(toast)) {
            callbackInfo.cancel();
        }
    }
}
