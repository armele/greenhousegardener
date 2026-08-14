package com.deathfrog.greenhousegardener.core.network;

import java.util.List;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.ColonyCropsModuleView;
import com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews.ColonyCropsModuleView.CropFieldView;
import com.deathfrog.greenhousegardener.core.client.gui.modules.WindowCropJournal;
import com.minecolonies.api.items.component.ColonyId;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Delivers a server-authoritative crop-journal snapshot to the client. */
public record SyncCropJournalMessage(ColonyId colonyId, BlockPos greenhousePosition, List<CropFieldView> fields)
    implements IClientboundPayload
{
    @SuppressWarnings("null")
    public static final Type<SyncCropJournalMessage> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, "sync_crop_journal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCropJournalMessage> STREAM_CODEC = new StreamCodec<>()
    {
        @Override
        public SyncCropJournalMessage decode(final @Nonnull RegistryFriendlyByteBuf buf)
        {
            return new SyncCropJournalMessage(ColonyId.STREAM_CODEC.decode(buf), buf.readBlockPos(),
                ColonyCropsModuleView.readFields(buf));
        }

        @SuppressWarnings("null")
        @Override
        public void encode(final @Nonnull RegistryFriendlyByteBuf buf, final @Nonnull SyncCropJournalMessage message)
        {
            ColonyId.STREAM_CODEC.encode(buf, message.colonyId());
            buf.writeBlockPos(message.greenhousePosition());
            ColonyCropsModuleView.writeFields(buf, message.fields());
        }
    };

    @Override
    public Type<SyncCropJournalMessage> type()
    {
        return ID;
    }

    public void onExecute(@NotNull final IPayloadContext context)
    {
        context.enqueueWork(() -> WindowCropJournal.acceptSnapshot(colonyId, greenhousePosition, fields));
    }
}
