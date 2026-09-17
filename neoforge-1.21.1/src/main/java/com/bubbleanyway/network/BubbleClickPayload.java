package com.bubbleanyway.network;

import com.bubbleanyway.BubbleAnyway;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Minimal client-to-server bubble click payload. */
public record BubbleClickPayload(String bubbleId, String controlId) implements CustomPacketPayload {
    public static final Type<BubbleClickPayload> TYPE = new Type<>(BubbleAnyway.id("bubble_click"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BubbleClickPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, BubbleClickPayload::bubbleId,
                    ByteBufCodecs.STRING_UTF8, BubbleClickPayload::controlId,
                    BubbleClickPayload::new);

    public static BubbleClickPayload click(String bubbleId, String controlId) {
        return new BubbleClickPayload(bubbleId, controlId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
