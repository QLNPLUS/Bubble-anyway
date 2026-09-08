package com.bubbleanyway.network;

import com.bubbleanyway.client.BubbleThemeClientCache;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Sends a bubble using a client-side cached theme and small per-bubble overrides. */
public final class BubbleThemePayload {
    private final String themeId;
    private final String overridesJson;

    private BubbleThemePayload(String themeId, String overridesJson) {
        this.themeId = themeId;
        this.overridesJson = overridesJson;
    }

    public String themeId() {
        return themeId;
    }

    public String overridesJson() {
        return overridesJson;
    }

    public static void encode(BubbleThemePayload payload, FriendlyByteBuf buffer) {
        buffer.writeUtf(payload.themeId, 256);
        buffer.writeUtf(payload.overridesJson, 32767);
    }

    public static BubbleThemePayload decode(FriendlyByteBuf buffer) {
        return show(buffer.readUtf(256), buffer.readUtf(32767));
    }

    public static void handle(BubbleThemePayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> BubbleThemeClientCache.enqueue(payload.themeId, payload.overridesJson));
        context.setPacketHandled(true);
    }

    public static BubbleThemePayload show(String themeId, String overridesJson) {
        return new BubbleThemePayload(themeId, overridesJson);
    }
}
