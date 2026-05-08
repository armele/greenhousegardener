package com.deathfrog.greenhousegardener.core.blocks;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.minecolonies.api.items.ModTags;
import com.minecolonies.core.blocks.MinecoloniesCropBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public class BlockBroccoli extends MinecoloniesCropBlock
{
    public static final String BLOCK_NAME = "broccoli";

    public BlockBroccoli()
    {
        super(BLOCK_NAME, com.minecolonies.api.blocks.ModBlocks.farmland, List.of(Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN), ModTags.coldBiomes);
    }

    @Override
    public ResourceLocation getRegistryName()
    {
        return ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, BLOCK_NAME);
    }
}
