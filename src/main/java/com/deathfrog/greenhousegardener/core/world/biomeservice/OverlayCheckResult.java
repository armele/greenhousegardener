package com.deathfrog.greenhousegardener.core.world.biomeservice;

/**
 * Summary of an overlay inspection.
 *
 * @param needsOverlay true when at least one loaded biome cell differs from the target biome
 * @param hadUnloadedChunks true when at least one chunk in the target range could not be inspected
 */
public record OverlayCheckResult(boolean needsOverlay, boolean hadUnloadedChunks)
{
    public static final OverlayCheckResult COMPLETE = new OverlayCheckResult(false, false);
}