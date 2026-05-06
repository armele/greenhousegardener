package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import javax.annotation.Nonnull;

import com.deathfrog.greenhousegardener.core.ModTags;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Building module that stores selected items for heating and cooling greenhouse temperature.
 */
public class GreenhouseTemperatureModule extends GreenhouseClimateItemModule
{
    /**
     * Get the tag used to filter hotter or cooler temperature control items.
     *
     * @param list the hotter or cooler item list
     * @return the item tag for the requested temperature list
     */
    @Override
    public @Nonnull TagKey<Item> getAllowedTag(final ClimateItemList list)
    {
        return list == ClimateItemList.INCREASE ? ModTags.ITEMS.GREENHOUSE_TEMP_INCREASE : ModTags.ITEMS.GREENHOUSE_TEMP_DECREASE;
    }

    @Override
    public @Nonnull ClimateModificationType getModificationType(final ClimateItemList list)
    {
        return list == ClimateItemList.INCREASE ? ClimateModificationType.HOT : ClimateModificationType.COLD;
    }
}
