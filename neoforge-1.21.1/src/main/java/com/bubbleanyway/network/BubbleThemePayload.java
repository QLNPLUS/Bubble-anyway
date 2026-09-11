package com.bubbleanyway.network;

import com.bubbleanyway.BubbleAnyway;
import com.bubbleanyway.client.BubbleThemeClientCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sends a bubble using a client-local theme and small per-bubble overrides. */
public record BubbleThemePayload(String themeId, String overridesJson) implements CustomPacketPayload {
    public static final Type<BubbleThemePayload> TYPE = new Type<>(BubbleAnyway.id("bubble_theme"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BubbleThemePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BubbleThemePayload decode(RegistryFriendlyByteBuf buffer) {
            return new BubbleThemePayload(buffer.readUtf(256), buffer.readUtf(32767));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, BubbleThemePayload payload) {
            buffer.writeUtf(payload.themeId(), 256);
            buffer.writeUtf(payload.overridesJson(), 32767);
        }
    };

    public static BubbleThemePayload show(String themeId, String overridesJson) {
        return new BubbleThemePayload(themeId, overridesJson);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
