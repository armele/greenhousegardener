package com.deathfrog.greenhousegardener.core.colony.buildings.jobs;

import com.deathfrog.greenhousegardener.core.entity.EntityAIWorkHorticulturist;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;

public class JobsHorticulturist extends AbstractJob<EntityAIWorkHorticulturist, JobsHorticulturist>
{
    public static final int COUNTER_TRIGGER = 0;
    protected int noGlassCounter = 0;

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
     * Check if the interaction is valid/should be triggered.
     *
     * @return true if the interaction is valid/should be triggered.
     */
    public boolean checkNoGlass()
    {
        return noGlassCounter > COUNTER_TRIGGER;
    }

    /**
     * Tick the menu interaction counter to determine the time when the interaction gets triggered.
     */
    public int tickNoGlass()
    {
        if (noGlassCounter < 100) // to prevent unnecessary high counter when ignored by player
        {
            noGlassCounter++;
        }

        return noGlassCounter;
    }

    /**
     * Reset the interaction counter.
     */
    public void resetNoGlassCounter()
    {
        noGlassCounter = 0;
    }
}
