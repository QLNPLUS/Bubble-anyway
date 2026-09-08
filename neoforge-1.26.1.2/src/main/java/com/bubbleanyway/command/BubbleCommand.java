package com.bubbleanyway.command;

import com.bubbleanyway.api.BubbleServerApi;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class BubbleCommand {
    private BubbleCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bubble")
                .requires(source -> source.permissions().hasPermission(
                        net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("show")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("config", StringArgumentType.greedyString())
                                        .executes(BubbleCommand::show))))
                .then(Commands.literal("clear")
                        .executes(context -> clear(context.getSource().getServer().getPlayerList().getPlayers()))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(BubbleCommand::clearTargets))));
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<ServerPlayer> players;
        try {
            players = EntityArgument.getPlayers(context, "targets");
            BubbleServerApi.showJson(players, StringArgumentType.getString(context, "config"));
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("Invalid bubble config: " + exception.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bubble sent to " + players.size() + " player(s)."), true);
        return players.size();
    }

    private static int clear(Collection<ServerPlayer> players) {
        BubbleServerApi.clear(players);
        return players.size();
    }

    private static int clearTargets(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return clear(EntityArgument.getPlayers(context, "targets"));
    }
}
