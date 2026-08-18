package com.deathfrog.greenhousegardener.core.world.biomeservice;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Exact greenhouse field footprint before biome lookup padding is applied.
 *
 * @param exactRegion block-space field footprint
 */
public record FieldBiomeFootprint(BoundingBox exactRegion)
{
    public FieldBiomeFootprint
    {
        if (exactRegion == null)
        {
            throw new IllegalArgumentException("exactRegion must not be null");
        }
    }

    /**
     * Build a footprint from directional field bounds.
     *
     * @param center field anchor position
     * @param westRadius blocks west of center included in the field
     * @param eastRadius blocks east of center included in the field
     * @param northRadius blocks north of center included in the field
     * @param southRadius blocks south of center included in the field
     * @return exact field footprint
     */
    public static FieldBiomeFootprint directional(
        final BlockPos center,
        final int westRadius,
        final int eastRadius,
        final int northRadius,
        final int southRadius)
    {
        return directional(
            center,
            westRadius,
            eastRadius,
            northRadius,
            southRadius,
            GreenhouseBiomeOverlayService.DEFAULT_BELOW_FIELD,
            GreenhouseBiomeOverlayService.DEFAULT_ABOVE_FIELD);
    }

    /**
     * Build a footprint from directional field bounds.
     *
     * @param center field anchor position
     * @param westRadius blocks west of center included in the field
     * @param eastRadius blocks east of center included in the field
     * @param northRadius blocks north of center included in the field
     * @param southRadius blocks south of center included in the field
     * @param verticalRange vertical block radius to include around the field anchor
     * @return exact field footprint
     */
    public static FieldBiomeFootprint directional(
        final BlockPos center,
        final int westRadius,
        final int eastRadius,
        final int northRadius,
        final int southRadius,
        final int verticalRange)
    {
        return directional(center, westRadius, eastRadius, northRadius, southRadius, verticalRange, verticalRange);
    }

    /**
     * Build a footprint with independently controlled vertical bounds.
     *
     * @param center field anchor position
     * @param westRadius blocks west of center included in the field
     * @param eastRadius blocks east of center included in the field
     * @param northRadius blocks north of center included in the field
     * @param southRadius blocks south of center included in the field
     * @param belowField blocks below the field anchor to include before quart quantization
     * @param aboveField blocks above the field anchor to include before quart quantization
     * @return exact field footprint
     */
    public static FieldBiomeFootprint directional(
        final BlockPos center,
        final int westRadius,
        final int eastRadius,
        final int northRadius,
        final int southRadius,
        final int belowField,
        final int aboveField)
    {
        if (center == null)
        {
            throw new IllegalArgumentException("center must not be null");
        }

        final int west = Math.max(0, westRadius);
        final int east = Math.max(0, eastRadius);
        final int north = Math.max(0, northRadius);
        final int south = Math.max(0, southRadius);
        final int below = Math.max(0, belowField);
        final int above = Math.max(0, aboveField);
        return new FieldBiomeFootprint(new BoundingBox(
            center.getX() - west,
            center.getY() - below,
            center.getZ() - north,
            center.getX() + east,
            center.getY() + above,
            center.getZ() + south));
    }

    public static FieldBiomeFootprint centered(final BlockPos center, final int horizontalRange, final int verticalRange)
    {
        final int xzRange = Math.max(0, horizontalRange);
        return directional(center, xzRange, xzRange, xzRange, xzRange, verticalRange);
    }

    public static FieldBiomeFootprint centered(
        final BlockPos center,
        final int horizontalRange,
        final int belowField,
        final int aboveField)
    {
        final int xzRange = Math.max(0, horizontalRange);
        return directional(center, xzRange, xzRange, xzRange, xzRange, belowField, aboveField);
    }

    public BoundingBox paddedBiomeRegion()
    {
        return GreenhouseBiomeOverlayService.paddedBiomeRegion(exactRegion);
    }
}
