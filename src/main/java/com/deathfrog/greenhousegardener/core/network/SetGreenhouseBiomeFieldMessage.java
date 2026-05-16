package com.deathfrog.greenhousegardener.core.network;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.HumiditySetting;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.TemperatureSetting;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Persists a greenhouse field's selected ownership and climate axes.
 */
public record SetGreenhouseBiomeFieldMessage(BlockPos buildingPos, BlockPos fieldPos, int temperatureId, int humidityId, boolean owned, boolean assignmentChanged) implements IServerboundPayload
{
    @SuppressWarnings("null")
    public static final Type<SetGreenhouseBiomeFieldMessage> ID =
        new Type<>(ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, "set_greenhouse_biome_field"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, SetGreenhouseBiomeFieldMessage> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SetGreenhouseBiomeFieldMessage::buildingPos,
        BlockPos.STREAM_CODEC,
        SetGreenhouseBiomeFieldMessage::fieldPos,
        ByteBufCodecs.INT,
        SetGreenhouseBiomeFieldMessage::temperatureId,
        ByteBufCodecs.INT,
        SetGreenhouseBiomeFieldMessage::humidityId,
        ByteBufCodecs.BOOL,
        SetGreenhouseBiomeFieldMessage::owned,
        ByteBufCodecs.BOOL,
        SetGreenhouseBiomeFieldMessage::assignmentChanged,
        SetGreenhouseBiomeFieldMessage::new);

    @Override
    public Type<SetGreenhouseBiomeFieldMessage> type()
    {
        return ID;
    }

    public void onExecute(@NotNull final IPayloadContext context)
    {
        final Player player = context.player();
        context.enqueueWork(() -> execute(player));
    }

    /**
     * Apply the requested field state on the server.
     *
     * @param player player who sent the payload
     */
    private void execute(final Player player)
    {
        if (player == null)
        {
            return;
        }

        final IBuilding building = IColonyManager.getInstance().getBuilding(player.level(), buildingPos);
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
        module.setFieldOwned(fieldPos, owned);
        if (owned && assignmentChanged)
        {
            module.setAssignment(
                fieldPos,
                byId(TemperatureSetting.values(), temperatureId, TemperatureSetting.TEMPERATE),
                byId(HumiditySetting.values(), humidityId, HumiditySetting.NORMAL));
        }
    }

    /**
     * Safely resolve an enum value by network id.
     *
     * @param values enum value array
     * @param id network id
     * @param fallback fallback value when the id is invalid
     * @return the resolved enum value
     * @param <T> enum type
     */
    private static <T> T byId(final T[] values, final int id, final T fallback)
    {
        return id >= 0 && id < values.length ? values[id] : fallback;
    }
}
