package com.bubbleanyway.network;

import com.bubbleanyway.data.BubbleSpec;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BubblePayload(BubbleSpec spec, boolean clear) implements CustomPayload {
    public static final CustomPayload.Id<BubblePayload> ID = new CustomPayload.Id<>(Identifier.of("bubble_anyway", "bubble"));
    public static final PacketCodec<PacketByteBuf, BubblePayload> CODEC =
            PacketCodec.ofStatic(BubblePayload::encode, BubblePayload::decode);

    @Override
    public CustomPayload.Id<BubblePayload> getId() {
        return ID;
    }

    public static BubblePayload show(BubbleSpec spec) {
        return new BubblePayload(spec, false);
    }

    public static BubblePayload clearAll() {
        return new BubblePayload(null, true);
    }

    public static BubblePayload decode(PacketByteBuf buffer) {
        boolean clear = buffer.readBoolean();
        if (clear) {
            return clearAll();
        }

        BubbleSpec spec = new BubbleSpec(
                buffer.readString(128),
                buffer.readString(32767),
                buffer.readString(128),
                BubbleSpec.IconType.parse(buffer.readString(32)),
                BubbleSpec.parseTextPartsJson(buffer.readString(32767)),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readString(256),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readString(256),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                BubbleSpec.TextAlignment.parse(buffer.readString(32)),
                buffer.readInt(),
                buffer.readInt(),
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
                BubbleSpec.Anchor.parse(buffer.readString(32)),
                BubbleSpec.Animation.parse(buffer.readString(32)));
        return show(spec.withRenderLayer(BubbleSpec.RenderLayer.parse(buffer.readString(32))));
    }

    public static void encode(PacketByteBuf buffer, BubblePayload payload) {
        buffer.writeBoolean(payload.clear());
        if (payload.clear()) {
            return;
        }

        BubbleSpec spec = payload.spec();
        buffer.writeString(spec.id(), 128);
        buffer.writeString(spec.text(), 32767);
        buffer.writeString(spec.iconId(), 128);
        buffer.writeString(spec.iconType().name(), 32);
        buffer.writeString(spec.textPartsJson(), 32767);
        buffer.writeInt(spec.iconSize());
        buffer.writeInt(spec.iconGap());
        buffer.writeInt(spec.iconOffsetX());
        buffer.writeInt(spec.iconOffsetY());
        buffer.writeInt(spec.textOffsetX());
        buffer.writeInt(spec.textOffsetY());
        buffer.writeInt(spec.textColor());
        buffer.writeInt(spec.backgroundColor());
        buffer.writeString(spec.backgroundTexture(), 256);
        buffer.writeInt(spec.backgroundBorder());
        buffer.writeInt(spec.backgroundGuide());
        buffer.writeString(spec.sound(), 256);
        buffer.writeFloat(spec.soundVolume());
        buffer.writeFloat(spec.soundPitch());
        buffer.writeInt(spec.x());
        buffer.writeInt(spec.y());
        buffer.writeInt(spec.width());
        buffer.writeInt(spec.height());
        buffer.writeInt(spec.maxWidth());
        buffer.writeInt(spec.padding());
        buffer.writeString(spec.textAlignment().name(), 32);
        buffer.writeInt(spec.duration());
        buffer.writeInt(spec.fadeIn());
        buffer.writeInt(spec.fadeOut());
        buffer.writeInt(spec.slideIn());
        buffer.writeInt(spec.slideOut());
        buffer.writeInt(spec.priority());
        buffer.writeFloat(spec.scale());
        buffer.writeBoolean(spec.bold());
        buffer.writeBoolean(spec.italic());
        buffer.writeBoolean(spec.underlined());
        buffer.writeBoolean(spec.strikethrough());
        buffer.writeBoolean(spec.obfuscated());
        buffer.writeBoolean(spec.shadow());
        buffer.writeBoolean(spec.replace());
        buffer.writeString(spec.anchor().name(), 32);
        buffer.writeString(spec.animation().name(), 32);
        buffer.writeString(spec.renderLayer().name(), 32);
    }
}
