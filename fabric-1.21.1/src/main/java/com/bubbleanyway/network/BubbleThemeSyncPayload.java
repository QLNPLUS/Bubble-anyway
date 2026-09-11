package com.bubbleanyway.network;

import com.bubbleanyway.data.BubbleThemeManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Sends one or more server-side theme definitions to a client. */
public record BubbleThemeSyncPayload(Map<String, String> themes, boolean replace) implements CustomPayload {
    private static final int MAX_THEMES = 4096;
    private static final int MAX_THEME_JSON = 1_000_000;
    public static final CustomPayload.Id<BubbleThemeSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of("bubble_anyway", "bubble_theme_sync"));
    public static final PacketCodec<PacketByteBuf, BubbleThemeSyncPayload> CODEC =
            PacketCodec.ofStatic(BubbleThemeSyncPayload::encode, BubbleThemeSyncPayload::decode);

    public BubbleThemeSyncPayload {
        themes = Map.copyOf(themes);
    }

    @Override
    public CustomPayload.Id<BubbleThemeSyncPayload> getId() {
        return ID;
    }

    public static BubbleThemeSyncPayload fromCurrentThemes() {
        return new BubbleThemeSyncPayload(BubbleThemeManager.snapshot(), true);
    }

    public static Optional<BubbleThemeSyncPayload> fromTheme(String themeId) {
        return BubbleThemeManager.encoded(themeId)
                .map(json -> new BubbleThemeSyncPayload(Map.of(themeId, json), false));
    }

    public static BubbleThemeSyncPayload decode(PacketByteBuf buffer) {
        boolean replace = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_THEMES) {
            throw new IllegalArgumentException("Invalid Bubble Anyway theme count: " + count);
        }
        Map<String, String> themes = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            themes.put(buffer.readString(256), buffer.readString(MAX_THEME_JSON));
        }
        return new BubbleThemeSyncPayload(themes, replace);
    }

    public static void encode(PacketByteBuf buffer, BubbleThemeSyncPayload payload) {
        buffer.writeBoolean(payload.replace());
        buffer.writeVarInt(payload.themes().size());
        for (Map.Entry<String, String> entry : payload.themes().entrySet()) {
            buffer.writeString(entry.getKey(), 256);
            buffer.writeString(entry.getValue(), MAX_THEME_JSON);
        }
    }
}
