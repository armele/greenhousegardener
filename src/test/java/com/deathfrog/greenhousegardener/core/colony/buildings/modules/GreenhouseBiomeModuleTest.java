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
}
