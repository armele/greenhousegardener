package com.deathfrog.greenhousegardener.core.network;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseClimateItemModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseClimateItemModule.ClimateItemList;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseHumidityModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseTemperatureModule;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server payload for adding or removing greenhouse climate control items.
 *
 * @param buildingPos building position receiving the update
 * @param moduleType temperature or humidity module id
 * @param listType increase or decrease list id
 * @param action add or remove action id
 * @param itemStack item stack being added or removed
 * @param protectedQuantity item count the worker should leave in storage
 */
public record SetGreenhouseClimateItemMessage(BlockPos buildingPos, int moduleType, int listType, int action, ItemStack itemStack, int protectedQuantity)
    implements IServerboundPayload
{
    @SuppressWarnings("null")
    public static final Type<SetGreenhouseClimateItemMessage> ID =
        new Type<>(ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, "set_greenhouse_climate_item"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, SetGreenhouseClimateItemMessage> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SetGreenhouseClimateItemMessage::buildingPos,
        ByteBufCodecs.INT,
        SetGreenhouseClimateItemMessage::moduleType,
        ByteBufCodecs.INT,
        SetGreenhouseClimateItemMessage::listType,
        ByteBufCodecs.INT,
        SetGreenhouseClimateItemMessage::action,
        ItemStack.OPTIONAL_STREAM_CODEC,
        SetGreenhouseClimateItemMessage::itemStack,
        ByteBufCodecs.INT,
        SetGreenhouseClimateItemMessage::protectedQuantity,
        SetGreenhouseClimateItemMessage::new);

    /**
     * Get this payload's network type.
     *
     * @return payload type
     */
    @Override
    public Type<SetGreenhouseClimateItemMessage> type()
    {
        return ID;
    }

    /**
     * Schedule this climate item update on the server thread.
     *
     * @param context network payload context
     */
    public void onExecute(@NotNull final IPayloadContext context)
    {
        final Player player = context.player();
        context.enqueueWork(() -> execute(player));
    }

    /**
     * Apply the requested climate item list change to the target building module.
     *
     * @param player player who sent the payload
     */
    private void execute(final Player player)
    {
        if (player == null || itemStack.isEmpty())
        {
            return;
        }

        final IBuilding building = IColonyManager.getInstance().getBuilding(player.level(), buildingPos);
        if (building == null)
        {
            return;
        }

        final GreenhouseClimateItemModule module = getModule(building, ClimateModuleType.byId(moduleType));
        if (module == null)
        {
            return;
        }

        final ClimateItemList list = ClimateItemList.byId(listType);
        final ItemStack selectedStack = itemStack.copy();
        selectedStack.setCount(1);
        final ItemStorage item = new ItemStorage(selectedStack, Math.max(0, protectedQuantity));
        if (ClimateItemAction.byId(action) == ClimateItemAction.REMOVE)
        {
            module.removeItem(list, item);
        }
        else
        {
            module.addItem(list, item);
        }
    }

    /**
     * Resolve the concrete climate item module from a building.
     *
     * @param building building containing the module
     * @param type requested module type
     * @return matching module, or null when absent
     */
    private static GreenhouseClimateItemModule getModule(final IBuilding building, final ClimateModuleType type)
    {
        return switch (type) {
            case TEMPERATURE -> building.getModule(GreenhouseTemperatureModule.class, candidate -> true);
            case HUMIDITY -> building.getModule(GreenhouseHumidityModule.class, candidate -> true);
        };
    }

    public enum ClimateModuleType
    {
        TEMPERATURE, HUMIDITY;

        /**
         * Resolve a module type from its network id.
         *
         * @param id ordinal value sent over the network
         * @return matching module type, or {@link #TEMPERATURE} when out of range
         */
        public static ClimateModuleType byId(final int id)
        {
            return id >= 0 && id < values().length ? values()[id] : TEMPERATURE;
        }
    }

    public enum ClimateItemAction
    {
        ADD, REMOVE;

        /**
         * Resolve an item action from its network id.
         *
         * @param id ordinal value sent over the network
         * @return matching action, or {@link #ADD} when out of range
         */
        public static ClimateItemAction byId(final int id)
        {
            return id >= 0 && id < values().length ? values()[id] : ADD;
        }
    }
}
