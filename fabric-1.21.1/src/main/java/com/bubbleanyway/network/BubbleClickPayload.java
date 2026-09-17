package com.bubbleanyway.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Minimal client-to-server bubble click payload. */
public record BubbleClickPayload(String bubbleId, String controlId) implements CustomPayload {
    public static final CustomPayload.Id<BubbleClickPayload> ID =
            new CustomPayload.Id<>(Identifier.of("bubble_anyway", "bubble_click"));
    public static final PacketCodec<PacketByteBuf, BubbleClickPayload> CODEC =
            PacketCodec.ofStatic(BubbleClickPayload::encode, BubbleClickPayload::decode);

    public static BubbleClickPayload click(String bubbleId, String controlId) {
        return new BubbleClickPayload(bubbleId, controlId);
    }

    public static BubbleClickPayload decode(PacketByteBuf buffer) {
        return new BubbleClickPayload(buffer.readString(128), buffer.readString(128));
    }

    public static void encode(PacketByteBuf buffer, BubbleClickPayload payload) {
        buffer.writeString(payload.bubbleId(), 128);
        buffer.writeString(payload.controlId(), 128);
    }

    @Override
    public CustomPayload.Id<BubbleClickPayload> getId() {
        return ID;
    }
}
