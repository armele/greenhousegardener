package com.deathfrog.greenhousegardener.core.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RancherDamageHandlerTest
{
    @Test
    void primarySkillMultipliesBaseMitigation()
    {
        assertEquals(0.22D, RancherDamageHandler.calculateMitigation(10, 0.0D), 0.000001D);
    }

    @Test
    void researchBonusesAreAlsoScaledBySkill()
    {
        assertEquals(0.385D, RancherDamageHandler.calculateMitigation(10, 0.15D), 0.000001D);
        assertEquals(0.55D, RancherDamageHandler.calculateMitigation(10, 0.30D), 0.000001D);
    }

    @Test
    void mitigationIsCapped()
    {
        assertEquals(0.80D, RancherDamageHandler.calculateMitigation(100, 0.30D), 0.000001D);
    }

    @Test
    void butcheringDamageScalesFromSixWithPrimarySkill()
    {
        assertEquals(6.0D, RancherDamageHandler.calculateButcheringDamage(0), 0.000001D);
        assertEquals(6.6D, RancherDamageHandler.calculateButcheringDamage(10), 0.000001D);
        assertEquals(9.0D, RancherDamageHandler.calculateButcheringDamage(50), 0.000001D);
        assertEquals(12.0D, RancherDamageHandler.calculateButcheringDamage(100), 0.000001D);
    }

    @Test
    void butcheringDamageDoesNotDropBelowBaseForInvalidSkill()
    {
        assertEquals(6.0D, RancherDamageHandler.calculateButcheringDamage(-1), 0.000001D);
    }
}
