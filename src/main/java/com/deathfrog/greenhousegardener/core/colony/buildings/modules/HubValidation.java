package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

final class HubValidation
{
    enum Result
    {
        VALID,
        INVALID,
        DEFERRED_UNLOADED
    }

    private HubValidation()
    {
    }

    static Result classify(final boolean chunkLoaded, final boolean hubPresent)
    {
        if (!chunkLoaded)
        {
            return Result.DEFERRED_UNLOADED;
        }
        return hubPresent ? Result.VALID : Result.INVALID;
    }
}
