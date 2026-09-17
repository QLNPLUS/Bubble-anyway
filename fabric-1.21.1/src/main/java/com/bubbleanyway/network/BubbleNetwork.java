package com.bubbleanyway.network;

import com.bubbleanyway.BubbleAnyway;
import com.bubbleanyway.data.BubbleSpec;
import com.bubbleanyway.data.BubbleThemeManager;
import java.util.Collection;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class BubbleNetwork {
    public static final Identifier CHANNEL = BubblePayload.ID.id();
    public static final Identifier THEME_CHANNEL = BubbleThemePayload.ID.id();
    public static final Identifier THEME_REQUEST_CHANNEL = BubbleThemeRequestPayload.ID.id();
    public static final Identifier THEME_SYNC_CHANNEL = BubbleThemeSyncPayload.ID.id();

    private BubbleNetwork() {
    }

    public static void send(Collection<ServerPlayerEntity> players, BubbleSpec spec) {
        for (ServerPlayerEntity player : players) {
            BubbleInteractionManager.register(player, spec);
            ServerPlayNetworking.send(player, BubblePayload.show(spec));
        }
    }

    public static void clear(Collection<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            BubbleInteractionManager.clear(player);
            ServerPlayNetworking.send(player, BubblePayload.clearAll());
        }
    }

    public static void sendTheme(Collection<ServerPlayerEntity> players, String themeId, String overridesJson) {
        BubbleThemePayload payload = BubbleThemePayload.show(themeId, overridesJson);
        BubbleSpec resolved = BubbleThemeManager.resolve(themeId, overridesJson);
        for (ServerPlayerEntity player : players) {
            BubbleInteractionManager.register(player, resolved);
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void sendThemeDefinition(ServerPlayerEntity player, String themeId) {
        if (player != null) {
            BubbleThemeSyncPayload.fromTheme(themeId).ifPresent(payload -> ServerPlayNetworking.send(player, payload));
        }
    }

    public static void syncThemes(Collection<ServerPlayerEntity> players) {
        BubbleThemeSyncPayload payload = BubbleThemeSyncPayload.fromCurrentThemes();
        for (ServerPlayerEntity player : players) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void sendClickToServer(String bubbleId, String controlId) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                BubbleClickPayload.click(bubbleId, controlId));
    }
}
