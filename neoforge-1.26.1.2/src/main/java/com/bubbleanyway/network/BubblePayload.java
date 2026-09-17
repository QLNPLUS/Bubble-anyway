package com.bubbleanyway.network;

import com.bubbleanyway.BubbleAnyway;
import com.bubbleanyway.data.BubbleControls;
import com.bubbleanyway.data.BubbleSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BubblePayload(BubbleSpec spec, boolean clear) implements CustomPacketPayload {
    public static final Type<BubblePayload> TYPE = new Type<>(BubbleAnyway.id("bubble"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BubblePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BubblePayload decode(RegistryFriendlyByteBuf buffer) {
            boolean clear = buffer.readBoolean();
            if (clear) {
                return new BubblePayload(null, true);
            }

            String id = buffer.readUtf(128);
            String text = buffer.readUtf(32767);
            String iconId = buffer.readUtf(128);
            BubbleSpec.IconType iconType = BubbleSpec.IconType.parse(buffer.readUtf(32));
            String textPartsJson = buffer.readUtf(32767);
            String controlsJson = buffer.readBoolean() ? buffer.readUtf(32767) : "{}";
            int iconSize = buffer.readInt();
            int iconGap = buffer.readInt();
            int iconOffsetX = buffer.readInt();
            int iconOffsetY = buffer.readInt();
            int textOffsetX = buffer.readInt();
            int textOffsetY = buffer.readInt();
            int textColor = buffer.readInt();
            int backgroundColor = buffer.readInt();
            String backgroundTexture = buffer.readUtf(256);
            int backgroundBorder = buffer.readInt();
            int backgroundGuide = buffer.readInt();
            String sound = buffer.readUtf(256);
            float soundVolume = buffer.readFloat();
            float soundPitch = buffer.readFloat();
            int x = buffer.readInt();
            int y = buffer.readInt();
            int width = buffer.readInt();
            int height = buffer.readInt();
            int maxWidth = buffer.readInt();
            int padding = buffer.readInt();
            int lineSpacing = buffer.readInt();
            BubbleSpec.TextAlignment textAlignment = BubbleSpec.TextAlignment.parse(buffer.readUtf(32));
            int duration = buffer.readInt();
            int fadeIn = buffer.readInt();
            int fadeOut = buffer.readInt();
            int slideIn = buffer.readInt();
            int slideOut = buffer.readInt();
            int priority = buffer.readInt();
            float scale = buffer.readFloat();
            boolean bold = buffer.readBoolean();
            boolean italic = buffer.readBoolean();
            boolean underlined = buffer.readBoolean();
            boolean strikethrough = buffer.readBoolean();
            boolean obfuscated = buffer.readBoolean();
            boolean shadow = buffer.readBoolean();
            boolean replace = buffer.readBoolean();
            boolean remove = buffer.readBoolean();
            BubbleSpec.Anchor anchor = BubbleSpec.Anchor.parse(buffer.readUtf(32));
            BubbleSpec.Animation animation = BubbleSpec.Animation.parse(buffer.readUtf(32));
            BubbleSpec.RenderLayer renderLayer = BubbleSpec.RenderLayer.parse(buffer.readUtf(32));
            BubbleSpec spec = new BubbleSpec(
                    id, text, iconId, iconType, BubbleSpec.parseTextPartsJson(textPartsJson),
                    iconSize, iconGap, iconOffsetX, iconOffsetY, textOffsetX, textOffsetY,
                    textColor, backgroundColor, backgroundTexture, backgroundBorder, backgroundGuide,
                    sound, soundVolume, soundPitch, x, y, width, height, maxWidth, padding,
                    textAlignment, duration, fadeIn, fadeOut, slideIn, slideOut, priority, scale,
                    bold, italic, underlined, strikethrough, obfuscated, shadow, replace,
                    anchor, animation, lineSpacing, remove,
                    BubbleControls.fromJson(com.google.gson.JsonParser.parseString(controlsJson)));
            return new BubblePayload(spec.withRenderLayer(renderLayer), false);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, BubblePayload payload) {
            buffer.writeBoolean(payload.clear());
            if (payload.clear()) {
                return;
            }

            BubbleSpec spec = payload.spec();
            buffer.writeUtf(spec.id(), 128);
            buffer.writeUtf(spec.text(), 32767);
            buffer.writeUtf(spec.iconId(), 128);
            buffer.writeUtf(spec.iconType().name(), 32);
            buffer.writeUtf(spec.textPartsJson(), 32767);
            buffer.writeBoolean(!spec.controls().isEmpty());
            if (!spec.controls().isEmpty()) {
                buffer.writeUtf(spec.controlsJson(), 32767);
            }
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
            buffer.writeInt(spec.lineSpacing());
            buffer.writeUtf(spec.textAlignment().name(), 32);
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
            buffer.writeBoolean(spec.remove());
            buffer.writeUtf(spec.anchor().name(), 32);
            buffer.writeUtf(spec.animation().name(), 32);
            buffer.writeUtf(spec.renderLayer().name(), 32);
        }
    };

    public static BubblePayload show(BubbleSpec spec) {
        return new BubblePayload(spec, false);
    }

    public static BubblePayload clearAll() {
        return new BubblePayload(null, true);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
