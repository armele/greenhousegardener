package com.deathfrog.greenhousegardener.core.world.biomeservice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class GreenhouseBiomeOverlayServiceTest
{
    @Test
    void overlayWriterSkipsCellsAlreadyMatchingTargetBiome()
    {
        final ResourceLocation targetBiome = ResourceLocation.fromNamespaceAndPath("minecraft", "plains");

        assertFalse(GreenhouseBiomeOverlayService.shouldWriteOverlayCell(targetBiome, targetBiome));
    }

    @Test
    void overlayWriterWritesCellsWithDifferentBiome()
    {
        final ResourceLocation targetBiome = ResourceLocation.fromNamespaceAndPath("minecraft", "plains");
        final ResourceLocation currentBiome = ResourceLocation.fromNamespaceAndPath("minecraft", "desert");

        assertTrue(GreenhouseBiomeOverlayService.shouldWriteOverlayCell(currentBiome, targetBiome));
    }

    @Test
    void overlayWriterWritesCellsWhenCurrentBiomeCannotBeResolved()
    {
        final ResourceLocation targetBiome = ResourceLocation.fromNamespaceAndPath("minecraft", "plains");

        assertTrue(GreenhouseBiomeOverlayService.shouldWriteOverlayCell(null, targetBiome));
    }

    @Test
    void overlayWriterSkipsCellsWhenTargetBiomeCannotBeResolved()
    {
        final ResourceLocation currentBiome = ResourceLocation.fromNamespaceAndPath("minecraft", "plains");

        assertFalse(GreenhouseBiomeOverlayService.shouldWriteOverlayCell(currentBiome, null));
    }
}
