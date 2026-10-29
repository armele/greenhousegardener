package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.deathfrog.greenhousegardener.core.colony.buildings.modules.HubValidation.Result;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.RoofProblemKind;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.FieldBiomeAssignment;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.HumiditySetting;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.TemperatureSetting;
import com.deathfrog.greenhousegardener.core.world.biomeservice.GreenhouseClimate;

import net.minecraft.core.BlockPos;

class GreenhouseBiomeModuleTest
{
    @Test
    void unloadedHubValidationIsDeferredRegardlessOfUnknownBlockState()
    {
        assertEquals(Result.DEFERRED_UNLOADED, HubValidation.classify(false, false));
        assertEquals(Result.DEFERRED_UNLOADED, HubValidation.classify(false, true));
    }

    @Test
    void loadedHubValidationDistinguishesValidAndStaleRecords()
    {
        assertEquals(Result.VALID, HubValidation.classify(true, true));
        assertEquals(Result.INVALID, HubValidation.classify(true, false));
    }

    @Test
    void maintenanceResearchAddsWholeDaysToConfiguredWindow()
    {
        assertEquals(6, GreenhouseBiomeModule.effectiveMaintenanceRevertDays(5, 0.0D));
        assertEquals(7, GreenhouseBiomeModule.effectiveMaintenanceRevertDays(5, 1.0D));
        assertEquals(8, GreenhouseBiomeModule.effectiveMaintenanceRevertDays(5, 2.0D));
        assertEquals(9, GreenhouseBiomeModule.effectiveMaintenanceRevertDays(5, 3.0D));
    }

    @Test
    void maintenanceResearchBonusIsFlooredAndClamped()
    {
        assertEquals(6, GreenhouseBiomeModule.effectiveMaintenanceRevertDays(5, -1.0D));
        assertEquals(7, GreenhouseBiomeModule.effectiveMaintenanceRevertDays(5, 1.9D));
    }

    @Test
    void roofProblemsMatchBothFieldAndProblemKind()
    {
        final GreenhouseBiomeModule module = new GreenhouseBiomeModule();
        final BlockPos firstField = new BlockPos(10, 64, 10);
        final BlockPos secondField = new BlockPos(20, 64, 20);

        module.recordRoofProblem(firstField, RoofProblemKind.MAINTENANCE_HOLE, 100L);
        module.recordRoofProblem(secondField, RoofProblemKind.MAINTENANCE_HOLE, 100L);

        assertTrue(module.hasRoofProblem(firstField, RoofProblemKind.MAINTENANCE_HOLE));
        assertFalse(module.hasRoofProblem(firstField, RoofProblemKind.CONVERSION_HOLE));

        module.clearRoofProblem(firstField);

        assertFalse(module.hasRoofProblem(firstField, RoofProblemKind.MAINTENANCE_HOLE));
        assertTrue(module.hasRoofProblem(secondField, RoofProblemKind.MAINTENANCE_HOLE));
    }

    @Test
    void matchingNaturalClimateDoesNotRequireConditioning()
    {
        final FieldBiomeAssignment assignment = new FieldBiomeAssignment(TemperatureSetting.HOT, HumiditySetting.DRY);
        final GreenhouseClimate matchingNaturalClimate = new GreenhouseClimate(TemperatureSetting.HOT, HumiditySetting.DRY);
        final GreenhouseClimate differentNaturalClimate = new GreenhouseClimate(TemperatureSetting.TEMPERATE, HumiditySetting.DRY);

        assertFalse(GreenhouseBiomeModule.requiresClimateConditioning(assignment, matchingNaturalClimate));
        assertTrue(GreenhouseBiomeModule.requiresClimateConditioning(assignment, differentNaturalClimate));
    }
}
