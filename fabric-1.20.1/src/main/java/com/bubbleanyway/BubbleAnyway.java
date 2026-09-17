package com.bubbleanyway;

import com.bubbleanyway.command.BubbleCommand;
import com.bubbleanyway.data.BubbleThemeDefaults;
import com.bubbleanyway.data.BubbleThemeManager;
import com.bubbleanyway.network.BubbleNetwork;
import com.bubbleanyway.network.BubbleClickPayload;
import com.bubbleanyway.network.BubbleInteractionManager;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

public final class BubbleAnyway implements ModInitializer {
    public static final String MOD_ID = "bubble_anyway";
    private static volatile MinecraftServer server;

    @Override
    public void onInitialize() {
        BubbleThemeDefaults.ensure(FabricLoader.getInstance().getConfigDir());
        BubbleThemeManager.registerReloadListener();
        BubbleThemeManager.setReloadListener(() -> {
            MinecraftServer current = server;
            if (current != null) {
                current.execute(() -> BubbleNetwork.syncThemes(current.getPlayerManager().getPlayerList()));
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> BubbleCommand.register(dispatcher));
        ServerPlayNetworking.registerGlobalReceiver(BubbleNetwork.THEME_REQUEST_CHANNEL,
                (server, player, handler, buffer, responseSender) -> {
                    String themeId = buffer.readString(256);
                    server.execute(() -> BubbleNetwork.sendThemeDefinition(player, themeId));
                });
        ServerPlayNetworking.registerGlobalReceiver(BubbleClickPayload.CHANNEL,
                (server, player, handler, buffer, responseSender) -> {
                    BubbleClickPayload payload = BubbleClickPayload.decode(buffer);
                    server.execute(() -> BubbleInteractionManager.handleClick(
                            player, payload.bubbleId(), payload.controlId()));
                });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                BubbleInteractionManager.clear(handler.player));
        ServerLifecycleEvents.SERVER_STARTED.register(current -> {
            server = current;
            BubbleNetwork.syncThemes(current.getPlayerManager().getPlayerList());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(current -> server = null);
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
