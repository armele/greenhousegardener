package com.deathfrog.greenhousegardener.core;

import javax.annotation.Nonnull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;

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
     * Item tag keys used by greenhouse climate control modules.
     */
    public static final class ITEMS
    {
        public static final ResourceLocation GREENHOUSE_TEMP_INCREASE_KEY = tag("greenhouse_temp_increase");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Item> GREENHOUSE_TEMP_INCREASE = ItemTags.create(GREENHOUSE_TEMP_INCREASE_KEY);

        public static final ResourceLocation GREENHOUSE_TEMP_INCREASE_LOW_KEY = tag("greenhouse_temp_increase_low");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Item> GREENHOUSE_TEMP_INCREASE_LOW = ItemTags.create(GREENHOUSE_TEMP_INCREASE_LOW_KEY);

        public static final ResourceLocation GREENHOUSE_TEMP_INCREASE_MEDIUM_KEY = tag("greenhouse_temp_increase_medium");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Item> GREENHOUSE_TEMP_INCREASE_MEDIUM = ItemTags.create(GREENHOUSE_TEMP_INCREASE_MEDIUM_KEY);

        public static final ResourceLocation GREENHOUSE_TEMP_INCREASE_HIGH_KEY = tag("greenhouse_temp_increase_high");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Item> GREENHOUSE_TEMP_INCREASE_HIGH = ItemTags.create(GREENHOUSE_TEMP_INCREASE_HIGH_KEY);

        public static final ResourceLocation GREENHOUSE_TEMP_DECREASE_KEY = tag("greenhouse_temp_decrease");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Item> GREENHOUSE_TEMP_DECREASE = ItemTags.create(GREENHOUSE_TEMP_DECREASE_KEY);

        public static final ResourceLocation GREENHOUSE_TEMP_DECREASE_LOW_KEY = tag("greenhouse_temp_decrease_low");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Item> GREENHOUSE_TEMP_DECREASE_LOW = ItemTags.create(GREENHOUSE_TEMP_DECREASE_LOW_KEY);

        public static final ResourceLocation GREENHOUSE_TEMP_DECREASE_MEDIUM_KEY = tag("greenhouse_temp_decrease_medium");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Item> GREENHOUSE_TEMP_DECREASE_MEDIUM = ItemTags.create(GREENHOUSE_TEMP_DECREASE_MEDIUM_KEY);

        public static final ResourceLocation GREENHOUSE_TEMP_DECREASE_HIGH_KEY = tag("greenhouse_temp_decrease_high");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Item> GREENHOUSE_TEMP_DECREASE_HIGH = ItemTags.create(GREENHOUSE_TEMP_DECREASE_HIGH_KEY);

        public static final ResourceLocation GREENHOUSE_HUMIDITY_INCREASE_KEY = tag("greenhouse_humidity_increase");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Item> GREENHOUSE_HUMIDITY_INCREASE = ItemTags.create(GREENHOUSE_HUMIDITY_INCREASE_KEY);

        public static final ResourceLocation GREENHOUSE_HUMIDITY_INCREASE_LOW_KEY = tag("greenhouse_humidity_increase_low");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Item> GREENHOUSE_HUMIDITY_INCREASE_LOW = ItemTags.create(GREENHOUSE_HUMIDITY_INCREASE_LOW_KEY);

        public static final ResourceLocation GREENHOUSE_HUMIDITY_INCREASE_MEDIUM_KEY = tag("greenhouse_humidity_increase_medium");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Item> GREENHOUSE_HUMIDITY_INCREASE_MEDIUM = ItemTags.create(GREENHOUSE_HUMIDITY_INCREASE_MEDIUM_KEY);

        public static final ResourceLocation GREENHOUSE_HUMIDITY_INCREASE_HIGH_KEY = tag("greenhouse_humidity_increase_high");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Item> GREENHOUSE_HUMIDITY_INCREASE_HIGH = ItemTags.create(GREENHOUSE_HUMIDITY_INCREASE_HIGH_KEY);

        public static final ResourceLocation GREENHOUSE_HUMIDITY_DECREASE_KEY = tag("greenhouse_humidity_decrease");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Item> GREENHOUSE_HUMIDITY_DECREASE = ItemTags.create(GREENHOUSE_HUMIDITY_DECREASE_KEY);

        public static final ResourceLocation GREENHOUSE_HUMIDITY_DECREASE_LOW_KEY = tag("greenhouse_humidity_decrease_low");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Item> GREENHOUSE_HUMIDITY_DECREASE_LOW = ItemTags.create(GREENHOUSE_HUMIDITY_DECREASE_LOW_KEY);

        public static final ResourceLocation GREENHOUSE_HUMIDITY_DECREASE_MEDIUM_KEY = tag("greenhouse_humidity_decrease_medium");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Item> GREENHOUSE_HUMIDITY_DECREASE_MEDIUM = ItemTags.create(GREENHOUSE_HUMIDITY_DECREASE_MEDIUM_KEY);

        public static final ResourceLocation GREENHOUSE_HUMIDITY_DECREASE_HIGH_KEY = tag("greenhouse_humidity_decrease_high");
        @SuppressWarnings("null")
        public @Nonnull static final TagKey<Item> GREENHOUSE_HUMIDITY_DECREASE_HIGH = ItemTags.create(GREENHOUSE_HUMIDITY_DECREASE_HIGH_KEY);

        /**
         * Build a Greenhouse Gardener item tag resource location.
         *
         * @param path tag path
         * @return namespaced resource location
         */
        private static ResourceLocation tag(final @Nonnull String path)
        {
            return ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, path);
        }
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
