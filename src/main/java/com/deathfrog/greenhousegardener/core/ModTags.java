package com.deathfrog.greenhousegardener.core;

import javax.annotation.Nonnull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Tag keys used by Greenhouse Gardener data-pack driven behavior.
 */
public final class ModTags
{
    /**
     * Utility class.
     */
    private ModTags()
    {
    }

    /**
     * Block tag keys used by greenhouse structure validation.
     */
    public static final class BLOCKS
    {
        public static final ResourceLocation GREENHOUSE_ROOF_KEY = tag("greenhouse_roof");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Block> GREENHOUSE_ROOF = BlockTags.create(GREENHOUSE_ROOF_KEY);

        /**
         * Build a Greenhouse Gardener block tag resource location.
         *
         * @param path tag path
         * @return namespaced resource location
         */
        private static ResourceLocation tag(final @Nonnull String path)
        {
            return ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, path);
        }
    }
}
