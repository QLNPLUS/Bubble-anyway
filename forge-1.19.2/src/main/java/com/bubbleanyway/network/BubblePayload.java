package com.bubbleanyway.network;

import com.bubbleanyway.data.BubbleSpec;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public final class BubblePayload {
    private final BubbleSpec spec;
    private final boolean clear;

    private BubblePayload(BubbleSpec spec, boolean clear) {
        this.spec = spec;
        this.clear = clear;
    }

    public BubbleSpec spec() {
        return spec;
    }

    public boolean clear() {
        return clear;
    }

    public static void encode(BubblePayload payload, FriendlyByteBuf buffer) {
        buffer.writeBoolean(payload.clear);
        if (payload.clear) {
            return;
        }

        BubbleSpec spec = payload.spec;
        buffer.writeUtf(spec.id(), 128);
        buffer.writeUtf(spec.text(), 32767);
        buffer.writeUtf(spec.iconId(), 128);
        buffer.writeInt(spec.iconSize());
        buffer.writeInt(spec.iconGap());
        buffer.writeInt(spec.iconOffsetX());
        buffer.writeInt(spec.iconOffsetY());
        buffer.writeInt(spec.textOffsetX());
        buffer.writeInt(spec.textOffsetY());
        buffer.writeInt(spec.textColor());
        buffer.writeInt(spec.backgroundColor());
        buffer.writeUtf(spec.backgroundTexture(), 256);
        buffer.writeInt(spec.backgroundBorder());
        buffer.writeInt(spec.backgroundGuide());
        buffer.writeUtf(spec.sound(), 256);
        buffer.writeFloat(spec.soundVolume());
        buffer.writeFloat(spec.soundPitch());
        buffer.writeInt(spec.x());
        buffer.writeInt(spec.y());
        buffer.writeInt(spec.width());
        buffer.writeInt(spec.height());
        buffer.writeInt(spec.maxWidth());
        buffer.writeInt(spec.padding());
        buffer.writeUtf(spec.textAlignment().name(), 32);
        buffer.writeInt(spec.duration());
        buffer.writeInt(spec.fadeIn());
        buffer.writeInt(spec.fadeOut());
        buffer.writeInt(spec.priority());
        buffer.writeFloat(spec.scale());
        buffer.writeBoolean(spec.bold());
        buffer.writeBoolean(spec.italic());
        buffer.writeBoolean(spec.underlined());
        buffer.writeBoolean(spec.strikethrough());
        buffer.writeBoolean(spec.obfuscated());
        buffer.writeBoolean(spec.shadow());
        buffer.writeBoolean(spec.replace());
        buffer.writeUtf(spec.anchor().name(), 32);
        buffer.writeUtf(spec.animation().name(), 32);
    }

    public static BubblePayload decode(FriendlyByteBuf buffer) {
        boolean clear = buffer.readBoolean();
        if (clear) {
            return clearAll();
        }

        BubbleSpec spec = new BubbleSpec(
                buffer.readUtf(128),
                buffer.readUtf(32767),
                buffer.readUtf(128),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readUtf(256),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readUtf(256),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                BubbleSpec.TextAlignment.parse(buffer.readUtf(32)),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readFloat(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                BubbleSpec.Anchor.parse(buffer.readUtf(32)),
                BubbleSpec.Animation.parse(buffer.readUtf(32)));
        return show(spec);
    }

    public static void handle(BubblePayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (payload.clear) {
                com.bubbleanyway.client.BubbleOverlay.clear();
            } else {
                com.bubbleanyway.client.BubbleOverlay.enqueue(payload.spec);
            }
        });
        context.setPacketHandled(true);
    }

    public static BubblePayload show(BubbleSpec spec) {
        return new BubblePayload(spec, false);
    }

    public static BubblePayload clearAll() {
        return new BubblePayload(null, true);
    }

}
