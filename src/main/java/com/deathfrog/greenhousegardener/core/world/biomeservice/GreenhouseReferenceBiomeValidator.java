package com.deathfrog.greenhousegardener.core.world.biomeservice;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.HumiditySetting;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.TemperatureSetting;
import com.minecolonies.api.items.ModTags;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

/**
 * Validates the datapack-overridable biomes used to represent greenhouse climate overlays.
 *
 * <p>The validator runs after biome tags are rebound so it observes the final registry and tag state contributed
 * by Minecraft, MineColonies, Greenhouse Gardener, and installed datapacks. Validation is diagnostic: invalid
 * definitions are logged as errors without aborting the tag-reload event.</p>
 */
public final class GreenhouseReferenceBiomeValidator
{
    /** Utility class; instances carry no validation state. */
    private GreenhouseReferenceBiomeValidator()
    {
    }

    /**
     * Validate that every greenhouse climate has a registered reference biome with matching MineColonies tags and
     * intrinsic temperature and downfall values.
     *
     * @param registryAccess registry view whose biome entries and rebound tags should be inspected
     */
    @SuppressWarnings("null")
    public static void validate(final RegistryAccess registryAccess)
    {
        final var registry = registryAccess.registryOrThrow(Registries.BIOME);
        for (final TemperatureSetting temperature : TemperatureSetting.values())
        {
            for (final HumiditySetting humidity : HumiditySetting.values())
            {
                final GreenhouseClimate expected = new GreenhouseClimate(temperature, humidity);
                final ResourceLocation biomeId = GreenhouseBiomeOverlayService.biomeFor(expected);
                final var holder = registry.getHolder(ResourceKey.create(Registries.BIOME, biomeId));
                if (holder.isEmpty())
                {
                    GreenhouseGardenerMod.LOGGER.error("Missing greenhouse reference biome {} for {}.", biomeId, expected);
                    continue;
                }
                validateTags(holder.get(), biomeId, expected);
                validateClimateValues(holder.get().value(), biomeId, expected);
            }
        }
    }

    /**
     * Check the MineColonies crop-climate tag membership of one reference biome.
     *
     * <p>Cold and temperate references must belong exclusively to their matching temperature tag, while hot
     * references belong to neither because MineColonies has no hot crop category. Dry and humid references must
     * belong exclusively to their corresponding humidity tag, and normal references must belong to neither.</p>
     *
     * @param biome registered reference-biome holder containing the rebound tag membership
     * @param biomeId registry identifier used in diagnostic messages
     * @param expected greenhouse climate represented by the biome
     */
    @SuppressWarnings("null")
    private static void validateTags(final Holder<Biome> biome, final ResourceLocation biomeId, final GreenhouseClimate expected)
    {
        final boolean cold = biome.is(ModTags.coldBiomes);
        final boolean temperate = biome.is(ModTags.temperateBiomes);
        final boolean dry = biome.is(ModTags.dryBiomes);
        final boolean humid = biome.is(ModTags.humidBiomes);
        final boolean validTemperature = switch (expected.temperature())
        {
            case COLD -> cold && !temperate;
            case TEMPERATE -> temperate && !cold;
            case HOT -> !cold && !temperate;
        };
        final boolean validHumidity = switch (expected.humidity())
        {
            case DRY -> dry && !humid;
            case NORMAL -> !dry && !humid;
            case HUMID -> humid && !dry;
        };
        if (!validTemperature || !validHumidity)
        {
            GreenhouseGardenerMod.LOGGER.error(
                "Greenhouse reference biome {} has incompatible MineColonies tags for {}: cold={}, temperate={}, dry={}, humid={}.",
                biomeId, expected, cold, temperate, dry, humid);
        }
    }

    /**
     * Check that a reference biome's intrinsic temperature and downfall resolve to its declared greenhouse climate.
     *
     * @param biome reference biome definition to inspect
     * @param biomeId registry identifier used in diagnostic messages
     * @param expected greenhouse climate represented by the biome
     */
    private static void validateClimateValues(final Biome biome, final ResourceLocation biomeId, final GreenhouseClimate expected)
    {
        final Biome.ClimateSettings settings = biome.getModifiedClimateSettings();
        if (GreenhouseBiomeClimateClassifier.numericTemperature(settings.temperature()) != expected.temperature()
            || GreenhouseBiomeClimateClassifier.numericHumidity(settings.downfall()) != expected.humidity())
        {
            GreenhouseGardenerMod.LOGGER.error(
                "Greenhouse reference biome {} climate values do not resolve to {}: temperature={}, downfall={}.",
                biomeId, expected, settings.temperature(), settings.downfall());
        }
    }
}
