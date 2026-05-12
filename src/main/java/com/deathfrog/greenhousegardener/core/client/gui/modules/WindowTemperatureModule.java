package com.deathfrog.greenhousegardener.core.client.gui.modules;

import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseTemperatureModuleView;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseClimateItemModule.ClimateModificationType;
import com.deathfrog.greenhousegardener.core.network.SetGreenhouseClimateItemMessage.ClimateModuleType;

import net.minecraft.network.chat.Component;

/**
 * Window for greenhouse heating and cooling item lists.
 */
public class WindowTemperatureModule extends WindowClimateItemModule<GreenhouseTemperatureModuleView>
{
    /**
     * Create the temperature controls window.
     *
     * @param moduleView temperature module view backing the window
     */
    public WindowTemperatureModule(final GreenhouseTemperatureModuleView moduleView)
    {
        super(moduleView, ClimateModuleType.TEMPERATURE, ClimateModificationType.HOT, ClimateModificationType.COLD);
        setTitle(Component.translatable("com.greenhousegardener.core.gui.modules.temperature_controls"));
        setIncreaseTitle(Component.translatable("com.greenhousegardener.core.gui.climate.temperature.hotter"));
        setDecreaseTitle(Component.translatable("com.greenhousegardener.core.gui.climate.temperature.cooler"));
    }
}
