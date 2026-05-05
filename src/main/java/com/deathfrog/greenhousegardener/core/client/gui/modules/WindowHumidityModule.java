package com.deathfrog.greenhousegardener.core.client.gui.modules;

import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseHumidityModuleView;
import com.deathfrog.greenhousegardener.core.ModTags;
import com.deathfrog.greenhousegardener.core.network.SetGreenhouseClimateItemMessage.ClimateModuleType;

import net.minecraft.network.chat.Component;

/**
 * Window for greenhouse humidifying and drying item lists.
 */
public class WindowHumidityModule extends WindowClimateItemModule<GreenhouseHumidityModuleView>
{
    /**
     * Create the humidity controls window.
     *
     * @param moduleView humidity module view backing the window
     */
    public WindowHumidityModule(final GreenhouseHumidityModuleView moduleView)
    {
        super(moduleView, ClimateModuleType.HUMIDITY, ModTags.ITEMS.GREENHOUSE_HUMIDITY_INCREASE, ModTags.ITEMS.GREENHOUSE_HUMIDITY_DECREASE);
        setTitle(Component.translatable("com.greenhousegardener.core.gui.modules.humidity_controls"));
        setIncreaseTitle(Component.translatable("com.greenhousegardener.core.gui.climate.humidity.humid"));
        setDecreaseTitle(Component.translatable("com.greenhousegardener.core.gui.climate.humidity.drier"));
    }
}
