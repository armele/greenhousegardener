package com.deathfrog.greenhousegardener.core.world.biomeservice;

/**
 * Summary of an overlay inspection.
 *
 * @param loadedCells biome cells inspected in currently loaded chunks
 * @param matchingCells inspected cells already matching the target biome
 * @param mismatchedCells inspected cells that need overlay work
 * @param hadUnloadedChunks true when at least one chunk in the target range could not be inspected
 */
public record OverlayCheckResult(int loadedCells, int matchingCells, int mismatchedCells, boolean hadUnloadedChunks)
{
    public static final OverlayCheckResult COMPLETE = new OverlayCheckResult(0, 0, 0, false);

    public boolean needsOverlay()
    {
        return mismatchedCells > 0;
    }

    public double mismatchRatio()
    {
        return loadedCells <= 0 ? 0.0D : (double) mismatchedCells / loadedCells;
    }
}
