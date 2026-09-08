package com.bubbleanyway.client;

import com.bubbleanyway.network.BubbleNetwork;
import com.bubbleanyway.network.BubblePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.render.RenderTickCounter;

public final class BubbleAnywayClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> BubbleOverlay.render(drawContext, tickCounter));
        ClientPlayNetworking.registerGlobalReceiver(BubblePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.clear()) {
                    BubbleOverlay.clear();
                } else {
                    BubbleOverlay.enqueue(payload.spec());
                }
            });
        });
    }
}
