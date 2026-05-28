package com.deathfrog.greenhousegardener.core.network;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseClimateItemModule.ClimateModificationType;
import com.deathfrog.greenhousegardener.core.datalistener.GreenhouseClimateItemValueListener;
import com.deathfrog.greenhousegardener.core.datalistener.GreenhouseClimateItemValueListener.SyncedClimateItemValue;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-to-client payload containing the server-authoritative greenhouse climate item values.
 *
 * @param values climate item values loaded from server datapacks
 */
public record SyncGreenhouseClimateItemsMessage(List<SyncedClimateItemValue> values) implements IClientboundPayload
{
    private static final int MAX_VALUES = 4096;

    @SuppressWarnings("null")
    public static final Type<SyncGreenhouseClimateItemsMessage> ID =
        new Type<>(ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, "sync_greenhouse_climate_items"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncGreenhouseClimateItemsMessage> STREAM_CODEC = new StreamCodec<>()
    {
        @Override
        public SyncGreenhouseClimateItemsMessage decode(final RegistryFriendlyByteBuf buf)
        {
            final int count = Math.min(buf.readVarInt(), MAX_VALUES);
            final List<SyncedClimateItemValue> values = new ArrayList<>(count);

            for (int i = 0; i < count; i++)
            {
                final ClimateModificationType type = typeById(buf.readVarInt());
                final boolean tag = buf.readBoolean();
                final ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
                final int value = buf.readVarInt();
                values.add(new SyncedClimateItemValue(type, tag, id, value));
            }

            return new SyncGreenhouseClimateItemsMessage(values);
        }

        @Override
        public void encode(final RegistryFriendlyByteBuf buf, final SyncGreenhouseClimateItemsMessage message)
        {
            final List<SyncedClimateItemValue> values = message.values();
            buf.writeVarInt(Math.min(values.size(), MAX_VALUES));

            for (int i = 0; i < values.size() && i < MAX_VALUES; i++)
            {
                final SyncedClimateItemValue value = values.get(i);
                buf.writeVarInt(value.type().ordinal());
                buf.writeBoolean(value.tag());
                ResourceLocation.STREAM_CODEC.encode(buf, value.id());
                buf.writeVarInt(value.value());
            }
        }
    };

    @Override
    public Type<SyncGreenhouseClimateItemsMessage> type()
    {
        return ID;
    }

    /**
     * Apply synced climate item values on the client thread.
     *
     * @param context network payload context
     */
    public void onExecute(@NotNull final IPayloadContext context)
    {
        context.enqueueWork(() -> GreenhouseClimateItemValueListener.INSTANCE.applySyncedValues(values));
    }

    private static ClimateModificationType typeById(final int id)
    {
        return id >= 0 && id < ClimateModificationType.values().length ? ClimateModificationType.values()[id] :
            ClimateModificationType.HOT;
    }
}
