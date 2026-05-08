package com.deathfrog.greenhousegardener;

import java.util.List;

import com.deathfrog.greenhousegardener.core.util.TraceUtils;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = GreenhouseGardenerMod.MODID)
public final class ModCommands
{
    public static final String TRACE_HORTICULTURIST = "horticulturist";

    private static final String CMD_ROOT = "mcgg";
    private static final String CMD_TRACE = "trace";
    private static final String ARG_TRACE_KEY = "target";
    private static final String ARG_TRACE_ENABLED = "enabled";

    private ModCommands()
    {
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void registerCommands(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
            Commands.literal(CMD_ROOT)
                .then(Commands.literal(CMD_TRACE)
                    .requires(ModCommands::hasCommandPermission)
                    .then(Commands.argument(ARG_TRACE_KEY, StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(getTraceKeys(), builder))
                        .then(Commands.argument(ARG_TRACE_ENABLED, BoolArgumentType.bool())
                            .executes(ModCommands::setTrace)))));
    }

    public static List<String> getTraceKeys()
    {
        return List.of(TraceUtils.TRACE_NONE, TRACE_HORTICULTURIST);
    }

    private static boolean hasCommandPermission(final CommandSourceStack source)
    {
        if (source.hasPermission(4))
        {
            return true;
        }

        final Entity entity = source.getEntity();
        if (entity instanceof Player player && IMCCommand.isPlayerOped(player))
        {
            return true;
        }

        final GameProfile profile = entity instanceof Player player ? player.getGameProfile() : null;
        return profile != null && source.getServer() != null && source.getServer().isSingleplayerOwner(profile);
    }

    private static int setTrace(final CommandContext<CommandSourceStack> context)
    {
        final String traceKey = StringArgumentType.getString(context, ARG_TRACE_KEY);
        final boolean enabled = BoolArgumentType.getBool(context, ARG_TRACE_ENABLED);

        if (!getTraceKeys().contains(traceKey))
        {
            context.getSource().sendSuccess(() -> Component.literal("Unknown trace target: " + traceKey), false);
            return 0;
        }

        if (TraceUtils.TRACE_NONE.equals(traceKey))
        {
            for (final String key : getTraceKeys())
            {
                if (!TraceUtils.TRACE_NONE.equals(key))
                {
                    TraceUtils.setTrace(key, false);
                }
            }

            context.getSource().sendSuccess(() -> Component.literal("Disabled all Greenhouse Gardener trace targets."), false);
            return Command.SINGLE_SUCCESS;
        }

        TraceUtils.setTrace(traceKey, enabled);
        context.getSource().sendSuccess(() -> Component.literal("Trace " + traceKey + " set to " + enabled + "."), false);
        return Command.SINGLE_SUCCESS;
    }
}
