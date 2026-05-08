package com.deathfrog.greenhousegardener;

import org.slf4j.Logger;

import com.deathfrog.greenhousegardener.api.sounds.ModSoundEvents;
import com.deathfrog.greenhousegardener.apiimp.initializer.CapabilityInitializer;
import com.deathfrog.greenhousegardener.apiimp.initializer.InteractionInitializer;
import com.deathfrog.greenhousegardener.apiimp.initializer.ModBuildingsInitializer;
import com.deathfrog.greenhousegardener.apiimp.initializer.ModJobsInitializer;
import com.deathfrog.greenhousegardener.apiimp.initializer.TileEntityInitializer;
import com.deathfrog.greenhousegardener.core.advancements.AdvancementTriggers;
import com.deathfrog.greenhousegardener.core.blocks.ModBlocks;
import com.deathfrog.greenhousegardener.core.datalistener.GreenhouseClimateRemainderListener;
import com.deathfrog.greenhousegardener.core.items.ModItems;
import com.deathfrog.greenhousegardener.core.network.NetworkHandler;
import com.deathfrog.greenhousegardener.core.world.GreenhouseAmbientPoofService;
import com.minecolonies.api.creativetab.ModCreativeTabs;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(GreenhouseGardenerMod.MODID)
public class GreenhouseGardenerMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "greenhousegardener";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public GreenhouseGardenerMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModBuildingsInitializer::registerBuildings);
        modEventBus.addListener(CapabilityInitializer::registerCapabilities);
        modEventBus.addListener(NetworkHandler::register);

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        AdvancementTriggers.DEFERRED_REGISTER.register(modEventBus);
        TileEntityInitializer.BLOCK_ENTITIES.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        ModJobsInitializer.DEFERRED_REGISTER.register(modEventBus);  

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        Config.register(modContainer);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        // modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Add a listener for the completion of the load.
        modEventBus.addListener(this::onLoadComplete);

    }

    private void commonSetup(FMLCommonSetupEvent event) {
            
    }

    // Add the greenhouse hut item to the MineColonies huts tab.
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeTabs.HUTS.getKey()))
        {
            event.accept(ModItems.blockHutGreenhouseItem);
            event.accept(ModItems.climateControlHubItem);
        }
        else if (event.getTabKey().equals(ModCreativeTabs.FOOD.getKey()))
        {
            event.accept(ModItems.cucumber);
            event.accept(ModItems.spinach);
            event.accept(ModItems.broccoli);
            event.accept(ModItems.appleCiderVinegar);
            event.accept(ModItems.aussieSpread);
            event.accept(ModItems.aussieToast);
            event.accept(ModItems.baconButty);
            event.accept(ModItems.barbecue);
            event.accept(ModItems.barbecuePlate);
            event.accept(ModItems.biscuits);
            event.accept(ModItems.biscuitsAndGravy);
            event.accept(ModItems.breadcrumbs);
            event.accept(ModItems.brekkiePlate);
            event.accept(ModItems.broccoliCheeseCasserole);
            event.accept(ModItems.cacciatore);
            event.accept(ModItems.chickenAndWaffles);
            event.accept(ModItems.chickpeaCurry);
            event.accept(ModItems.clubSandwich);
            event.accept(ModItems.clubSandwichPlate);
            event.accept(ModItems.coleslaw);
            event.accept(ModItems.cornOil);
            event.accept(ModItems.cottagePie);
            event.accept(ModItems.doner);
            event.accept(ModItems.eggplantParmagiana);
            event.accept(ModItems.friedChicken);
            event.accept(ModItems.gamekeepersPie);
            event.accept(ModItems.garlicCheeseGrits);
            event.accept(ModItems.generalTsosChicken);
            event.accept(ModItems.mayo);
            event.accept(ModItems.mashedPotato);
            event.accept(ModItems.mintyPeas);
            event.accept(ModItems.mixedRice);
            event.accept(ModItems.pastie);
            event.accept(ModItems.pastry);
            event.accept(ModItems.pickles);
            event.accept(ModItems.popcorn);
            event.accept(ModItems.potatoChips);
            event.accept(ModItems.rouladen);
            event.accept(ModItems.sabzi);
            event.accept(ModItems.sausage);
            event.accept(ModItems.sausagePizza);
            event.accept(ModItems.sourdoughBread);
            event.accept(ModItems.sourdoughStarter);
            event.accept(ModItems.spanakopita);
            event.accept(ModItems.spinachSalad);
            event.accept(ModItems.waffles);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    public void onAddReloadListener(final AddReloadListenerEvent event) {
        event.addListener(GreenhouseClimateRemainderListener.INSTANCE);
    }

    @SubscribeEvent
    public void onLevelTick(final LevelTickEvent.Post event) {
        GreenhouseAmbientPoofService.tick(event.getLevel());
    }

    private void onLoadComplete(final FMLLoadCompleteEvent event) {
        LOGGER.info("GreenhouseGardener onLoadComplete"); 
        LOGGER.info("Injecting sounds."); 
        ModSoundEvents.injectSounds();              // These need to be injected both on client (to play) and server (to register)

        /*
        LOGGER.info("Injecting crafting rules.");
        MCTPCraftingSetup.injectCraftingRules();    
        */

        LOGGER.info("Injecting interaction handlers.");
        InteractionInitializer.injectInteractionHandlers();

        /*
        LOGGER.info("Injecting building modules.");
        ModBuildingsInitializer.injectBuildingModules();
        */
    }
}
