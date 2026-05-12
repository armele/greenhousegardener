package com.deathfrog.greenhousegardener.core.client;

import java.time.Duration;

import com.minecolonies.core.client.render.worldevent.HighlightManager;
import com.minecolonies.core.client.render.worldevent.highlightmanager.TimedBoxRenderData;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side field highlight rendering hooks.
 */
@OnlyIn(Dist.CLIENT)
public final class GreenhouseFieldHighlighter
{
    private static final String HIGHLIGHT_KEY = "greenhousegardenerFieldLocation";
    private static final Duration HIGHLIGHT_DURATION = Duration.ofSeconds(10);
    private static final int FIELD_HIGHLIGHT_COLOR = 0xff55ff99;

    private GreenhouseFieldHighlighter()
    {
    }

    /**
     * Highlight one greenhouse field block in the world.
     *
     * @param fieldPos field anchor position
     */
    public static void highlight(final BlockPos fieldPos)
    {
        if (fieldPos == null)
        {
            return;
        }

        HighlightManager.clearHighlightsForKey(HIGHLIGHT_KEY);
        HighlightManager.addHighlight(
            HIGHLIGHT_KEY,
            fieldPos.toShortString(),
            new TimedBoxRenderData(fieldPos)
                .setDuration(HIGHLIGHT_DURATION)
                .setColor(FIELD_HIGHLIGHT_COLOR)
                .addText("Greenhouse field"));
    }
}
