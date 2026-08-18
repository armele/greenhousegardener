package com.deathfrog.greenhousegardener.core.colony.crops;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Distribution-neutral crop-field data shared by server snapshots, packets, and client views. */
public record CropFieldSnapshot(
    BlockPos position,
    ItemStack seed,
    ItemStack product,
    int productCount,
    boolean assigned,
    @Nullable BlockPos farmPosition,
    List<String> workers,
    boolean hasClimateControlHub,
    boolean ownedByThisGreenhouse,
    boolean ownedByAnotherGreenhouse,
    boolean thisGreenhouseHasCapacity,
    ResourceLocation effectiveBiome,
    ResourceLocation naturalBiome)
{
}
