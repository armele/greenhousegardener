package com.deathfrog.greenhousegardener;

import java.util.List;

import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule;
import com.deathfrog.greenhousegardener.core.network.HighlightFieldBlockMessage;
import com.deathfrog.greenhousegardener.core.util.TraceUtils;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
    private static final String CMD_HIGHLIGHT_FIELD = "highlightfield";
    private static final String ARG_TRACE_KEY = "target";
    private static final String ARG_TRACE_ENABLED = "enabled";
    private static final String ARG_X = "x";
    private static final String ARG_Y = "y";
    private static final String ARG_Z = "z";

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
                            .executes(ModCommands::setTrace))))
                .then(Commands.literal(CMD_HIGHLIGHT_FIELD)
                    .requires(source -> source.getEntity() instanceof ServerPlayer)
                    .then(Commands.argument(ARG_X, IntegerArgumentType.integer())
                        .then(Commands.argument(ARG_Y, IntegerArgumentType.integer())
                            .then(Commands.argument(ARG_Z, IntegerArgumentType.integer())
                                .executes(ModCommands::highlightField))))));
    }

    /**
     * Build the chat-click command for highlighting a field.
     *
     * @param fieldPosition field block position
     * @return executable command string
     */
    public static String highlightFieldCommand(final BlockPos fieldPosition)
    {
        return "/" + CMD_ROOT + " " + CMD_HIGHLIGHT_FIELD + " "
            + fieldPosition.getX() + " "
            + fieldPosition.getY() + " "
            + fieldPosition.getZ();
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

    /**
     * Responds to a command to highlight a field by placing a highlight box around the block in question,
     * and giving directive guidance.
     * 
     * @param context
     * @return
     */
    @SuppressWarnings("null")
    private static int highlightField(final CommandContext<CommandSourceStack> context)
    {
        final Entity entity = context.getSource().getEntity();
        if (!(entity instanceof ServerPlayer player))
        {
            return 0;
        }

        final BlockPos fieldPosition = new BlockPos(
            IntegerArgumentType.getInteger(context, ARG_X),
            IntegerArgumentType.getInteger(context, ARG_Y),
            IntegerArgumentType.getInteger(context, ARG_Z));

        if (!canHighlightField(player, fieldPosition))
        {
            return 0;
        }

        new HighlightFieldBlockMessage(fieldPosition).sendToPlayer(player);
        player.displayClientMessage(directionMessage(player, fieldPosition), true);
        return Command.SINGLE_SUCCESS;
    }

    private static boolean canHighlightField(final ServerPlayer player, final BlockPos fieldPosition)
    {
        for (final IColony colony : IColonyManager.getInstance().getColonies(player.level()))
        {
            if (!colony.getPermissions().hasPermission(player, Action.RECEIVE_MESSAGES))
            {
                continue;
            }

            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
            {
                final GreenhouseBiomeModule module = building.getModule(GreenhouseBiomeModule.class, candidate -> true);
                if (module != null && module.isOwned(fieldPosition))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private static Component directionMessage(final ServerPlayer player, final BlockPos fieldPosition)
    {
        final double dx = fieldPosition.getX() + 0.5D - player.getX();
        final double dy = fieldPosition.getY() + 0.5D - player.getY();
        final double dz = fieldPosition.getZ() + 0.5D - player.getZ();
        final int horizontalDistance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        final String direction = horizontalDirection(dx, dz);
        final String vertical = verticalDirection(dy);

        if (horizontalDistance <= 1 && vertical.isBlank())
        {
            return Component.literal("Highlighted greenhouse field at your position.");
        }

        return Component.literal("Highlighted greenhouse field " + direction + ", "
            + horizontalDistance + " blocks away" + vertical + ".");
    }

    private static String horizontalDirection(final double dx, final double dz)
    {
        final double absX = Math.abs(dx);
        final double absZ = Math.abs(dz);
        if (absX < 1.0D && absZ < 1.0D)
        {
            return "nearby";
        }

        final String eastWest = dx >= 0 ? "east" : "west";
        final String northSouth = dz >= 0 ? "south" : "north";
        if (absX < absZ * 0.5D)
        {
            return northSouth;
        }
        if (absZ < absX * 0.5D)
        {
            return eastWest;
        }

        return northSouth + "-" + eastWest;
    }

    private static String verticalDirection(final double dy)
    {
        if (dy > 2.0D)
        {
            return ", above you";
        }
        if (dy < -2.0D)
        {
            return ", below you";
        }

        return "";
    }
}
