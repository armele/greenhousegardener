package com.deathfrog.greenhousegardener.core.colony.buildings;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import org.jetbrains.annotations.NotNull;

public class ModBuildings
{
    public static final String GREENHOUSE_ID   = "greenhouse";

    public static BuildingEntry greenhouse;

    private ModBuildings()
    {
        throw new IllegalStateException("Tried to initialize: ModBuildings but this is a Utility class.");
    }

    @NotNull
    public static AbstractBlockHut<?>[] getHuts()
    {
        return new AbstractBlockHut[] 
        {
            GreenhouseGardenerMod.blockHutGreenhouse.get(),
        };
    }
}
