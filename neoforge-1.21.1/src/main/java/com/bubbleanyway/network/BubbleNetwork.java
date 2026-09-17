package com.bubbleanyway.network;

import com.bubbleanyway.data.BubbleSpec;
import com.bubbleanyway.data.BubbleThemeManager;
import java.util.Collection;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class BubbleNetwork {
    private BubbleNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(BubblePayload.TYPE, BubblePayload.STREAM_CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                if (payload.clear()) {
                    com.bubbleanyway.client.BubbleOverlay.clear();
                } else {
                    com.bubbleanyway.client.BubbleOverlay.enqueue(payload.spec(), true);
                }
            });
        }).playToClient(BubbleThemePayload.TYPE, BubbleThemePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> com.bubbleanyway.client.BubbleThemeClientCache.enqueue(
                        payload.themeId(), payload.overridesJson(), true)))
                .playToClient(BubbleThemeSyncPayload.TYPE, BubbleThemeSyncPayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (payload.replace()) {
                                com.bubbleanyway.client.BubbleThemeClientCache.replaceServerThemes(payload.themes());
                            } else {
                                com.bubbleanyway.client.BubbleThemeClientCache.mergeServerThemes(payload.themes());
                            }
                        }))
                .playToServer(BubbleThemeRequestPayload.TYPE, BubbleThemeRequestPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        context.enqueueWork(() -> sendThemeDefinition(player, payload.themeId()));
                    }
                })
                .playToServer(BubbleClickPayload.TYPE, BubbleClickPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        context.enqueueWork(() -> BubbleInteractionManager.handleClick(
                                player, payload.bubbleId(), payload.controlId()));
                    }
                });
    }

    public static void send(Collection<ServerPlayer> players, BubbleSpec spec) {
        BubblePayload payload = BubblePayload.show(spec);
        for (ServerPlayer player : players) {
            BubbleInteractionManager.register(player, spec);
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void clear(Collection<ServerPlayer> players) {
        BubblePayload payload = BubblePayload.clearAll();
        for (ServerPlayer player : players) {
            BubbleInteractionManager.clear(player);
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void sendTheme(Collection<ServerPlayer> players, String themeId, String overridesJson) {
        BubbleThemePayload payload = BubbleThemePayload.show(themeId, overridesJson);
        BubbleSpec resolved = BubbleThemeManager.resolve(themeId, overridesJson);
        for (ServerPlayer player : players) {
            BubbleInteractionManager.register(player, resolved);
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void requestTheme(String themeId) {
        PacketDistributor.sendToServer(BubbleThemeRequestPayload.request(themeId));
    }

    public static void sendThemeDefinition(ServerPlayer player, String themeId) {
        if (player != null) {
            BubbleThemeSyncPayload.fromTheme(themeId).ifPresent(payload -> PacketDistributor.sendToPlayer(player, payload));
        }
    }

    public static void syncThemes(Collection<ServerPlayer> players) {
        BubbleThemeSyncPayload payload = BubbleThemeSyncPayload.fromCurrentThemes();
        for (ServerPlayer player : players) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void sendClickToServer(String bubbleId, String controlId) {
        PacketDistributor.sendToServer(BubbleClickPayload.click(bubbleId, controlId));
    }
}
