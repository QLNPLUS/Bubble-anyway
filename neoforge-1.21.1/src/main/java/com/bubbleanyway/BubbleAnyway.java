package com.bubbleanyway;

import com.bubbleanyway.command.BubbleCommand;
import com.bubbleanyway.data.BubbleThemeDefaults;
import com.bubbleanyway.data.BubbleThemeManager;
import com.bubbleanyway.network.BubbleNetwork;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

@Mod(BubbleAnyway.MOD_ID)
public final class BubbleAnyway {
    public static final String MOD_ID = "bubble_anyway";

    public BubbleAnyway(IEventBus modEventBus) {
        BubbleThemeDefaults.ensure(FMLPaths.CONFIGDIR.get());
        ModList.get().getModContainerById(MOD_ID).ifPresent(container ->
                com.bubbleanyway.config.BubbleClientConfig.register(container));
        modEventBus.addListener(BubbleNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener(BubbleCommand::register);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> BubbleThemeManager.registerReloadListener(event));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                BubbleNetwork.syncThemes(event.getServer().getPlayerList().getPlayers()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                BubbleNetwork.syncThemes(List.of(player));
            }
        });
        BubbleThemeManager.setReloadListener(() -> {
            net.minecraft.server.MinecraftServer server =
                    net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.executeIfPossible(() -> BubbleNetwork.syncThemes(server.getPlayerList().getPlayers()));
            }
        });
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
