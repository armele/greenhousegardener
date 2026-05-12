package com.deathfrog.greenhousegardener.api.colony.buildings;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.core.colony.buildings.ModBuildings;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.AbstractBuilding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public class BuildingGreenhouse extends AbstractBuilding
{
    public BuildingGreenhouse(@NotNull IColony colony, BlockPos pos)
    {
        super(colony, pos);
    }

    @Override
    public String getSchematicName()
    {
    
        return ModBuildings.GREENHOUSE_ID;
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
