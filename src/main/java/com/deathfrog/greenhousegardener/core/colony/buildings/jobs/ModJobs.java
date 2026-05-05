package com.deathfrog.greenhousegardener.core.colony.buildings.jobs;

import java.util.ArrayList;
import java.util.List;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.minecolonies.api.colony.jobs.registry.JobEntry;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModJobs
{
    public static final String HORTICULTURIST_TAG = "horticulturist";

    public static final ResourceLocation HORTICULTURIST_ID = ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, HORTICULTURIST_TAG);

    public static DeferredHolder<JobEntry, JobEntry> horticulturist;

    private ModJobs()
    {
        throw new IllegalStateException("Tried to initialize: ModJobs but this is a Utility class.");
    }

    public static List<ResourceLocation> getJobs()
    {
        List<ResourceLocation> jobs = new ArrayList<>() { };
        jobs.add(HORTICULTURIST_ID);

        return jobs;
    }
}
