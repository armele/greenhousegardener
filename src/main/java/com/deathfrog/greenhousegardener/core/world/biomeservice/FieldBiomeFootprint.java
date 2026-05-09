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
        return directional(center, westRadius, eastRadius, northRadius, southRadius, GreenhouseBiomeOverlayService.DEFAULT_VERTICAL_RANGE);
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
        if (center == null)
        {
            throw new IllegalArgumentException("center must not be null");
        }

        final int west = Math.max(0, westRadius);
        final int east = Math.max(0, eastRadius);
        final int north = Math.max(0, northRadius);
        final int south = Math.max(0, southRadius);
        final int yRange = Math.max(0, verticalRange);
        return new FieldBiomeFootprint(new BoundingBox(
            center.getX() - west,
            center.getY() - yRange,
            center.getZ() - north,
            center.getX() + east,
            center.getY() + yRange,
            center.getZ() + south));
    }

    public static FieldBiomeFootprint centered(final BlockPos center, final int horizontalRange, final int verticalRange)
    {
        final int xzRange = Math.max(0, horizontalRange);
        return directional(center, xzRange, xzRange, xzRange, xzRange, verticalRange);
    }

    public BoundingBox paddedBiomeRegion()
    {
        return GreenhouseBiomeOverlayService.paddedBiomeRegion(exactRegion);
    }
}