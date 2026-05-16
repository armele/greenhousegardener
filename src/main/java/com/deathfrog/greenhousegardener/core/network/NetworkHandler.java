package com.deathfrog.greenhousegardener.core.network;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers Greenhouse Gardener payloads.
 */
public final class NetworkHandler
{
    private NetworkHandler()
    {
    }

    @SuppressWarnings("null")
    public static void register(final RegisterPayloadHandlersEvent event)
    {
        final String modVersion = ModList.get().getModContainerById(GreenhouseGardenerMod.MODID).get().getModInfo().getVersion().toString();
        final PayloadRegistrar registrar = event.registrar(GreenhouseGardenerMod.MODID).versioned(modVersion);
        registrar.playToClient(HighlightFieldBlockMessage.ID, HighlightFieldBlockMessage.STREAM_CODEC, HighlightFieldBlockMessage::onExecute);
        registrar.playToServer(RefreshGreenhouseBiomeModuleMessage.ID, RefreshGreenhouseBiomeModuleMessage.STREAM_CODEC, RefreshGreenhouseBiomeModuleMessage::onExecute);
        registrar.playToServer(SaveGreenhouseBiomeFieldsMessage.ID, SaveGreenhouseBiomeFieldsMessage.STREAM_CODEC, SaveGreenhouseBiomeFieldsMessage::onExecute);
        registrar.playToServer(SetGreenhouseBiomeFieldMessage.ID, SetGreenhouseBiomeFieldMessage.STREAM_CODEC, SetGreenhouseBiomeFieldMessage::onExecute);
        registrar.playToServer(SetGreenhouseClimateItemMessage.ID, SetGreenhouseClimateItemMessage.STREAM_CODEC, SetGreenhouseClimateItemMessage::onExecute);
    }
}
