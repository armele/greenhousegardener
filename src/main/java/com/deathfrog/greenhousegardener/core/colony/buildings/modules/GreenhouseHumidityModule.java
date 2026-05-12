package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import javax.annotation.Nonnull;

/**
 * Building module that stores selected items for humidifying and drying greenhouse humidity.
 */
public class GreenhouseHumidityModule extends GreenhouseClimateItemModule
{
    @Override
    public @Nonnull ClimateModificationType getModificationType(final ClimateItemList list)
    {
        return list == ClimateItemList.INCREASE ? ClimateModificationType.HUMID : ClimateModificationType.DRY;
    }
}
