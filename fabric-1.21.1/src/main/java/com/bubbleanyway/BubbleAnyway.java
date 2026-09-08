package com.bubbleanyway;

import com.bubbleanyway.command.BubbleCommand;
import com.bubbleanyway.data.BubbleThemeDefaults;
import com.bubbleanyway.network.BubblePayload;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Identifier;

public final class BubbleAnyway implements ModInitializer {
    public static final String MOD_ID = "bubble_anyway";

    @Override
    public void onInitialize() {
        BubbleThemeDefaults.ensure(FabricLoader.getInstance().getConfigDir());
        PayloadTypeRegistry.playS2C().register(BubblePayload.ID, BubblePayload.CODEC);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> BubbleCommand.register(dispatcher));
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
