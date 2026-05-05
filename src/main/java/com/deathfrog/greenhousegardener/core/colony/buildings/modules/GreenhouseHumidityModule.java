package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import javax.annotation.Nonnull;

import com.deathfrog.greenhousegardener.core.ModTags;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Building module that stores selected items for humidifying and drying greenhouse humidity.
 */
public class GreenhouseHumidityModule extends GreenhouseClimateItemModule
{
    /**
     * Get the tag used to filter humidifying or drying control items.
     *
     * @param list the humidifying or drying item list
     * @return the item tag for the requested humidity list
     */
    @Override
    public @Nonnull TagKey<Item> getAllowedTag(final ClimateItemList list)
    {
        return list == ClimateItemList.INCREASE ? ModTags.ITEMS.GREENHOUSE_HUMIDITY_INCREASE : ModTags.ITEMS.GREENHOUSE_HUMIDITY_DECREASE;
    }
}
