package com.bubbleanyway.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client request for a theme that is not present in the local theme catalog. */
public record BubbleThemeRequestPayload(String themeId) implements CustomPayload {
    public static final CustomPayload.Id<BubbleThemeRequestPayload> ID =
            new CustomPayload.Id<>(Identifier.of("bubble_anyway", "bubble_theme_request"));
    public static final PacketCodec<PacketByteBuf, BubbleThemeRequestPayload> CODEC =
            PacketCodec.ofStatic(BubbleThemeRequestPayload::encode, BubbleThemeRequestPayload::decode);

    @Override
    public CustomPayload.Id<BubbleThemeRequestPayload> getId() {
        return ID;
    }

    public static BubbleThemeRequestPayload request(String themeId) {
        return new BubbleThemeRequestPayload(themeId);
    }

    public static BubbleThemeRequestPayload decode(PacketByteBuf buffer) {
        return request(buffer.readString(256));
    }

    public static void encode(PacketByteBuf buffer, BubbleThemeRequestPayload payload) {
        buffer.writeString(payload.themeId(), 256);
    }
}
