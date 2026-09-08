package com.bubbleanyway;

import com.bubbleanyway.command.BubbleCommand;
import com.bubbleanyway.data.BubbleThemeDefaults;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.util.Identifier;

public final class BubbleAnyway implements ModInitializer {
    public static final String MOD_ID = "bubble_anyway";

    @Override
    public void onInitialize() {
        BubbleThemeDefaults.ensure(FabricLoader.getInstance().getConfigDir());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> BubbleCommand.register(dispatcher));
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
