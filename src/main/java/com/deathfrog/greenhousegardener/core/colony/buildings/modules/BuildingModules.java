package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseBiomeModuleView;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.ColonyCropsModuleView;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseHumidityModuleView;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseTemperatureModuleView;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.RanchHerdListModuleView;
import com.deathfrog.greenhousegardener.core.colony.buildings.jobs.ModJobs;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.deathfrog.greenhousegardener.api.colony.buildings.BuildingRanch;

public class BuildingModules
{
    public static final BuildingEntry.ModuleProducer<ColonyCropsModule, ColonyCropsModuleView> COLONY_CROPS_MODULE =
      new BuildingEntry.ModuleProducer<>("gg:colony_crops", ColonyCropsModule::new, () -> ColonyCropsModuleView::new);

    public static final BuildingEntry.ModuleProducer<GreenhouseBiomeModule, GreenhouseBiomeModuleView> BIOME_MODULE =
      new BuildingEntry.ModuleProducer<GreenhouseBiomeModule, GreenhouseBiomeModuleView>("gg:biome_settings", GreenhouseBiomeModule::new, () -> GreenhouseBiomeModuleView::new);

    public static final BuildingEntry.ModuleProducer<GreenhouseTemperatureModule, GreenhouseTemperatureModuleView> TEMPERATURE_MODULE =
      new BuildingEntry.ModuleProducer<GreenhouseTemperatureModule, GreenhouseTemperatureModuleView>("gg:temperature_controls", GreenhouseTemperatureModule::new, () -> GreenhouseTemperatureModuleView::new);

    public static final BuildingEntry.ModuleProducer<GreenhouseHumidityModule, GreenhouseHumidityModuleView> HUMIDITY_MODULE =
      new BuildingEntry.ModuleProducer<GreenhouseHumidityModule, GreenhouseHumidityModuleView>("gg:humidity_controls", GreenhouseHumidityModule::new, () -> GreenhouseHumidityModuleView::new);

    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule,WorkerBuildingModuleView> HORTICULTURIST_WORK          =
      new BuildingEntry.ModuleProducer<>("gg:horticulturist_work", 
        () -> new WorkerBuildingModule(ModJobs.horticulturist.get(), Skill.Creativity, Skill.Knowledge, false, (b) -> 1),
        () -> WorkerBuildingModuleView::new);

    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule, WorkerBuildingModuleView> RANCHER_WORK =
      new BuildingEntry.ModuleProducer<>("gg:rancher_work",
        () -> new WorkerBuildingModule(ModJobs.rancher.get(), Skill.Strength, Skill.Athletics, false, ignored -> 1),
        () -> WorkerBuildingModuleView::new);

    public static final BuildingEntry.ModuleProducer<RanchHerdingModule, com.minecolonies.api.colony.buildings.modules.IBuildingModuleView> RANCH_HERDING =
      new BuildingEntry.ModuleProducer<>("gg:ranch_herding", RanchHerdingModule::new, null);

    public static final BuildingEntry.ModuleProducer<RanchHerdListModule, RanchHerdListModuleView> RANCH_HERD_LIST =
      new BuildingEntry.ModuleProducer<>("gg:ranch_herd_list", RanchHerdListModule::new, () -> RanchHerdListModuleView::new);

    public static final BuildingEntry.ModuleProducer<SettingsModule, SettingsModuleView> RANCH_SETTINGS =
      new BuildingEntry.ModuleProducer<>("gg:ranch_settings",
        () -> (SettingsModule) new SettingsModule()
          .with(AbstractBuilding.BREEDING, new BoolSetting(true))
          .with(BuildingRanch.FEEDING, new BoolSetting(true))
          .with(BuildingRanch.BUTCHERING, new BoolSetting(true))
          .with(BuildingRanch.SHEARING, new BoolSetting(true))
          .with(BuildingRanch.MILKING, new BoolSetting(true)),
        () -> SettingsModuleView::new);
}
