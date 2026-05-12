package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import javax.annotation.Nonnull;

/**
 * Building module that stores selected items for heating and cooling greenhouse temperature.
 */
public class GreenhouseTemperatureModule extends GreenhouseClimateItemModule
{
    @Override
    public @Nonnull ClimateModificationType getModificationType(final ClimateItemList list)
    {
        return list == ClimateItemList.INCREASE ? ClimateModificationType.HOT : ClimateModificationType.COLD;
    }
}
