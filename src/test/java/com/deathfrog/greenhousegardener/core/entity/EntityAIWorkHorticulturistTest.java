package com.deathfrog.greenhousegardener.core.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EntityAIWorkHorticulturistTest
{
    @Test
    void conversionScanSkipsFieldsAlreadyConvertedToday()
    {
        assertFalse(HorticulturistConversionPolicy.mayAttemptConversionToday(true, true, false));
        assertFalse(HorticulturistConversionPolicy.mayAttemptConversionToday(true, false, false));
        assertFalse(HorticulturistConversionPolicy.mayAttemptConversionToday(true, true, true));
    }

    @Test
    void conversionScanSkipsFieldsAlreadyVisitedUnlessConversionWasBlocked()
    {
        assertFalse(HorticulturistConversionPolicy.mayAttemptConversionToday(false, true, false));
        assertTrue(HorticulturistConversionPolicy.mayAttemptConversionToday(false, true, true));
    }

    @Test
    void conversionScanAllowsUnvisitedFields()
    {
        assertTrue(HorticulturistConversionPolicy.mayAttemptConversionToday(false, false, false));
    }
}
