package com.deathfrog.greenhousegardener.core.blocks.huts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.greenhousegardener.api.tileentities.GreenhouseTileEntities;
import com.deathfrog.greenhousegardener.core.colony.buildings.ModBuildings;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull final BlockPos blockPos, @NotNull final BlockState blockState)
    {

        if (blockPos == null || blockState == null) return null;

        final TileEntityColonyBuilding building = GreenhouseTileEntities.BUILDING.get().create(blockPos, blockState);

        if (building != null)
        {
            building.registryName = getBuildingEntry().getRegistryName();
        }

        return building;
    }
    
}
