package com.bubbleanyway.network;

import com.bubbleanyway.BubbleAnyway;
import com.bubbleanyway.data.BubbleThemeManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sends server theme definitions to a client. */
public record BubbleThemeSyncPayload(Map<String, String> themes, boolean replace) implements CustomPacketPayload {
    private static final int MAX_THEMES = 4096;
    private static final int MAX_THEME_JSON = 1_000_000;
    public static final Type<BubbleThemeSyncPayload> TYPE = new Type<>(BubbleAnyway.id("bubble_theme_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BubbleThemeSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BubbleThemeSyncPayload decode(RegistryFriendlyByteBuf buffer) {
            boolean replace = buffer.readBoolean();
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_THEMES) {
                throw new IllegalArgumentException("Invalid Bubble Anyway theme count: " + count);
            }
            Map<String, String> themes = new LinkedHashMap<>();
            for (int index = 0; index < count; index++) {
                themes.put(buffer.readUtf(256), buffer.readUtf(MAX_THEME_JSON));
            }
            return new BubbleThemeSyncPayload(themes, replace);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, BubbleThemeSyncPayload payload) {
            buffer.writeBoolean(payload.replace());
            buffer.writeVarInt(payload.themes().size());
            for (Map.Entry<String, String> entry : payload.themes().entrySet()) {
                buffer.writeUtf(entry.getKey(), 256);
                buffer.writeUtf(entry.getValue(), MAX_THEME_JSON);
            }
        }
    };

    public BubbleThemeSyncPayload {
        themes = Map.copyOf(themes);
    }

    public static BubbleThemeSyncPayload fromCurrentThemes() {
        return new BubbleThemeSyncPayload(BubbleThemeManager.snapshot(), true);
    }

    public static Optional<BubbleThemeSyncPayload> fromTheme(String themeId) {
        return BubbleThemeManager.encoded(themeId)
                .map(json -> new BubbleThemeSyncPayload(Map.of(themeId, json), false));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
