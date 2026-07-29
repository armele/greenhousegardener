package com.deathfrog.greenhousegardener.core.blocks.huts;

import com.deathfrog.greenhousegardener.api.tileentities.GreenhouseTileEntities;
import com.deathfrog.greenhousegardener.core.colony.buildings.ModBuildings;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockHutRanch extends AbstractBlockHut<BlockHutRanch>
{
    public static final String HUT_NAME = "blockhutranch";

    @Override
    public String getHutName()
    {
        return HUT_NAME;
    }

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return ModBuildings.ranch;
    }

    @SuppressWarnings("null")
    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull final BlockPos pos, @NotNull final BlockState state)
    {
        final TileEntityColonyBuilding tile = GreenhouseTileEntities.BUILDING.get().create(pos, state);
        if (tile != null)
        {
            tile.registryName = getBuildingEntry().getRegistryName();
        }
        return tile;
    }
}
