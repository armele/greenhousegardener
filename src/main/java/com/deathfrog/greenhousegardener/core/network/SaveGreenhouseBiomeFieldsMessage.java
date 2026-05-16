package com.deathfrog.greenhousegardener.core.network;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.FieldBiomeAssignment;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.FieldBiomeChange;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.HumiditySetting;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.TemperatureSetting;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Saves a complete set of drafted greenhouse biome field changes as one atomic update.
 */
public record SaveGreenhouseBiomeFieldsMessage(BlockPos buildingPos, List<FieldChange> changes) implements IServerboundPayload
{
    private static final String BIOME_LIMIT_REACHED = "com.greenhousegardener.core.gui.biome.limit_reached";

    @SuppressWarnings("null")
    public static final Type<SaveGreenhouseBiomeFieldsMessage> ID =
        new Type<>(ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, "save_greenhouse_biome_fields"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveGreenhouseBiomeFieldsMessage> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SaveGreenhouseBiomeFieldsMessage::buildingPos,
        ByteBufCodecs.collection(ArrayList::new, FieldChange.STREAM_CODEC),
        SaveGreenhouseBiomeFieldsMessage::changes,
        SaveGreenhouseBiomeFieldsMessage::new);

    @Override
    public Type<SaveGreenhouseBiomeFieldsMessage> type()
    {
        return ID;
    }

    public void onExecute(@NotNull final IPayloadContext context)
    {
        final Player player = context.player();
        context.enqueueWork(() -> execute(player));
    }

    /**
     * Apply the requested field changes on the server.
     *
     * @param player player who sent the payload
     */
    @SuppressWarnings("null")
    private void execute(final Player player)
    {
        if (player == null || changes == null || changes.isEmpty())
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
        if (!module.applyFieldChanges(moduleChanges()))
        {
            player.displayClientMessage(Component.translatable(BIOME_LIMIT_REACHED), true);
        }
    }

    /**
     * Convert network changes into module changes with validated enum values.
     *
     * @return module-level field changes
     */
    private List<FieldBiomeChange> moduleChanges()
    {
        final List<FieldBiomeChange> converted = new ArrayList<>();
        for (final FieldChange change : changes)
        {
            if (change == null)
            {
                continue;
            }

            converted.add(new FieldBiomeChange(
                change.fieldPos(),
                new FieldBiomeAssignment(
                    byId(TemperatureSetting.values(), change.temperatureId(), TemperatureSetting.TEMPERATE),
                    byId(HumiditySetting.values(), change.humidityId(), HumiditySetting.NORMAL)),
                change.owned()));
        }
        return converted;
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

    /**
     * One drafted field state from the client.
     */
    public record FieldChange(BlockPos fieldPos, int temperatureId, int humidityId, boolean owned)
    {
        @SuppressWarnings("null")
        public static final StreamCodec<RegistryFriendlyByteBuf, FieldChange> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            FieldChange::fieldPos,
            ByteBufCodecs.INT,
            FieldChange::temperatureId,
            ByteBufCodecs.INT,
            FieldChange::humidityId,
            ByteBufCodecs.BOOL,
            FieldChange::owned,
            FieldChange::new);
    }
}
