package com.deathfrog.greenhousegardener.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.deathfrog.greenhousegardener.core.world.biomeservice.FieldBiomeFootprint;
import com.deathfrog.greenhousegardener.core.world.biomeservice.GreenhouseBiomeOverlayService;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

class GreenhouseBiomeOverlayServiceTest
{
    @Test
    void paddedBiomeRegionExpandsFieldEdgesByOneQuartCell()
    {
        final BoundingBox exactField = new BoundingBox(10, 64, 20, 18, 64, 28);

        final BoundingBox paddedRegion = GreenhouseBiomeOverlayService.paddedBiomeRegion(exactField);

        assertEquals(new BoundingBox(4, 64, 16, 20, 64, 32), paddedRegion);
    }

    @Test
    void directionalFootprintPreservesAsymmetricFieldBoundsBeforePadding()
    {
        final FieldBiomeFootprint footprint =
            FieldBiomeFootprint.directional(new BlockPos(10, 64, 20), 1, 8, 2, 5);

        assertEquals(new BoundingBox(9, 56, 18, 18, 72, 25), footprint.exactRegion());
        assertEquals(new BoundingBox(4, 56, 12, 20, 72, 28), GreenhouseBiomeOverlayService.paddedBiomeRegion(footprint.exactRegion()));
    }
}
