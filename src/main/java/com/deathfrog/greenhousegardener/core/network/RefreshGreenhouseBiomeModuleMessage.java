package com.deathfrog.greenhousegardener.core.network;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.network.messages.client.colony.ColonyViewBuildingViewMessage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Requests an immediate greenhouse biome module refresh for the player viewing it.
 */
public record RefreshGreenhouseBiomeModuleMessage(BlockPos buildingPos) implements IServerboundPayload
{
    @SuppressWarnings("null")
    public static final Type<RefreshGreenhouseBiomeModuleMessage> ID =
        new Type<>(ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, "refresh_greenhouse_biome_module"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, RefreshGreenhouseBiomeModuleMessage> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        RefreshGreenhouseBiomeModuleMessage::buildingPos,
        RefreshGreenhouseBiomeModuleMessage::new);

    @Override
    public Type<RefreshGreenhouseBiomeModuleMessage> type()
    {
        return ID;
    }

    public void onExecute(@NotNull final IPayloadContext context)
    {
        final Player player = context.player();
        context.enqueueWork(() -> execute(player));
    }

    /**
     * Sends the latest server-side building view to the requesting player only.
     *
     * @param player player who requested the refresh
     */
    private void execute(final Player player)
    {
        if (!(player instanceof ServerPlayer serverPlayer))
        {
            return;
        }

        final IBuilding building = IColonyManager.getInstance().getBuilding(serverPlayer.level(), buildingPos);
        if (building == null)
        {
            return;
        }

        final GreenhouseBiomeModule module = building.getModule(GreenhouseBiomeModule.class, candidate -> true);
        if (module == null)
        {
            return;
        }

        module.cleanupInvalidOwnedFields();
        new ColonyViewBuildingViewMessage(building, false).sendToPlayer(serverPlayer);
    }
}
