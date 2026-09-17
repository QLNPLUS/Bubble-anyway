package com.bubbleanyway.network;

import com.bubbleanyway.BubbleAnyway;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** Minimal client-to-server bubble click payload. */
public record BubbleClickPayload(String bubbleId, String controlId) {
    public static final Identifier CHANNEL = BubbleAnyway.id("bubble_click");

    public static BubbleClickPayload decode(PacketByteBuf buffer) {
        return new BubbleClickPayload(buffer.readString(128), buffer.readString(128));
    }

    public static void encode(PacketByteBuf buffer, BubbleClickPayload payload) {
        buffer.writeString(payload.bubbleId(), 128);
        buffer.writeString(payload.controlId(), 128);
    }
}
