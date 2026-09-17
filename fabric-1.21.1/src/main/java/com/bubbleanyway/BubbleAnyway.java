package com.bubbleanyway;

import com.bubbleanyway.command.BubbleCommand;
import com.bubbleanyway.data.BubbleThemeDefaults;
import com.bubbleanyway.data.BubbleThemeManager;
import com.bubbleanyway.network.BubbleNetwork;
import com.bubbleanyway.network.BubblePayload;
import com.bubbleanyway.network.BubbleThemeRequestPayload;
import com.bubbleanyway.network.BubbleThemePayload;
import com.bubbleanyway.network.BubbleThemeSyncPayload;
import com.bubbleanyway.network.BubbleClickPayload;
import com.bubbleanyway.network.BubbleInteractionManager;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Identifier;
import net.minecraft.server.MinecraftServer;

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
        PayloadTypeRegistry.playS2C().register(BubblePayload.ID, BubblePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BubbleThemePayload.ID, BubbleThemePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BubbleThemeSyncPayload.ID, BubbleThemeSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BubbleThemeRequestPayload.ID, BubbleThemeRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BubbleClickPayload.ID, BubbleClickPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BubbleThemeRequestPayload.ID, (payload, context) ->
                context.server().execute(() -> BubbleNetwork.sendThemeDefinition(context.player(), payload.themeId())));
        ServerPlayNetworking.registerGlobalReceiver(BubbleClickPayload.ID, (payload, context) ->
                context.server().execute(() -> BubbleInteractionManager.handleClick(
                        context.player(), payload.bubbleId(), payload.controlId())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                BubbleInteractionManager.clear(handler.player));
        ServerLifecycleEvents.SERVER_STARTED.register(current -> {
            server = current;
            BubbleNetwork.syncThemes(current.getPlayerManager().getPlayerList());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(current -> server = null);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> BubbleCommand.register(dispatcher));
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
