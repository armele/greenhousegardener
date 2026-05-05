package com.deathfrog.greenhousegardener.api.tileentities;

import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class GreenhouseTileEntityColonyBuilding extends TileEntityColonyBuilding
{
    public GreenhouseTileEntityColonyBuilding(
      final BlockEntityType<? extends AbstractTileEntityColonyBuilding> type,
      final BlockPos pos,
      final BlockState state)
    {
        super(type, pos, state);
    }

    public GreenhouseTileEntityColonyBuilding(final BlockPos pos, final BlockState state)
    {
        this(GreenhouseTileEntities.BUILDING.get(), pos, state);
    }
}
