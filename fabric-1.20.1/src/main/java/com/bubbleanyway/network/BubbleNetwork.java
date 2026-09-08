package com.bubbleanyway.network;

import com.bubbleanyway.BubbleAnyway;
import com.bubbleanyway.data.BubbleSpec;
import java.util.Collection;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class BubbleNetwork {
    public static final Identifier CHANNEL = BubbleAnyway.id("bubble");

    private BubbleNetwork() {
    }

    public static void send(Collection<ServerPlayerEntity> players, BubbleSpec spec) {
        for (ServerPlayerEntity player : players) {
            PacketByteBuf buffer = PacketByteBufs.create();
            BubblePayload.encode(buffer, BubblePayload.show(spec));
            ServerPlayNetworking.send(player, CHANNEL, buffer);
        }
    }

    public static void clear(Collection<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            PacketByteBuf buffer = PacketByteBufs.create();
            BubblePayload.encode(buffer, BubblePayload.clearAll());
            ServerPlayNetworking.send(player, CHANNEL, buffer);
        }
    }
}
