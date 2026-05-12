package com.deathfrog.greenhousegardener.apiimp.initializer;

import com.deathfrog.greenhousegardener.api.colony.buildings.BuildingGreenhouse;
import com.deathfrog.greenhousegardener.core.colony.buildings.jobs.JobsHorticulturist;
import com.minecolonies.api.colony.interactionhandling.InteractionValidatorRegistry;

import net.minecraft.network.chat.Component;

public class InteractionInitializer
{
    public static final String GREENHOUSE_NOGLASS_AT                 = "entity.horticulturist.noglass.at";
    public static final String GREENHOUSE_BIOME_MAINTENANCE_NOGLASS  = "entity.horticulturist.biome_maintenance.noglass";
    public static final String GREENHOUSE_ROOF_RATIO                 = "entity.horticulturist.roof.ratio";
    public static final String GREENHOUSE_BIOME_MAINTENANCE_ROOF_RATIO = "entity.horticulturist.biome_maintenance.roof_ratio";
    public static final String GREENHOUSE_BIOME_LEDGER_SHORTAGE      = "entity.horticulturist.biome_ledger.shortage";
    public static final String GREENHOUSE_BIOME_MAINTENANCE_SHORTAGE = "entity.horticulturist.biome_maintenance.shortage";
    public static final String GREENHOUSE_BIOME_LEDGER_UNCONFIGURED  = "entity.horticulturist.biome_ledger.unconfigured";
    public static final String GREENHOUSE_BIOME_CONTENTION           = "entity.horticulturist.biome_contention";

    public static void injectInteractionHandlers() 
    {
        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(GREENHOUSE_NOGLASS_AT),
          citizen -> citizen.getWorkBuilding() instanceof BuildingGreenhouse && citizen.getJob(JobsHorticulturist.class).checkNoGlass());
        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(GREENHOUSE_BIOME_MAINTENANCE_NOGLASS),
          citizen -> citizen.getWorkBuilding() instanceof BuildingGreenhouse && citizen.getJob(JobsHorticulturist.class).checkNoGlass());
        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(GREENHOUSE_ROOF_RATIO),
          citizen -> citizen.getWorkBuilding() instanceof BuildingGreenhouse && citizen.getJob(JobsHorticulturist.class).checkNoGlass());
        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(GREENHOUSE_BIOME_MAINTENANCE_ROOF_RATIO),
          citizen -> citizen.getWorkBuilding() instanceof BuildingGreenhouse && citizen.getJob(JobsHorticulturist.class).checkNoGlass());
        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(GREENHOUSE_BIOME_LEDGER_SHORTAGE),
          citizen -> citizen.getWorkBuilding() instanceof BuildingGreenhouse && citizen.getJob(JobsHorticulturist.class).checkBiomeLedgerShortage());
        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(GREENHOUSE_BIOME_MAINTENANCE_SHORTAGE),
          citizen -> citizen.getWorkBuilding() instanceof BuildingGreenhouse && citizen.getJob(JobsHorticulturist.class).checkBiomeLedgerShortage());
        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(GREENHOUSE_BIOME_LEDGER_UNCONFIGURED),
          citizen -> citizen.getWorkBuilding() instanceof BuildingGreenhouse && citizen.getJob(JobsHorticulturist.class).checkBiomeLedgerShortage());
        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(GREENHOUSE_BIOME_CONTENTION),
          citizen -> citizen.getWorkBuilding() instanceof BuildingGreenhouse && citizen.getJob(JobsHorticulturist.class).checkBiomeContentionWarning());
    }
}
