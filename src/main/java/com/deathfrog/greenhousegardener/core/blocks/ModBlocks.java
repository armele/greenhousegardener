package com.deathfrog.greenhousegardener.core.blocks;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.core.blocks.huts.BlockHutGreenhouse;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks
{
  public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GreenhouseGardenerMod.MODID);

  public static final DeferredBlock<BlockHutGreenhouse> blockHutGreenhouse =
    BLOCKS.register(BlockHutGreenhouse.HUT_NAME, BlockHutGreenhouse::new);

  public static final DeferredBlock<BlockClimateControlHub> climateControlHub =
    BLOCKS.register(BlockClimateControlHub.BLOCK_NAME, BlockClimateControlHub::new);

  public static final DeferredBlock<BlockCucumber> cucumber = BLOCKS.register(BlockCucumber.BLOCK_NAME, BlockCucumber::new);
  public static final DeferredBlock<BlockSpinach> spinach = BLOCKS.register(BlockSpinach.BLOCK_NAME, BlockSpinach::new);
  public static final DeferredBlock<BlockBroccoli> broccoli = BLOCKS.register(BlockBroccoli.BLOCK_NAME, BlockBroccoli::new);

  private ModBlocks()
  {
    throw new IllegalStateException("Tried to initialize: ModBlocks but this is a Utility class.");
  }

  public static void register(final IEventBus modEventBus)
  {
    if (modEventBus == null) return;

    BLOCKS.register(modEventBus);
  }
}
