package com.bubbleanyway.client;

import com.bubbleanyway.network.BubbleNetwork;
import com.bubbleanyway.network.BubblePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.render.RenderTickCounter;

public final class BubbleAnywayClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BubbleThemeClientCache.setThemeRequester(themeId ->
                ClientPlayNetworking.send(com.bubbleanyway.network.BubbleThemeRequestPayload.request(themeId)));
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (net.minecraft.client.MinecraftClient.getInstance().currentScreen == null) {
                BubbleOverlay.render(drawContext, tickCounter.getTickDelta(false));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BubbleOverlay.clientTick(client);
            BubbleDiagnostics.clientTick(client);
        });
        ClientPlayNetworking.registerGlobalReceiver(BubblePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.clear()) {
                    BubbleOverlay.clear();
                } else {
                    BubbleOverlay.enqueue(payload.spec(), true);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(com.bubbleanyway.network.BubbleThemePayload.ID, (payload, context) ->
                context.client().execute(payload::handleClient));
        ClientPlayNetworking.registerGlobalReceiver(com.bubbleanyway.network.BubbleThemeSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.replace()) {
                        BubbleThemeClientCache.replaceServerThemes(payload.themes());
                    } else {
                        BubbleThemeClientCache.mergeServerThemes(payload.themes());
                    }
                }));
    }
}
