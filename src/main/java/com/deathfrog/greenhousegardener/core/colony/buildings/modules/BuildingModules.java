package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.GreenhouseBiomeModuleView;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;

public class BuildingModules
{
    public static final BuildingEntry.ModuleProducer<WorkshopModule, GreenhouseBiomeModuleView> BIOME_MODULE     =
      new BuildingEntry.ModuleProducer<WorkshopModule, GreenhouseBiomeModuleView>("biome_settings", () -> new WorkshopModule(), () -> GreenhouseBiomeModuleView::new);
}
