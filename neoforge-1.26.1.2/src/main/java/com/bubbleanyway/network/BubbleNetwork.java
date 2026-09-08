package com.bubbleanyway.network;

import com.bubbleanyway.data.BubbleSpec;
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
                    com.bubbleanyway.client.BubbleOverlay.enqueue(payload.spec());
                }
            });
        });
    }

    public static void send(Collection<ServerPlayer> players, BubbleSpec spec) {
        BubblePayload payload = BubblePayload.show(spec);
        for (ServerPlayer player : players) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void clear(Collection<ServerPlayer> players) {
        BubblePayload payload = BubblePayload.clearAll();
        for (ServerPlayer player : players) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
