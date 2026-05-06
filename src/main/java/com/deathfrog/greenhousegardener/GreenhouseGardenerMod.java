package com.deathfrog.greenhousegardener;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.greenhousegardener.api.sounds.ModSoundEvents;
import com.deathfrog.greenhousegardener.apiimp.initializer.InteractionInitializer;
import com.deathfrog.greenhousegardener.apiimp.initializer.ModBuildingsInitializer;
import com.deathfrog.greenhousegardener.apiimp.initializer.ModJobsInitializer;
import com.deathfrog.greenhousegardener.apiimp.initializer.TileEntityInitializer;
import com.deathfrog.greenhousegardener.core.blocks.BlockClimateControlHub;
import com.deathfrog.greenhousegardener.core.blocks.huts.BlockHutGreenhouse;
import com.deathfrog.greenhousegardener.core.network.NetworkHandler;
import com.minecolonies.api.creativetab.ModCreativeTabs;
import com.minecolonies.api.items.ItemBlockHut;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(GreenhouseGardenerMod.MODID)
public class GreenhouseGardenerMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "greenhousegardener";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "greenhousegardener" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "greenhousegardener" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    /*
    * BLOCKS
    */
    public static final DeferredBlock<BlockHutGreenhouse> blockHutGreenhouse = BLOCKS.register(BlockHutGreenhouse.HUT_NAME, () -> new BlockHutGreenhouse());
    public static final DeferredBlock<BlockClimateControlHub> climateControlHub = BLOCKS.register(BlockClimateControlHub.BLOCK_NAME, () -> new BlockClimateControlHub());

    /*
    * ITEMS
    */
    @SuppressWarnings("null")
    public static final @Nonnull DeferredItem<ItemBlockHut> blockHutGreenhouseItem = ITEMS.register(
      BlockHutGreenhouse.HUT_NAME,
      () -> new ItemBlockHut(blockHutGreenhouse.get(), new Item.Properties()));

    @SuppressWarnings("null")
    public static final @Nonnull DeferredItem<BlockItem> climateControlHubItem = ITEMS.register(
      BlockClimateControlHub.BLOCK_NAME,
      () -> new BlockItem(climateControlHub.get(), new Item.Properties()));
  
    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public GreenhouseGardenerMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModBuildingsInitializer::registerBuildings);
        modEventBus.addListener(NetworkHandler::register);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
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
            event.accept(blockHutGreenhouseItem);
            event.accept(climateControlHubItem);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
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
