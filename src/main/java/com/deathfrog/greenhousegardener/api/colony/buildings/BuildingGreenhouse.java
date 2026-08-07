package com.deathfrog.greenhousegardener.api.colony.buildings;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.core.colony.buildings.ModBuildings;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.BuildingModules;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.util.constant.NbtTagConstants;
import com.minecolonies.core.colony.buildings.AbstractBuilding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

public class BuildingGreenhouse extends AbstractBuilding
{
    private static final String LEGACY_BIOME_MODULE = "biome_settings";
    private static final String LEGACY_TEMPERATURE_MODULE = "temperature_controls";
    private static final String LEGACY_HUMIDITY_MODULE = "humidity_controls";
    private static final String LEGACY_HORTICULTURIST_MODULE = "horticulturist_work";

    public BuildingGreenhouse(@NotNull IColony colony, BlockPos pos)
    {
        super(colony, pos);
    }

    @Override
    public String getSchematicName()
    {
        return ModBuildings.GREENHOUSE_ID;
    }

    @SuppressWarnings("null")
    @Override
    public void deserializeNBT(final HolderLookup.Provider provider, final CompoundTag compound)
    {
        final CompoundTag migrated = compound.copy();

        if (migrated.contains(NbtTagConstants.TAG_BUILDING_MODULES, Tag.TAG_COMPOUND))
        {
            final CompoundTag modules = migrated.getCompound(NbtTagConstants.TAG_BUILDING_MODULES);
            migrateModuleKey(modules, LEGACY_BIOME_MODULE, BuildingModules.BIOME_MODULE.key);
            migrateModuleKey(modules, LEGACY_TEMPERATURE_MODULE, BuildingModules.TEMPERATURE_MODULE.key);
            migrateModuleKey(modules, LEGACY_HUMIDITY_MODULE, BuildingModules.HUMIDITY_MODULE.key);
            migrateModuleKey(modules, LEGACY_HORTICULTURIST_MODULE, BuildingModules.HORTICULTURIST_WORK.key);
        }

        super.deserializeNBT(provider, migrated);
    }

    @SuppressWarnings("null")
    private static void migrateModuleKey(final CompoundTag modules, final @Nonnull String legacyKey, final @Nonnull String currentKey)
    {
        if (!modules.contains(currentKey) && modules.contains(legacyKey, Tag.TAG_COMPOUND))
        {
            modules.put(currentKey, modules.get(legacyKey).copy());
        }
    }

    @Override
    public void pickUp(final Player player)
    {
        final GreenhouseBiomeModule biomeModule = getModule(GreenhouseBiomeModule.class, ignored -> true);
        if (biomeModule != null)
        {
            biomeModule.restoreOwnedFieldBiomesBeforePickup();
        }

        super.pickUp(player);
    }
}
