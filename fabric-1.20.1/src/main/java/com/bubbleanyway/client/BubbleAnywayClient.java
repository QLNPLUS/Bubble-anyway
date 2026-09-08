package com.bubbleanyway.client;

import com.bubbleanyway.network.BubbleNetwork;
import com.bubbleanyway.network.BubblePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class BubbleAnywayClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> BubbleOverlay.render(drawContext, tickDelta));
        ClientPlayNetworking.registerGlobalReceiver(BubbleNetwork.CHANNEL, (client, handler, buffer, responseSender) -> {
            BubblePayload payload = BubblePayload.decode(buffer);
            client.execute(() -> {
                if (payload.clear()) {
                    BubbleOverlay.clear();
                } else {
                    BubbleOverlay.enqueue(payload.spec());
                }
            });
        });
    }
}
