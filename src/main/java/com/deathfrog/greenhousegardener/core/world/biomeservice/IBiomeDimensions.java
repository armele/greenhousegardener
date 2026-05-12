package com.deathfrog.greenhousegardener.core.world.biomeservice;

/**
 * Describes the values necessary on each axis of a biome configuration.
 */
public interface IBiomeDimensions
{
    public int hot();
    public int cold();
    public int humid();
    public int dry();
}
