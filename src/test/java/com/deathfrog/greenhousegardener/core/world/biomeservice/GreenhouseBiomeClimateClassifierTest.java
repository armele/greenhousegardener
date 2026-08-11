package com.deathfrog.greenhousegardener.core.world.biomeservice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.HumiditySetting;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.TemperatureSetting;

class GreenhouseBiomeClimateClassifierTest
{
    @Test
    void numericFallbackUsesStableInteriorThresholds()
    {
        assertEquals(TemperatureSetting.COLD, GreenhouseBiomeClimateClassifier.numericTemperature(0.2F));
        assertEquals(TemperatureSetting.TEMPERATE, GreenhouseBiomeClimateClassifier.numericTemperature(0.7F));
        assertEquals(TemperatureSetting.HOT, GreenhouseBiomeClimateClassifier.numericTemperature(1.0F));
        assertEquals(HumiditySetting.DRY, GreenhouseBiomeClimateClassifier.numericHumidity(0.1F));
        assertEquals(HumiditySetting.NORMAL, GreenhouseBiomeClimateClassifier.numericHumidity(0.5F));
        assertEquals(HumiditySetting.HUMID, GreenhouseBiomeClimateClassifier.numericHumidity(0.9F));
    }
}
