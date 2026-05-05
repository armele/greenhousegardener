package com.deathfrog.greenhousegardener.api.tileentities;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class GreenhouseTileEntities
{
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<GreenhouseTileEntityColonyBuilding>> BUILDING;

    private GreenhouseTileEntities()
    {
        throw new IllegalStateException("Tried to initialize GreenhouseTileEntities but this is a utility class.");
    }
}
