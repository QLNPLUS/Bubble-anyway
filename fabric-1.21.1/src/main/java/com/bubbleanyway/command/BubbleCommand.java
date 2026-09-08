package com.bubbleanyway.command;

import com.bubbleanyway.api.BubbleServerApi;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.command.argument.EntityArgumentType;

public final class BubbleCommand {
    private BubbleCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("bubble")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("show")
                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                .then(CommandManager.argument("config", StringArgumentType.greedyString())
                                        .executes(BubbleCommand::show))))
                .then(CommandManager.literal("clear")
                        .executes(context -> clear(context.getSource().getServer().getPlayerManager().getPlayerList()))
                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                .executes(BubbleCommand::clearTargets))));
    }

    private static int show(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        Collection<ServerPlayerEntity> players;
        try {
            players = EntityArgumentType.getPlayers(context, "targets");
            BubbleServerApi.showJson(players, StringArgumentType.getString(context, "config"));
        } catch (RuntimeException exception) {
            source.sendError(Text.literal("Invalid bubble config: " + exception.getMessage()));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Bubble sent to " + players.size() + " player(s)."), true);
        return players.size();
    }

    private static int clear(Collection<ServerPlayerEntity> players) {
        BubbleServerApi.clear(players);
        return players.size();
    }

    private static int clearTargets(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return clear(EntityArgumentType.getPlayers(context, "targets"));
    }
}
