package com.deathfrog.greenhousegardener.core.world.biomeservice;

/**
 * Conversion costs grouped by the ledgers that pay for each climate direction.
 */
public record BiomeConversionCost(int hot, int cold, int humid, int dry) implements IBiomeDimensions
{
    public static final int DEFAULT_LEDGER_TARGET = 500;
    public static final BiomeConversionCost NONE = new BiomeConversionCost(0, 0, 0, 0);
    public static final BiomeConversionCost DEFAULT_TARGET = new BiomeConversionCost(DEFAULT_LEDGER_TARGET, DEFAULT_LEDGER_TARGET, DEFAULT_LEDGER_TARGET, DEFAULT_LEDGER_TARGET);

    public boolean isNone()
    {
        return hot == 0 && cold == 0 && humid == 0 && dry == 0;
    }
}
