package com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Utils;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side view data shared by greenhouse climate item modules.
 */
public abstract class GreenhouseClimateItemModuleView extends AbstractBuildingModuleView
{
    private final List<ItemStorage> increaseItems = new ArrayList<>();
    private final List<ItemStorage> decreaseItems = new ArrayList<>();

    /**
     * Read the climate item lists from the server-side building module.
     *
     * @param buf serialized module view data
     */
    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf)
    {
        readItems(buf, increaseItems);
        readItems(buf, decreaseItems);
    }

    /**
     * Get the items selected for the top half of the module.
     *
     * @return selected increase items
     */
    public List<ItemStorage> getIncreaseItems()
    {
        return increaseItems;
    }

    /**
     * Get the items selected for the bottom half of the module.
     *
     * @return selected decrease items
     */
    public List<ItemStorage> getDecreaseItems()
    {
        return decreaseItems;
    }

    /**
     * Read one serialized item list into the given view list.
     *
     * @param buf source network buffer
     * @param target list to populate
     */
    private static void readItems(final RegistryFriendlyByteBuf buf, final List<ItemStorage> target)
    {
        target.clear();
        final int size = buf.readInt();
        for (int i = 0; i < size; i++)
        {
            final ItemStack stack = Utils.deserializeCodecMess(buf);
            if (!stack.isEmpty())
            {
                stack.setCount(1);
            }
            final int protectedQuantity = buf.readInt();
            target.add(new ItemStorage(stack, Math.max(0, protectedQuantity)));
        }
    }
}
