package com.deathfrog.greenhousegardener.api.colony.buildings;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.core.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.AbstractBuilding;

import net.minecraft.core.BlockPos;

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
}
