package com.bubbleanyway;

import com.bubbleanyway.command.BubbleCommand;
import com.bubbleanyway.data.BubbleThemeDefaults;
import com.bubbleanyway.network.BubbleNetwork;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;

@Mod(BubbleAnyway.MOD_ID)
public final class BubbleAnyway {
    public static final String MOD_ID = "bubble_anyway";

    public BubbleAnyway(IEventBus modEventBus) {
        BubbleThemeDefaults.ensure(FMLPaths.CONFIGDIR.get());
        modEventBus.addListener(BubbleNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener(BubbleCommand::register);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
