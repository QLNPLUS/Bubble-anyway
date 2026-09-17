package com.bubbleanyway.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client click payload: only the current bubble id and control id cross the wire. */
public final class BubbleClickPayload {
    private final String bubbleId;
    private final String controlId;

    private BubbleClickPayload(String bubbleId, String controlId) {
        this.bubbleId = bubbleId;
        this.controlId = controlId;
    }

    public static BubbleClickPayload click(String bubbleId, String controlId) {
        return new BubbleClickPayload(bubbleId, controlId);
    }

    public static void encode(BubbleClickPayload payload, FriendlyByteBuf buffer) {
        buffer.writeUtf(payload.bubbleId, 128);
        buffer.writeUtf(payload.controlId, 128);
    }

    public static BubbleClickPayload decode(FriendlyByteBuf buffer) {
        return click(buffer.readUtf(128), buffer.readUtf(128));
    }

    public static void handle(BubbleClickPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> BubbleInteractionManager.handleClick(sender, payload.bubbleId, payload.controlId));
        }
        context.setPacketHandled(true);
    }
}
