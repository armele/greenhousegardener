package com.deathfrog.greenhousegardener.core.world.biomeservice;

import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.HumiditySetting;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.TemperatureSetting;

/**
 * Shared greenhouse climate axes used for field assignment, overlay biomes, and cost calculation.
 *
 * @param temperature requested or inferred temperature axis
 * @param humidity requested or inferred humidity axis
 */
public record GreenhouseClimate(TemperatureSetting temperature, HumiditySetting humidity)
{
    /**
     * Create climate settings from serialized module names.
     *
     * @param temperature serialized temperature name
     * @param humidity serialized humidity name
     * @return parsed climate settings, defaulting invalid values to temperate/normal
     */
    public static GreenhouseClimate bySerializedNames(final String temperature, final String humidity)
    {
        return new GreenhouseClimate(TemperatureSetting.bySerializedName(temperature), HumiditySetting.bySerializedName(humidity));
    }
}