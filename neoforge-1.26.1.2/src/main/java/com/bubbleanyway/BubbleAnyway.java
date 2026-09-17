package com.bubbleanyway;

import com.bubbleanyway.command.BubbleCommand;
import com.bubbleanyway.data.BubbleThemeDefaults;
import com.bubbleanyway.data.BubbleThemeManager;
import com.bubbleanyway.network.BubbleNetwork;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.server.level.ServerPlayer;

@Mod(BubbleAnyway.MOD_ID)
public final class BubbleAnyway {
    public static final String MOD_ID = "bubble_anyway";

    public BubbleAnyway(IEventBus modEventBus) {
        BubbleThemeDefaults.ensure(FMLPaths.CONFIGDIR.get());
        ModList.get().getModContainerById(MOD_ID).ifPresent(container ->
                com.bubbleanyway.config.BubbleClientConfig.register(container));
        modEventBus.addListener(BubbleNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener(BubbleCommand::register);
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) ->
                BubbleThemeManager.registerReloadListener(event));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                BubbleNetwork.syncThemes(event.getServer().getPlayerList().getPlayers()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                com.bubbleanyway.network.BubbleInteractionManager.clear(player);
            }
        });
        BubbleThemeManager.setReloadListener(() -> {
            net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.executeIfPossible(() -> BubbleNetwork.syncThemes(server.getPlayerList().getPlayers()));
            }
        });
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
