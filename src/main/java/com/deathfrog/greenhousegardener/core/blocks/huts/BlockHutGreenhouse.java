package com.deathfrog.greenhousegardener.core.blocks.huts;

import com.deathfrog.greenhousegardener.core.colony.buildings.ModBuildings;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;

public class BlockHutGreenhouse extends AbstractBlockHut<BlockHutGreenhouse>
{

    public static final String HUT_NAME = "blockhutgreenhouse";

    @Override
    public String getHutName() 
    {
        return HUT_NAME;
    }

    @Override
    public BuildingEntry getBuildingEntry() 
    {
        return ModBuildings.greenhouse;
    }
    
}
