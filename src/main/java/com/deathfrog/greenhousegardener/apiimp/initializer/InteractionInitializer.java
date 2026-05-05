package com.deathfrog.greenhousegardener.apiimp.initializer;

import com.deathfrog.greenhousegardener.api.colony.buildings.BuildingGreenhouse;
import com.deathfrog.greenhousegardener.core.colony.buildings.jobs.JobsHorticulturist;
import com.minecolonies.api.colony.interactionhandling.InteractionValidatorRegistry;

import net.minecraft.network.chat.Component;

public class InteractionInitializer
{
    public static final String GREENHOUSE_NOGLASS_AT                = "entity.horticulturist.noglass.at";

    public static void injectInteractionHandlers() 
    {
        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(GREENHOUSE_NOGLASS_AT),
          citizen -> citizen.getWorkBuilding() instanceof BuildingGreenhouse && citizen.getJob(JobsHorticulturist.class).checkNoGlass());
    }
}
