package com.deathfrog.greenhousegardener.core.colony.buildings.jobs;

import com.deathfrog.greenhousegardener.core.entity.EntityAIWorkHorticulturist;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;

public class JobsHorticulturist extends AbstractJob<EntityAIWorkHorticulturist, JobsHorticulturist>
{
    protected boolean biomeLedgerShortage = false;
    protected boolean biomeContentionWarning = false;

    public JobsHorticulturist(ICitizenData entity)
    {
        super(entity);
    }

    @Override
    public EntityAIWorkHorticulturist generateAI()
    {
        return new EntityAIWorkHorticulturist(this);
    }
    
    /**
     * Check if the biome ledger shortage interaction is still valid.
     *
     * @return true when a greenhouse field still lacks climate material ledger balance
     */
    public boolean checkBiomeLedgerShortage()
    {
        return biomeLedgerShortage;
    }

    /**
     * Update whether a greenhouse field is blocked by insufficient climate material ledger balance.
     *
     * @param shortage true when a shortage is currently blocking field transformation
     */
    public void setBiomeLedgerShortage(final boolean shortage)
    {
        biomeLedgerShortage = shortage;
    }

    /**
     * Check if a biome-contention interaction is still valid.
     *
     * @return true when managed fields currently have conflicting nearby biome conditioning
     */
    public boolean checkBiomeContentionWarning()
    {
        return biomeContentionWarning;
    }

    /**
     * Update whether managed fields currently have conflicting nearby biome conditioning.
     *
     * @param warning true when a field-contention warning should remain visible
     */
    public void setBiomeContentionWarning(final boolean warning)
    {
        biomeContentionWarning = warning;
    }
}
