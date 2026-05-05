package com.deathfrog.greenhousegardener.apiimp.initializer;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.api.tileentities.GreenhouseTileEntities;
import com.deathfrog.greenhousegardener.api.tileentities.GreenhouseTileEntityColonyBuilding;
import com.deathfrog.greenhousegardener.core.colony.buildings.ModBuildings;
import com.mojang.datafixers.DSL;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("null")
public final class TileEntityInitializer
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, GreenhouseGardenerMod.MODID);

    static
    {
        GreenhouseTileEntities.BUILDING = BLOCK_ENTITIES.register(
          "greenhouse_colonybuilding",
          () -> BlockEntityType.Builder.of(GreenhouseTileEntityColonyBuilding::new, ModBuildings.getHuts()).build(DSL.remainderType()));
    }

    private TileEntityInitializer()
    {
        throw new IllegalStateException("Tried to initialize TileEntityInitializer but this is a utility class.");
    }
}
