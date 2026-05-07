package com.deathfrog.greenhousegardener.apiimp.initializer;

import com.deathfrog.greenhousegardener.api.tileentities.GreenhouseTileEntities;
import com.minecolonies.api.util.IItemHandlerCapProvider;

import net.neoforged.neoforge.capabilities.Capabilities.ItemHandler;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class CapabilityInitializer
{
    @SuppressWarnings("null")
    public static void registerCapabilities(final RegisterCapabilitiesEvent event)
    {
        event.registerBlockEntity(ItemHandler.BLOCK, GreenhouseTileEntities.BUILDING.get(), IItemHandlerCapProvider::getItemHandlerCap);
    }

    private CapabilityInitializer()
    {
        throw new IllegalStateException("Tried to initialize CapabilityInitializer but this is a utility class.");
    }
}
