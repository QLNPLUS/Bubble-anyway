package com.bubbleanyway.network;

import com.bubbleanyway.data.BubbleThemeManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Sends one requested server theme definition to a client. */
public final class BubbleThemeSyncPayload {
    private static final int MAX_THEMES = 4096;
    private static final int MAX_THEME_JSON = 1_000_000;

    private final Map<String, String> themes;
    private final boolean replace;

    private BubbleThemeSyncPayload(Map<String, String> themes, boolean replace) {
        this.themes = Map.copyOf(themes);
        this.replace = replace;
    }

    public static BubbleThemeSyncPayload fromCurrentThemes() {
        return new BubbleThemeSyncPayload(BubbleThemeManager.snapshot(), true);
    }

    public static Optional<BubbleThemeSyncPayload> fromTheme(String themeId) {
        return BubbleThemeManager.encoded(themeId)
                .map(json -> new BubbleThemeSyncPayload(Map.of(themeId, json), false));
    }

    public Map<String, String> themes() {
        return themes;
    }

    public static void encode(BubbleThemeSyncPayload payload, FriendlyByteBuf buffer) {
        buffer.writeBoolean(payload.replace);
        buffer.writeVarInt(payload.themes.size());
        for (Map.Entry<String, String> entry : payload.themes.entrySet()) {
            buffer.writeUtf(entry.getKey(), 256);
            buffer.writeUtf(entry.getValue(), MAX_THEME_JSON);
        }
    }

    public static BubbleThemeSyncPayload decode(FriendlyByteBuf buffer) {
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

    public static void handle(BubbleThemeSyncPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (payload.replace) {
                com.bubbleanyway.client.BubbleThemeClientCache.replaceServerThemes(payload.themes);
            } else {
                com.bubbleanyway.client.BubbleThemeClientCache.mergeServerThemes(payload.themes);
            }
        });
        context.setPacketHandled(true);
    }
}
