package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.deathfrog.greenhousegardener.core.colony.buildings.modules.HubValidation.Result;

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
}
