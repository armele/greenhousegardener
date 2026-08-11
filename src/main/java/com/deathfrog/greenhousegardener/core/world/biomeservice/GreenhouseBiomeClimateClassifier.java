package com.deathfrog.greenhousegardener.core.world.biomeservice;

import com.minecolonies.api.items.ModTags;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.HumiditySetting;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.TemperatureSetting;

/**
 * Classifies natural and current greenhouse reference biomes into the temperature and humidity axes used by the
 * greenhouse UI, conversion costs, and maintenance logic.
 *
 * <p>MineColonies crop-climate tags are authoritative when exactly one tag on an axis matches. Intrinsic biome
 * temperature or downfall is used when neither tag matches, and also resolves ambiguous datapack configurations in
 * which both opposing tags match. Current Greenhouse Gardener reference biomes use their exact declared mapping so
 * their meaning remains stable even if a modpack changes their tags.</p>
 *
 * <p>Legacy vanilla overlay aliases are deliberately not recognized by this classifier. This prevents naturally
 * generated vanilla biomes such as plains, savanna, and sparse jungle from being mistaken for old greenhouse
 * overlays. Legacy recognition is restricted to migration code that can also verify persisted overlay ownership.</p>
 */
public final class GreenhouseBiomeClimateClassifier
{
    /** Utility class; biome classification carries no mutable state. */
    private GreenhouseBiomeClimateClassifier()
    {
    }

    /**
     * Resolve a biome to the greenhouse's two climate axes.
     *
     * <p>Classification precedence is: exact current reference-biome mapping, unambiguous MineColonies tags, then
     * numeric climate values. Temperature tags are Cold and Temperate; MineColonies has no Hot crop tag, so Hot is
     * obtained through the numeric fallback. Humidity tags are Dry and Humid, with Normal representing neither.</p>
     *
     * @param biomeId registry identifier, or {@code null} when the holder has no resolvable key
     * @param biome biome holder whose value and rebound tags should be inspected
     * @return resolved greenhouse temperature and humidity settings
     */
    @SuppressWarnings("null")
    public static GreenhouseClimate classify(final ResourceLocation biomeId, final Holder<Biome> biome)
    {
        if (biomeId != null)
        {
            final var referenceClimate = GreenhouseBiomeOverlayService.climateFor(biomeId);
            if (referenceClimate.isPresent())
            {
                return referenceClimate.get();
            }
        }

        final Biome.ClimateSettings settings = biome.value().getModifiedClimateSettings();
        final boolean cold = biome.is(ModTags.coldBiomes);
        final boolean temperate = biome.is(ModTags.temperateBiomes);
        final boolean dry = biome.is(ModTags.dryBiomes);
        final boolean humid = biome.is(ModTags.humidBiomes);

        final TemperatureSetting temperature = cold != temperate
            ? (cold ? TemperatureSetting.COLD : TemperatureSetting.TEMPERATE)
            : numericTemperature(settings.temperature());
        final HumiditySetting humidity = dry != humid
            ? (dry ? HumiditySetting.DRY : HumiditySetting.HUMID)
            : numericHumidity(settings.downfall());
        return new GreenhouseClimate(temperature, humidity);
    }

    /**
     * Classify an intrinsic biome temperature using the greenhouse thresholds.
     *
     * @param temperature biome temperature value
     * @return Cold at or below {@code 0.3}, Hot at or above {@code 0.9}, otherwise Temperate
     */
    static TemperatureSetting numericTemperature(final float temperature)
    {
        return temperature <= 0.3F
            ? TemperatureSetting.COLD
            : temperature >= 0.9F ? TemperatureSetting.HOT : TemperatureSetting.TEMPERATE;
    }

    /**
     * Classify intrinsic biome downfall using the greenhouse thresholds.
     *
     * @param downfall biome downfall value
     * @return Dry at or below {@code 0.3}, Humid at or above {@code 0.8}, otherwise Normal
     */
    static HumiditySetting numericHumidity(final float downfall)
    {
        return downfall <= 0.3F
            ? HumiditySetting.DRY
            : downfall >= 0.8F ? HumiditySetting.HUMID : HumiditySetting.NORMAL;
    }
}
