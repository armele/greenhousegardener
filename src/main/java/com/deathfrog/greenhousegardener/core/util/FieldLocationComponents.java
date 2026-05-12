package com.deathfrog.greenhousegardener.core.util;

import javax.annotation.Nonnull;

import com.deathfrog.greenhousegardener.ModCommands;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Formats field positions for chat messages.
 */
public final class FieldLocationComponents
{
    private FieldLocationComponents()
    {
    }

    /**
     * Build a clickable chat component that highlights a greenhouse field block.
     *
     * @param fieldPosition field anchor position
     * @return clickable location text, or a plain unknown label
     */
    @SuppressWarnings("null")
    public static Component fieldLocation(final @Nonnull BlockPos fieldPosition)
    {

        final MutableComponent location = Component.literal(format(fieldPosition));
        return location.withStyle(style -> style
            .withColor(ChatFormatting.AQUA)
            .withUnderlined(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, ModCommands.highlightFieldCommand(fieldPosition))));
    }

    /**
     * Format a field block position consistently for player-facing text.
     *
     * @param pos position to format
     * @return compact coordinate string
     */
    public static String format(final BlockPos pos)
    {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
