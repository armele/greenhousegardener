package com.deathfrog.greenhousegardener.core.network;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.api.colony.buildings.BuildingGreenhouse;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.ColonyCropsModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule;
import com.deathfrog.greenhousegardener.core.items.ItemCropJournal;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.items.component.ColonyId;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Requests a validated snapshot for the held crop journal. */
public record RequestCropJournalMessage(InteractionHand hand, BlockPos greenhousePosition) implements IServerboundPayload
{
    @SuppressWarnings("null")
    public static final Type<RequestCropJournalMessage> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, "request_crop_journal"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestCropJournalMessage> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT.map(id -> InteractionHand.values()[Math.max(0, Math.min(id, InteractionHand.values().length - 1))],
            InteractionHand::ordinal), RequestCropJournalMessage::hand,
        BlockPos.STREAM_CODEC, RequestCropJournalMessage::greenhousePosition,
        RequestCropJournalMessage::new);

    @Override
    public Type<RequestCropJournalMessage> type()
    {
        return ID;
    }

    public void onExecute(@NotNull final IPayloadContext context)
    {
        context.enqueueWork(() -> execute(context.player()));
    }

    /** Validate the held journal binding before returning colony information. */
    @SuppressWarnings("null")
    private void execute(final Player player)
    {
        if (!(player instanceof ServerPlayer serverPlayer))
        {
            return;
        }
        final ItemStack stack = serverPlayer.getItemInHand(hand);
        if (!(stack.getItem() instanceof ItemCropJournal)
            || !greenhousePosition.equals(ItemCropJournal.readGreenhousePosition(stack)))
        {
            return;
        }
        final ColonyId colonyId = ColonyId.readFromItemStack(stack);
        final IColony colony = ColonyId.readColonyFromItemStack(stack);
        if (colony == null)
        {
            serverPlayer.displayClientMessage(Component.translatable("item.greenhousegardener.crop_journal.invalid"), true);
            return;
        }
        final IBuilding building = colony.getServerBuildingManager().getBuilding(greenhousePosition);
        if (!(building instanceof BuildingGreenhouse))
        {
            serverPlayer.displayClientMessage(Component.translatable("item.greenhousegardener.crop_journal.invalid"), true);
            return;
        }
        final ColonyCropsModule module = building.getModule(ColonyCropsModule.class, ignored -> true);
        if (module != null)
        {
            final GreenhouseBiomeModule biomeModule = building.getModule(GreenhouseBiomeModule.class, ignored -> true);
            if (biomeModule != null)
            {
                biomeModule.cleanupInvalidOwnedFields();
            }
            new SyncCropJournalMessage(colonyId, greenhousePosition, module.snapshot()).sendToPlayer(serverPlayer);
        }
    }
}
