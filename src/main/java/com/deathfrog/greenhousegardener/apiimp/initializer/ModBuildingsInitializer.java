package com.deathfrog.greenhousegardener.apiimp.initializer;

import javax.annotation.Nonnull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.api.colony.buildings.BuildingGreenhouse;
import com.deathfrog.greenhousegardener.api.colony.buildings.views.GreenhouseView;
import com.deathfrog.greenhousegardener.core.blocks.ModBlocks;
import com.deathfrog.greenhousegardener.core.colony.buildings.ModBuildings;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class ModBuildingsInitializer
{
    private ModBuildingsInitializer()
    {
        throw new IllegalStateException("Tried to initialize ModBuildingsInitializer but this is a utility class.");
    }

    public static void registerBuildings(final RegisterEvent event)
    {
        if (event.getRegistryKey().equals(CommonMinecoloniesAPIImpl.BUILDINGS))
        {
            final BuildingEntry.Builder greenhouseBuilder = new BuildingEntry.Builder();
            greenhouseBuilder.setBuildingBlock(ModBlocks.blockHutGreenhouse.get());
            greenhouseBuilder.setBuildingProducer(BuildingGreenhouse::new);
            greenhouseBuilder.setBuildingViewProducer(() -> GreenhouseView::new);
            greenhouseBuilder.setRegistryName(ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, ModBuildings.GREENHOUSE_ID));
            greenhouseBuilder.addBuildingModuleProducer(BuildingModules.SHOPKEEPER_WORK);
            greenhouseBuilder.addBuildingModuleProducer(BuildingModules.BIOME_MODULE);
            greenhouseBuilder.addBuildingModuleProducer(BuildingModules.TEMPERATURE_MODULE);
            greenhouseBuilder.addBuildingModuleProducer(BuildingModules.HUMIDITY_MODULE);
            greenhouseBuilder.addBuildingModuleProducer(com.minecolonies.core.colony.buildings.modules.BuildingModules.STATS_MODULE);  

            ModBuildings.greenhouse = greenhouseBuilder.createBuildingEntry();
            registerBuilding(event, ModBuildings.greenhouse);
        }
    }

    private static void registerBuilding(final RegisterEvent event, final BuildingEntry buildingEntry)
    {
        @SuppressWarnings("null")
        final @Nonnull ResourceKey<Registry<BuildingEntry>> buildingsRegistry = CommonMinecoloniesAPIImpl.BUILDINGS;
        final ResourceLocation registryName = buildingEntry.getRegistryName();

        if (registryName == null)
        {
            throw new IllegalStateException("Attempting to register the Greenhouse building with no registry name.");
        }

        event.register(buildingsRegistry, registry -> registry.register(registryName, buildingEntry));
    }
}
