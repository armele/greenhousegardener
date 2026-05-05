package com.deathfrog.greenhousegardener.apiimp.initializer;

import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.core.colony.buildings.jobs.JobsHorticulturist;
import com.deathfrog.greenhousegardener.core.colony.buildings.jobs.ModJobs;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.jobs.views.DefaultJobView;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModJobsInitializer
{
    @SuppressWarnings("null")
    public static final DeferredRegister<JobEntry> DEFERRED_REGISTER = DeferredRegister.create(CommonMinecoloniesAPIImpl.JOBS, GreenhouseGardenerMod.MODID);

    private ModJobsInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModJobsInitializer but this is a Utility class.");
    }

    static
    {
        ModJobs.horticulturist = register(DEFERRED_REGISTER, ModJobs.HORTICULTURIST_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobsHorticulturist::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.HORTICULTURIST_ID)
          .createJobEntry());
    }

    /**
     * Register a job at the deferred registry and store the job token in the job list.
     * @param deferredRegister the registry.
     * @param path the path.
     * @param supplier the supplier of the entry.
     * @return the registry object.
     */
    private static DeferredHolder<JobEntry, JobEntry> register(final DeferredRegister<JobEntry> deferredRegister, final String path, final @Nonnull Supplier<JobEntry> supplier)
    {
        if (path == null) return null;

        GreenhouseGardenerMod.LOGGER.info("Registering job: " + path);
        return deferredRegister.register(path, supplier);
    }
}
