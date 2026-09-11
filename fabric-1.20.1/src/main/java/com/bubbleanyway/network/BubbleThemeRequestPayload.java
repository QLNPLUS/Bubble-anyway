package com.bubbleanyway.network;

import net.minecraft.network.PacketByteBuf;

/** Client request for a theme that is not present in the local theme catalog. */
public record BubbleThemeRequestPayload(String themeId) {
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
