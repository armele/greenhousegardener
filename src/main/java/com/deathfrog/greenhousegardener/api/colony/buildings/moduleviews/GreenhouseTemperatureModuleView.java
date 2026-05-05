package com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews;

import org.jetbrains.annotations.Nullable;

import com.deathfrog.greenhousegardener.core.client.gui.modules.WindowTemperatureModule;
import com.ldtteam.blockui.views.BOWindow;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-side module view for greenhouse temperature controls.
 */
public class GreenhouseTemperatureModuleView extends GreenhouseClimateItemModuleView
{
    /**
     * Get the description shown for the temperature controls tab.
     *
     * @return translated module description
     */
    @Override
    public @Nullable Component getDesc()
    {
        return Component.translatable("com.greenhousegardener.core.gui.modules.temperature_controls");
    }

    /**
     * Create the temperature controls window for this module view.
     *
     * @return temperature controls window
     */
    @Override
    public BOWindow getWindow()
    {
        return new WindowTemperatureModule(this);
    }

    /**
     * Get the icon used for the temperature controls tab.
     *
     * @return icon resource location
     */
    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath("minecolonies", "textures/gui/modules/temperature.png");
    }
}
