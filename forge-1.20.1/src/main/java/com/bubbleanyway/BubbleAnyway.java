package com.bubbleanyway;

import com.bubbleanyway.command.BubbleCommand;
import com.bubbleanyway.data.BubbleThemeDefaults;
import com.bubbleanyway.data.BubbleThemeManager;
import com.bubbleanyway.network.BubbleNetwork;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod(BubbleAnyway.MOD_ID)
public final class BubbleAnyway {
    public static final String MOD_ID = "bubble_anyway";

    public BubbleAnyway() {
        BubbleThemeDefaults.ensure(FMLPaths.CONFIGDIR.get());
        BubbleNetwork.register();
        BubbleThemeManager.setReloadListener(BubbleAnyway::syncThemesToClients);
        MinecraftForge.EVENT_BUS.addListener(BubbleCommand::register);
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) ->
                BubbleThemeManager.registerReloadListener(event));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private static void syncThemesToClients() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.executeIfPossible(() -> BubbleNetwork.syncThemes(server.getPlayerList().getPlayers()));
        }
    }
}
