package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.constant.NbtTagConstants;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Base building module for greenhouse climate items with increase and decrease lists.
 */
public abstract class GreenhouseClimateItemModule extends AbstractBuildingModule implements IPersistentModule
{
    private static final String TAG_INCREASE = "increase";
    private static final String TAG_DECREASE = "decrease";
    private static final String TAG_PROTECTED_QUANTITY = "protectedQuantity";

    private final Map<ClimateItemList, Set<ItemStorage>> items = new EnumMap<>(ClimateItemList.class);

    /**
     * Creates a climate item module with separate increase and decrease item lists.
     */
    protected GreenhouseClimateItemModule()
    {
        items.put(ClimateItemList.INCREASE, new HashSet<>());
        items.put(ClimateItemList.DECREASE, new HashSet<>());
    }

    /**
     * Get the selected items for one side of the climate control module.
     *
     * @param list the item list to return
     * @return the selected item set for that list
     */
    public Set<ItemStorage> getItems(final ClimateItemList list)
    {
        return items.get(list);
    }

    /**
     * Add a climate control item when it belongs to the list's allowed item tag.
     *
     * @param list the target increase or decrease list
     * @param item the item and protected quantity to add
     */
    public void addItem(final ClimateItemList list, final ItemStorage item)
    {
        if (item.getItemStack().isEmpty() || !item.getItemStack().is(getAllowedTag(list)))
        {
            return;
        }

        items.get(list).add(item);
        markDirty();
    }

    /**
     * Remove a climate control item from the given list.
     *
     * @param list the target increase or decrease list
     * @param item the item and protected quantity to remove
     */
    public void removeItem(final ClimateItemList list, final ItemStorage item)
    {
        items.get(list).remove(item);
        markDirty();
    }

    /**
     * Get the item tag that controls which items can be selected for a list.
     *
     * @param list the target increase or decrease list
     * @return the item tag allowed for that list
     */
    public abstract @Nonnull TagKey<Item> getAllowedTag(ClimateItemList list);

    /**
     * Read persisted increase and decrease item lists from NBT.
     *
     * @param provider holder lookup used to deserialize item stacks
     * @param compound building module NBT
     */
    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        items.values().forEach(Set::clear);
        readItems(provider, compound.getList(TAG_INCREASE, Tag.TAG_COMPOUND), items.get(ClimateItemList.INCREASE));
        readItems(provider, compound.getList(TAG_DECREASE, Tag.TAG_COMPOUND), items.get(ClimateItemList.DECREASE));
    }

    /**
     * Write the selected increase and decrease item lists to NBT.
     *
     * @param provider holder lookup used to serialize item stacks
     * @param compound building module NBT
     */
    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        ListTag increaseItems = writeItems(provider, items.get(ClimateItemList.INCREASE));
        if (increaseItems != null)
        {
            compound.put(TAG_INCREASE, increaseItems);
        }

        ListTag decreaseItems = writeItems(provider, items.get(ClimateItemList.DECREASE));
        if (decreaseItems != null)
        {
            compound.put(TAG_DECREASE, decreaseItems);
        }
    }

    /**
     * Send the selected climate item lists to the client module view.
     *
     * @param buf target network buffer
     */
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        writeItems(buf, items.get(ClimateItemList.INCREASE));
        writeItems(buf, items.get(ClimateItemList.DECREASE));
    }

    /**
     * Read persisted item selections into the target item set.
     *
     * @param provider holder lookup used to deserialize item stacks
     * @param tags persisted item tags
     * @param target item set to populate
     */
    private static void readItems(final HolderLookup.Provider provider, final ListTag tags, final Set<ItemStorage> target)
    {

        if (provider == null)
        {
            return;
        }

        for (final Tag tag : tags)
        {
            final CompoundTag itemTag = (CompoundTag) tag;
            final CompoundTag stackTag = itemTag.getCompound(NbtTagConstants.STACK);
            if (stackTag.isEmpty())
            {
                continue;
            }

            target.add(new ItemStorage(ItemStack.parseOptional(provider, stackTag), itemTag.getInt(TAG_PROTECTED_QUANTITY)));
        }
    }

    /**
     * Write selected items to NBT.
     *
     * @param provider holder lookup used to serialize item stacks
     * @param source item set to write
     * @return persisted item list
     */
    @SuppressWarnings("null")
    private static ListTag writeItems(final HolderLookup.Provider provider, final Set<ItemStorage> source)
    {
        final ListTag tags = new ListTag();
        sorted(source).forEach(item -> {
            final CompoundTag itemTag = new CompoundTag();
            itemTag.put(NbtTagConstants.STACK, item.getItemStack().saveOptional(provider));
            itemTag.putInt(TAG_PROTECTED_QUANTITY, item.getAmount());
            tags.add(itemTag);
        });
        return tags;
    }

    /**
     * Write selected items to the building module view buffer.
     *
     * @param buf target network buffer
     * @param source item set to write
     */
    private static void writeItems(final RegistryFriendlyByteBuf buf, final Set<ItemStorage> source)
    {
        final List<ItemStorage> sorted = sorted(source);
        buf.writeInt(sorted.size());
        for (final ItemStorage item : sorted)
        {
            Utils.serializeCodecMess(buf, item.getItemStack());
            buf.writeInt(item.getAmount());
        }
    }

    /**
     * Sort items by display name for stable persistence and GUI ordering.
     *
     * @param source item set to sort
     * @return sorted item list
     */
    private static List<ItemStorage> sorted(final Set<ItemStorage> source)
    {
        return source.stream()
            .sorted(Comparator.comparing(item -> item.getItemStack().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /**
     * The two item lists displayed in a climate control module.
     */
    public enum ClimateItemList
    {
        INCREASE, DECREASE;

        /**
         * Resolve a climate item list from its network id.
         *
         * @param id ordinal value sent over the network
         * @return matching list, or {@link #INCREASE} when out of range
         */
        public static ClimateItemList byId(final int id)
        {
            return id >= 0 && id < values().length ? values()[id] : INCREASE;
        }
    }
}
