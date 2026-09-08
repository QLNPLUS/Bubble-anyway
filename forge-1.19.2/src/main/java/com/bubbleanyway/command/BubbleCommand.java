package com.bubbleanyway.command;

import com.bubbleanyway.api.BubbleServerApi;
import com.bubbleanyway.data.BubbleSpec;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class BubbleCommand {
    private BubbleCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bubble")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("show")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("config", StringArgumentType.greedyString())
                                        .executes(BubbleCommand::show)))
                        .then(Commands.literal("theme")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("theme", StringArgumentType.word())
                                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                                        .executes(BubbleCommand::showTheme))))))
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
            BubbleSpec spec = BubbleSpec.fromJson(StringArgumentType.getString(context, "config"));
            BubbleServerApi.show(players, spec);
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("Invalid bubble config: " + exception.getMessage()));
            return 0;
        }

        source.sendSuccess(Component.literal("Bubble sent to " + players.size() + " player(s)."), true);
        return players.size();
    }

    private static int showTheme(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        String themeId = StringArgumentType.getString(context, "theme");
        String text = StringArgumentType.getString(context, "text");
        try {
            BubbleServerApi.showTheme(players, themeId, text);
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("Invalid bubble theme: " + exception.getMessage()));
            return 0;
        }
        source.sendSuccess(Component.literal("Bubble theme sent to " + players.size() + " player(s)."), true);
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
