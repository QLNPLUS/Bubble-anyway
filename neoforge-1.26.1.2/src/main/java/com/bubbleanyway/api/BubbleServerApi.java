package com.bubbleanyway.api;

import com.bubbleanyway.data.BubbleSpec;
import com.bubbleanyway.data.BubbleThemeManager;
import com.bubbleanyway.network.BubbleNetwork;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server-side entry point for mods that want to show bubbles without commands. */
public final class BubbleServerApi {
    private BubbleServerApi() {
    }

    public static void show(ServerPlayer player, BubbleSpec spec) {
        if (player != null) {
            show(List.of(player), spec);
        }
    }

    public static void show(Collection<? extends ServerPlayer> players, BubbleSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("Bubble spec cannot be null");
        }

        List<ServerPlayer> targets = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (player != null) {
                targets.add(player);
            }
        }
        BubbleNetwork.send(targets, spec);
    }

    public static void showJson(ServerPlayer player, String json) {
        Optional<String> theme = BubbleThemeManager.themeFromJson(json);
        show(player, theme.isPresent()
                ? BubbleThemeManager.resolve(theme.get(), BubbleThemeManager.withoutThemeField(json))
                : BubbleSpec.fromJson(json));
    }

    public static void showJson(Collection<? extends ServerPlayer> players, String json) {
        Optional<String> theme = BubbleThemeManager.themeFromJson(json);
        show(players, theme.isPresent()
                ? BubbleThemeManager.resolve(theme.get(), BubbleThemeManager.withoutThemeField(json))
                : BubbleSpec.fromJson(json));
    }

    public static void showAll(MinecraftServer server, BubbleSpec spec) {
        if (server != null) {
            show(server.getPlayerList().getPlayers(), spec);
        }
    }

    public static void showAllJson(MinecraftServer server, String json) {
        Optional<String> theme = BubbleThemeManager.themeFromJson(json);
        showAll(server, theme.isPresent()
                ? BubbleThemeManager.resolve(theme.get(), BubbleThemeManager.withoutThemeField(json))
                : BubbleSpec.fromJson(json));
    }

    public static void showTheme(ServerPlayer player, String themeId, String text) {
        show(player, BubbleThemeManager.resolve(themeId, BubbleThemeManager.overridesWithText(text)));
    }

    public static void showTheme(Collection<? extends ServerPlayer> players, String themeId, String text) {
        show(players, BubbleThemeManager.resolve(themeId, BubbleThemeManager.overridesWithText(text)));
    }

    public static void showThemeJson(ServerPlayer player, String themeId, String overridesJson) {
        show(player, BubbleThemeManager.resolve(themeId, BubbleThemeManager.withoutThemeField(overridesJson)));
    }

    public static void showThemeJson(Collection<? extends ServerPlayer> players, String themeId, String overridesJson) {
        show(players, BubbleThemeManager.resolve(themeId, BubbleThemeManager.withoutThemeField(overridesJson)));
    }

    public static void showAllTheme(MinecraftServer server, String themeId, String text) {
        showAll(server, BubbleThemeManager.resolve(themeId, BubbleThemeManager.overridesWithText(text)));
    }

    public static void showAllThemeJson(MinecraftServer server, String themeId, String overridesJson) {
        showAll(server, BubbleThemeManager.resolve(themeId, BubbleThemeManager.withoutThemeField(overridesJson)));
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            BubbleNetwork.clear(List.of(player));
        }
    }

    public static void clear(Collection<? extends ServerPlayer> players) {
        List<ServerPlayer> targets = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (player != null) {
                targets.add(player);
            }
        }
        BubbleNetwork.clear(targets);
    }

    public static void clearAll(MinecraftServer server) {
        if (server != null) {
            clear(server.getPlayerList().getPlayers());
        }
    }
}
