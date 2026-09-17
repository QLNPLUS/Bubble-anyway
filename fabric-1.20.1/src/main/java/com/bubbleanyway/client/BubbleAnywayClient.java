package com.bubbleanyway.client;

import com.bubbleanyway.network.BubbleNetwork;
import com.bubbleanyway.network.BubblePayload;
import com.bubbleanyway.network.BubbleThemePayload;
import com.bubbleanyway.network.BubbleThemeSyncPayload;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class BubbleAnywayClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BubbleThemeClientCache.setThemeRequester(themeId -> {
            var buffer = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            com.bubbleanyway.network.BubbleThemeRequestPayload.encode(
                    buffer, com.bubbleanyway.network.BubbleThemeRequestPayload.request(themeId));
            ClientPlayNetworking.send(BubbleNetwork.THEME_REQUEST_CHANNEL, buffer);
        });
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (net.minecraft.client.MinecraftClient.getInstance().currentScreen == null) {
                BubbleOverlay.render(drawContext, tickDelta);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BubbleOverlay.clientTick(client);
            BubbleDiagnostics.clientTick(client);
        });
        ClientPlayNetworking.registerGlobalReceiver(BubbleNetwork.CHANNEL, (client, handler, buffer, responseSender) -> {
            BubblePayload payload = BubblePayload.decode(buffer);
            client.execute(() -> {
                if (payload.clear()) {
                    BubbleOverlay.clear();
                } else {
                    BubbleOverlay.enqueue(payload.spec(), true);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(BubbleNetwork.THEME_CHANNEL, (client, handler, buffer, responseSender) -> {
            BubbleThemePayload payload = BubbleThemePayload.decode(buffer);
            client.execute(payload::handleClient);
        });
        ClientPlayNetworking.registerGlobalReceiver(BubbleNetwork.THEME_SYNC_CHANNEL, (client, handler, buffer, responseSender) -> {
            BubbleThemeSyncPayload payload = BubbleThemeSyncPayload.decode(buffer);
            client.execute(() -> {
                if (payload.replace()) {
                    com.bubbleanyway.client.BubbleThemeClientCache.replaceServerThemes(payload.themes());
                } else {
                    com.bubbleanyway.client.BubbleThemeClientCache.mergeServerThemes(payload.themes());
                }
            });
        });
    }
}
