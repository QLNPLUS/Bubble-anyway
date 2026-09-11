package com.bubbleanyway.network;

import com.bubbleanyway.client.BubbleThemeClientCache;
import net.minecraft.network.PacketByteBuf;

/** Sends a bubble with a compact theme id and per-bubble overrides. */
public record BubbleThemePayload(String themeId, String overridesJson) {
    public static BubbleThemePayload show(String themeId, String overridesJson) {
        return new BubbleThemePayload(themeId, overridesJson);
    }

    public static BubbleThemePayload decode(PacketByteBuf buffer) {
        return new BubbleThemePayload(buffer.readString(256), buffer.readString(32767));
    }

    public static void encode(PacketByteBuf buffer, BubbleThemePayload payload) {
        buffer.writeString(payload.themeId(), 256);
        buffer.writeString(payload.overridesJson(), 32767);
    }

    public void handleClient() {
        BubbleThemeClientCache.enqueue(themeId(), overridesJson());
    }
}
