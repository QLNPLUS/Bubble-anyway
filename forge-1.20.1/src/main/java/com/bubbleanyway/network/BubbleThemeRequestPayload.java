package com.bubbleanyway.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client request for one theme definition when its local catalog has no match. */
public final class BubbleThemeRequestPayload {
    private final String themeId;

    private BubbleThemeRequestPayload(String themeId) {
        this.themeId = themeId;
    }

    public static BubbleThemeRequestPayload request(String themeId) {
        return new BubbleThemeRequestPayload(themeId);
    }

    public static void encode(BubbleThemeRequestPayload payload, FriendlyByteBuf buffer) {
        buffer.writeUtf(payload.themeId, 256);
    }

    public static BubbleThemeRequestPayload decode(FriendlyByteBuf buffer) {
        return request(buffer.readUtf(256));
    }

    public static void handle(BubbleThemeRequestPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> BubbleNetwork.sendThemeDefinition(sender, payload.themeId));
        }
        context.setPacketHandled(true);
    }
}
