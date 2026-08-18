package com.deathfrog.greenhousegardener.core.client.network;

import com.deathfrog.greenhousegardener.core.client.gui.modules.WindowCropJournal;
import com.deathfrog.greenhousegardener.core.network.SyncCropJournalMessage;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client-only application of crop-journal snapshots. */
@OnlyIn(Dist.CLIENT)
public final class ClientCropJournalPayloadHandler
{
    private ClientCropJournalPayloadHandler()
    {
    }

    public static void handle(final SyncCropJournalMessage message, final IPayloadContext context)
    {
        context.enqueueWork(() -> WindowCropJournal.acceptSnapshot(
            message.colonyId(), message.greenhousePosition(), message.fields()));
    }
}
