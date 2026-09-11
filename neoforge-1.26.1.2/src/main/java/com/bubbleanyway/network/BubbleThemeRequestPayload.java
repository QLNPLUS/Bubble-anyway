package com.bubbleanyway.network;

import com.bubbleanyway.BubbleAnyway;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Requests one missing theme from the server. */
public record BubbleThemeRequestPayload(String themeId) implements CustomPacketPayload {
    public static final Type<BubbleThemeRequestPayload> TYPE = new Type<>(BubbleAnyway.id("bubble_theme_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BubbleThemeRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BubbleThemeRequestPayload decode(RegistryFriendlyByteBuf buffer) {
            return new BubbleThemeRequestPayload(buffer.readUtf(256));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, BubbleThemeRequestPayload payload) {
            buffer.writeUtf(payload.themeId(), 256);
        }
    };

    public static BubbleThemeRequestPayload request(String themeId) {
        return new BubbleThemeRequestPayload(themeId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
