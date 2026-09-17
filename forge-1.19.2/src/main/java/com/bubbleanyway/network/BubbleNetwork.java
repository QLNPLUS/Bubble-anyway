package com.bubbleanyway.network;

import com.bubbleanyway.data.BubbleSpec;
import java.util.Collection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BubbleNetwork {
    private static final String PROTOCOL_VERSION = "4";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("bubble_anyway", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);
    private static int messageId;

    private BubbleNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(
                messageId++,
                BubblePayload.class,
                BubblePayload::encode,
                BubblePayload::decode,
                BubblePayload::handle);
        CHANNEL.registerMessage(
                messageId++,
                BubbleThemePayload.class,
                BubbleThemePayload::encode,
                BubbleThemePayload::decode,
                BubbleThemePayload::handle);
        CHANNEL.registerMessage(
                messageId++,
                BubbleThemeRequestPayload.class,
                BubbleThemeRequestPayload::encode,
                BubbleThemeRequestPayload::decode,
                BubbleThemeRequestPayload::handle);
        CHANNEL.registerMessage(
                messageId++,
                BubbleThemeSyncPayload.class,
                BubbleThemeSyncPayload::encode,
                BubbleThemeSyncPayload::decode,
                BubbleThemeSyncPayload::handle);
        CHANNEL.registerMessage(
                messageId++,
                BubbleClickPayload.class,
                BubbleClickPayload::encode,
                BubbleClickPayload::decode,
                BubbleClickPayload::handle);
    }

    public static void send(Collection<ServerPlayer> players, BubbleSpec spec) {
        BubblePayload payload = BubblePayload.show(spec);
        for (ServerPlayer player : players) {
            BubbleInteractionManager.register(player, spec);
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        }
    }

    public static void sendTheme(Collection<ServerPlayer> players, String themeId, String overridesJson) {
        BubbleThemePayload payload = BubbleThemePayload.show(themeId, overridesJson);
        BubbleSpec resolved = com.bubbleanyway.data.BubbleThemeManager.resolve(themeId, overridesJson);
        for (ServerPlayer player : players) {
            BubbleInteractionManager.register(player, resolved);
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        }
    }

    public static void requestTheme(String themeId) {
        CHANNEL.sendToServer(BubbleThemeRequestPayload.request(themeId));
    }

    public static void sendThemeDefinition(ServerPlayer player, String themeId) {
        if (player != null) {
            BubbleThemeSyncPayload.fromTheme(themeId).ifPresent(payload ->
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload));
        }
    }

    public static void syncThemes(Collection<ServerPlayer> players) {
        BubbleThemeSyncPayload payload = BubbleThemeSyncPayload.fromCurrentThemes();
        for (ServerPlayer player : players) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        }
    }

    public static void clear(Collection<ServerPlayer> players) {
        BubblePayload payload = BubblePayload.clearAll();
        for (ServerPlayer player : players) {
            BubbleInteractionManager.clear(player);
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        }
    }

    public static void sendClickToServer(String bubbleId, String controlId) {
        CHANNEL.sendToServer(BubbleClickPayload.click(bubbleId, controlId));
    }
}
