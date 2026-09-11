package com.bubbleanyway.api;

import com.bubbleanyway.data.BubbleSpec;
import com.bubbleanyway.data.BubbleThemeManager;
import com.bubbleanyway.network.BubbleNetwork;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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
        if (theme.isPresent()) {
            showThemeJson(player, theme.get(), json);
        } else {
            show(player, BubbleSpec.fromJson(json));
        }
    }

    public static void showJson(Collection<? extends ServerPlayer> players, String json) {
        Optional<String> theme = BubbleThemeManager.themeFromJson(json);
        if (theme.isPresent()) {
            showThemeJson(players, theme.get(), json);
        } else {
            show(players, BubbleSpec.fromJson(json));
        }
    }

    public static void showAll(MinecraftServer server, BubbleSpec spec) {
        if (server != null) {
            show(server.getPlayerList().getPlayers(), spec);
        }
    }

    public static void showAllJson(MinecraftServer server, String json) {
        Optional<String> theme = BubbleThemeManager.themeFromJson(json);
        if (theme.isPresent()) {
            showAllThemeJson(server, theme.get(), json);
        } else {
            showAll(server, BubbleSpec.fromJson(json));
        }
    }

    public static void showTheme(ServerPlayer player, String themeId, String text) {
        showThemeJson(player, themeId, BubbleThemeManager.overridesWithText(text));
    }

    public static void showTheme(Collection<? extends ServerPlayer> players, String themeId, String text) {
        showThemeJson(players, themeId, BubbleThemeManager.overridesWithText(text));
    }

    public static void showThemeJson(ServerPlayer player, String themeId, String overridesJson) {
        if (player != null) {
            showThemeJson(List.of(player), themeId, overridesJson);
        }
    }

    public static void showThemeJson(Collection<? extends ServerPlayer> players, String themeId, String overridesJson) {
        Objects.requireNonNull(players, "players");
        String normalizedOverrides = BubbleThemeManager.withoutThemeField(overridesJson);
        List<ServerPlayer> targets = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (player != null) {
                targets.add(player);
            }
        }
        BubbleNetwork.sendTheme(targets, themeId, normalizedOverrides);
    }

    public static void showAllTheme(MinecraftServer server, String themeId, String text) {
        showAllThemeJson(server, themeId, BubbleThemeManager.overridesWithText(text));
    }

    public static void showAllThemeJson(MinecraftServer server, String themeId, String overridesJson) {
        if (server != null) {
            showThemeJson(server.getPlayerList().getPlayers(), themeId, overridesJson);
        }
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
