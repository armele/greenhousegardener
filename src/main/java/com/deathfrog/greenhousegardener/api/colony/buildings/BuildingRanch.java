package com.deathfrog.greenhousegardener.api.colony.buildings;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.Config;
import com.deathfrog.greenhousegardener.ModResearch;
import com.deathfrog.greenhousegardener.core.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

/**
 * MineColonies building that houses the tag-driven Rancher job.
 */
public class BuildingRanch extends AbstractBuilding
{
    /**
     * Number of animals added to each exact species' capacity per hut level.
     */
    public static final int HERD_CAPACITY_PER_LEVEL = 2;

    /** Controls passive feeding of managed animals. */
    public static final ISettingKey<BoolSetting> FEEDING = setting("feeding");
    /** Controls butchering of eligible, unprotected managed animals. */
    public static final ISettingKey<BoolSetting> BUTCHERING = setting("butchering");
    /** Controls collection from animals in the shearable capability tag. */
    public static final ISettingKey<BoolSetting> SHEARING = setting("shearing");
    /** Controls collection from animals in either milkable capability tag. */
    public static final ISettingKey<BoolSetting> MILKING = setting("milking");

    /**
     * Creates a Ranch building.
     *
     * @param colony owning colony
     * @param pos hut block position
     */
    public BuildingRanch(@NotNull final IColony colony, @NotNull final BlockPos pos)
    {
        super(colony, pos);
    }

    /**
     * Maximum number of animals maintained for each exact entity type.
     *
     * @return herd capacity at the current building level
     */
    public int getHerdCapacity()
    {
        return getBuildingLevel() * HERD_CAPACITY_PER_LEVEL;
    }

    /**
     * Maximum number of exact managed entity types supported by this Ranch.
     *
     * @return configured base type count plus the optional research bonus
     */
    public int getSupportedHerdTypeCapacity()
    {
        final int researchBonus = (int) Math.floor(getColony().getResearchManager()
            .getResearchEffects().getEffectStrength(ModResearch.RESEARCH_ADDITIONAL_RANCH_HERD));
        return Config.supportedRanchAnimalTypes.get() + Math.max(0, researchBonus);
    }

    /**
     * Returns the schematic identifier used by MineColonies.
     *
     * @return Ranch schematic name
     */
    @Override
    public String getSchematicName()
    {
        return ModBuildings.RANCH_ID;
    }

    /**
     * Creates a namespaced Ranch boolean-setting key.
     *
     * @param path setting identifier path
     * @return typed setting key
     */
    private static ISettingKey<BoolSetting> setting(final @Nonnull String path)
    {
        return new SettingKey<>(BoolSetting.class,
            ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, path));
    }
}
