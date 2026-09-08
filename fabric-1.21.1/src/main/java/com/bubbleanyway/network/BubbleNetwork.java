package com.bubbleanyway.network;

import com.bubbleanyway.BubbleAnyway;
import com.bubbleanyway.data.BubbleSpec;
import java.util.Collection;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class BubbleNetwork {
    public static final Identifier CHANNEL = BubblePayload.ID.id();

    private BubbleNetwork() {
    }

    public static void send(Collection<ServerPlayerEntity> players, BubbleSpec spec) {
        for (ServerPlayerEntity player : players) {
            ServerPlayNetworking.send(player, BubblePayload.show(spec));
        }
    }

    public static void clear(Collection<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            ServerPlayNetworking.send(player, BubblePayload.clearAll());
        }
    }
}
