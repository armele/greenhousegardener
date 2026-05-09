package com.deathfrog.greenhousegardener.core.world.biomeservice;

/**
 * Summary of biome overlay or restoration work.
 *
 * @param changedCells number of quart biome cells changed
 * @param touchedChunks number of loaded chunks touched
 * @param hadUnloadedChunks true when at least one chunk in the target range was skipped because it was not loaded
 */
public record OverlayResult(int changedCells, int touchedChunks, boolean hadUnloadedChunks)
{
    public static final OverlayResult EMPTY = new OverlayResult(0, 0, false);
}