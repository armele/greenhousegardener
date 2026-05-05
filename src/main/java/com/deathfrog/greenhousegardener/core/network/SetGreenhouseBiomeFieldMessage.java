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
 * Persists a greenhouse field's selected climate axes.
 */
public record SetGreenhouseBiomeFieldMessage(BlockPos buildingPos, int fieldIndex, int temperatureId, int humidityId) implements IServerboundPayload
{
    @SuppressWarnings("null")
    public static final Type<SetGreenhouseBiomeFieldMessage> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, "set_greenhouse_biome_field"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, SetGreenhouseBiomeFieldMessage> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SetGreenhouseBiomeFieldMessage::buildingPos,
        ByteBufCodecs.INT,
        SetGreenhouseBiomeFieldMessage::fieldIndex,
        ByteBufCodecs.INT,
        SetGreenhouseBiomeFieldMessage::temperatureId,
        ByteBufCodecs.INT,
        SetGreenhouseBiomeFieldMessage::humidityId,
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
        if (module != null)
        {
            module.setAssignment(fieldIndex, byId(TemperatureSetting.values(), temperatureId, TemperatureSetting.TEMPERATE), byId(HumiditySetting.values(), humidityId, HumiditySetting.NORMAL));
        }
    }

    private static <T> T byId(final T[] values, final int id, final T fallback)
    {
        return id >= 0 && id < values.length ? values[id] : fallback;
    }
}
