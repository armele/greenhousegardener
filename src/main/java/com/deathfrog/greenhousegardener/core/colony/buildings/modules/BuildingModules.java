package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseBiomeModuleView;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseHumidityModuleView;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseTemperatureModuleView;
import com.deathfrog.greenhousegardener.core.colony.buildings.jobs.ModJobs;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;

public class BuildingModules
{
    public static final BuildingEntry.ModuleProducer<GreenhouseBiomeModule, GreenhouseBiomeModuleView> BIOME_MODULE =
      new BuildingEntry.ModuleProducer<GreenhouseBiomeModule, GreenhouseBiomeModuleView>("biome_settings", GreenhouseBiomeModule::new, () -> GreenhouseBiomeModuleView::new);

    public static final BuildingEntry.ModuleProducer<GreenhouseTemperatureModule, GreenhouseTemperatureModuleView> TEMPERATURE_MODULE =
      new BuildingEntry.ModuleProducer<GreenhouseTemperatureModule, GreenhouseTemperatureModuleView>("temperature_controls", GreenhouseTemperatureModule::new, () -> GreenhouseTemperatureModuleView::new);

    public static final BuildingEntry.ModuleProducer<GreenhouseHumidityModule, GreenhouseHumidityModuleView> HUMIDITY_MODULE =
      new BuildingEntry.ModuleProducer<GreenhouseHumidityModule, GreenhouseHumidityModuleView>("humidity_controls", GreenhouseHumidityModule::new, () -> GreenhouseHumidityModuleView::new);

    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule,WorkerBuildingModuleView> SHOPKEEPER_WORK          =
      new BuildingEntry.ModuleProducer<>("horticulturist_work", 
        () -> new WorkerBuildingModule(ModJobs.horticulturist.get(), Skill.Creativity, Skill.Knowledge, false, (b) -> 1),
        () -> WorkerBuildingModuleView::new);
}
