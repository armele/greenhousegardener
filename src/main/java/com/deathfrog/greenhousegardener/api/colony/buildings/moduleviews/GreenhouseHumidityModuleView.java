package com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews;

import org.jetbrains.annotations.Nullable;

import com.deathfrog.greenhousegardener.core.client.gui.modules.WindowHumidityModule;
import com.ldtteam.blockui.views.BOWindow;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-side module view for greenhouse humidity controls.
 */
public class GreenhouseHumidityModuleView extends GreenhouseClimateItemModuleView
{
    /**
     * Get the description shown for the humidity controls tab.
     *
     * @return translated module description
     */
    @Override
    public @Nullable Component getDesc()
    {
        return Component.translatable("com.greenhousegardener.core.gui.modules.humidity_controls");
    }

    /**
     * Create the humidity controls window for this module view.
     *
     * @return humidity controls window
     */
    @Override
    public BOWindow getWindow()
    {
        return new WindowHumidityModule(this);
    }

    /**
     * Get the icon used for the humidity controls tab.
     *
     * @return icon resource location
     */
    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath("minecolonies", "textures/gui/modules/humidity.png");
    }
}
