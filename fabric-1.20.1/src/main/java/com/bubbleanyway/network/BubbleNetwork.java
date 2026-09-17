package com.bubbleanyway.network;

import com.bubbleanyway.BubbleAnyway;
import com.bubbleanyway.data.BubbleSpec;
import com.bubbleanyway.data.BubbleThemeManager;
import java.util.Collection;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class BubbleNetwork {
    public static final Identifier CHANNEL = BubbleAnyway.id("bubble");
    public static final Identifier THEME_CHANNEL = BubbleAnyway.id("bubble_theme");
    public static final Identifier THEME_REQUEST_CHANNEL = BubbleAnyway.id("bubble_theme_request");
    public static final Identifier THEME_SYNC_CHANNEL = BubbleAnyway.id("bubble_theme_sync");

    private BubbleNetwork() {
    }

    public static void send(Collection<ServerPlayerEntity> players, BubbleSpec spec) {
        for (ServerPlayerEntity player : players) {
            BubbleInteractionManager.register(player, spec);
            PacketByteBuf buffer = PacketByteBufs.create();
            BubblePayload.encode(buffer, BubblePayload.show(spec));
            ServerPlayNetworking.send(player, CHANNEL, buffer);
        }
    }

    public static void clear(Collection<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            BubbleInteractionManager.clear(player);
            PacketByteBuf buffer = PacketByteBufs.create();
            BubblePayload.encode(buffer, BubblePayload.clearAll());
            ServerPlayNetworking.send(player, CHANNEL, buffer);
        }
    }

    public static void sendTheme(Collection<ServerPlayerEntity> players, String themeId, String overridesJson) {
        BubbleSpec resolved = BubbleThemeManager.resolve(themeId, overridesJson);
        for (ServerPlayerEntity player : players) {
            BubbleInteractionManager.register(player, resolved);
            PacketByteBuf buffer = PacketByteBufs.create();
            BubbleThemePayload.encode(buffer, BubbleThemePayload.show(themeId, overridesJson));
            ServerPlayNetworking.send(player, THEME_CHANNEL, buffer);
        }
    }

    public static void sendThemeDefinition(ServerPlayerEntity player, String themeId) {
        if (player == null) {
            return;
        }
        BubbleThemeSyncPayload.fromTheme(themeId).ifPresent(payload -> {
            PacketByteBuf buffer = PacketByteBufs.create();
            BubbleThemeSyncPayload.encode(buffer, payload);
            ServerPlayNetworking.send(player, THEME_SYNC_CHANNEL, buffer);
        });
    }

    public static void syncThemes(Collection<ServerPlayerEntity> players) {
        BubbleThemeSyncPayload payload = BubbleThemeSyncPayload.fromCurrentThemes();
        for (ServerPlayerEntity player : players) {
            PacketByteBuf buffer = PacketByteBufs.create();
            BubbleThemeSyncPayload.encode(buffer, payload);
            ServerPlayNetworking.send(player, THEME_SYNC_CHANNEL, buffer);
        }
    }

    public static void sendClickToServer(String bubbleId, String controlId) {
        PacketByteBuf buffer = PacketByteBufs.create();
        BubbleClickPayload.encode(buffer, new BubbleClickPayload(bubbleId, controlId));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(BubbleClickPayload.CHANNEL, buffer);
    }
}
