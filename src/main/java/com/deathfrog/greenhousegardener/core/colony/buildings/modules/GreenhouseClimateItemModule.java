package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.Config;
import com.deathfrog.greenhousegardener.core.ModTags;
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
    private static final String TAG_LEDGER_INCREASE = "ledgerIncrease";
    private static final String TAG_LEDGER_DECREASE = "ledgerDecrease";
    public static final int DEFAULT_LEDGER_LIMIT = 500;

    private final Map<ClimateItemList, Set<ItemStorage>> items = new EnumMap<>(ClimateItemList.class);
    private final Map<ClimateItemList, Integer> ledgerBalances = new EnumMap<>(ClimateItemList.class);

    /**
     * Creates a climate item module with separate increase and decrease item lists.
     */
    protected GreenhouseClimateItemModule()
    {
        items.put(ClimateItemList.INCREASE, new HashSet<>());
        items.put(ClimateItemList.DECREASE, new HashSet<>());
        ledgerBalances.put(ClimateItemList.INCREASE, 0);
        ledgerBalances.put(ClimateItemList.DECREASE, 0);
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
     * Get the climate modification type represented by one side of this module.
     *
     * @param list the target increase or decrease list
     * @return the climate modification type ledgered by the list
     */
    public abstract @Nonnull ClimateModificationType getModificationType(ClimateItemList list);

    /**
     * Get the current ledgered climate modification balance for a list.
     *
     * @param list the target increase or decrease list
     * @return current ledger balance
     */
    public int getLedgerBalance(final ClimateItemList list)
    {
        return ledgerBalances.getOrDefault(list, 0);
    }

    /**
     * Get the maximum balance this module tries to keep for a list.
     *
     * @param list the target increase or decrease list
     * @return ledger limit
     */
    public int getLedgerLimit(final ClimateItemList list)
    {
        return DEFAULT_LEDGER_LIMIT;
    }

    /**
     * Check if this list can accept more ledgered climate modification power.
     *
     * @param list the target increase or decrease list
     * @return true when the balance is below the configured limit
     */
    public boolean isLedgerUnderLimit(final ClimateItemList list)
    {
        return isLedgerUnderTarget(list, getLedgerLimit(list));
    }

    /**
     * Check if this list can accept more ledgered climate modification power for a specific target balance.
     *
     * @param list the target increase or decrease list
     * @param targetBalance desired ledger balance
     * @return true when the balance is below the desired target
     */
    public boolean isLedgerUnderTarget(final ClimateItemList list, final int targetBalance)
    {
        return getLedgerBalance(list) < Math.max(0, targetBalance);
    }

    /**
     * Calculate a sensible request count for this item based on the remaining ledger capacity.
     *
     * @param list the target increase or decrease list
     * @param stack selected climate item
     * @return requested item count, or zero when the item has no modification value
     */
    public int getLedgerRequestCount(final ClimateItemList list, final ItemStack stack)
    {
        return getLedgerRequestCount(list, stack, getLedgerLimit(list));
    }

    /**
     * Calculate a sensible request count for this item based on the remaining balance needed for a target.
     *
     * @param list the target increase or decrease list
     * @param stack selected climate item
     * @param targetBalance desired ledger balance
     * @return requested item count, or zero when the item has no modification value
     */
    public int getLedgerRequestCount(final ClimateItemList list, final ItemStack stack, final int targetBalance)
    {
        final int unit = climateModificationUnit(stack);
        if (unit <= 0 || !isLedgerUnderTarget(list, targetBalance))
        {
            return 0;
        }

        final int remaining = Math.max(0, targetBalance) - getLedgerBalance(list);
        final int requiredItems = Math.max(1, (int) Math.ceil((double) remaining / unit));
        return Math.min(stack.getMaxStackSize(), requiredItems);
    }

    /**
     * Add an item stack's climate modification power to a list ledger.
     *
     * @param list the target increase or decrease list
     * @param stack stack consumed by the horticulturist
     * @return amount of climate modification power added
     */
    public int ledgerStack(final ClimateItemList list, final ItemStack stack)
    {
        return ledgerStack(list, stack, getLedgerLimit(list));
    }

    /**
     * Add an item stack's climate modification power to a list ledger until a specific target balance is reached.
     *
     * @param list the target increase or decrease list
     * @param stack stack consumed by the horticulturist
     * @param targetBalance desired ledger balance
     * @return amount of climate modification power added
     */
    public int ledgerStack(final ClimateItemList list, final ItemStack stack, final int targetBalance)
    {
        if (stack.isEmpty() || !stack.is(getAllowedTag(list)) || !isLedgerUnderTarget(list, targetBalance))
        {
            return 0;
        }

        final int value = climateModificationUnit(stack) * stack.getCount();
        if (value <= 0)
        {
            return 0;
        }

        ledgerBalances.put(list, getLedgerBalance(list) + value);
        markDirty();
        return value;
    }

    /**
     * Deduct climate modification power from a list ledger when enough balance is available.
     *
     * @param list the target increase or decrease list
     * @param amount amount of climate modification power to deduct
     * @return true when the amount was deducted or no amount was required
     */
    public boolean tryDebitLedger(final ClimateItemList list, final int amount)
    {
        if (amount <= 0)
        {
            return true;
        }

        final int balance = getLedgerBalance(list);
        if (balance < amount)
        {
            return false;
        }

        ledgerBalances.put(list, balance - amount);
        markDirty();
        return true;
    }

    /**
     * Resolve the climate modification unit for a selected item from its tier tag.
     *
     * @param stack selected climate item
     * @return unit value contributed by one item
     */
    public static int climateModificationUnit(final ItemStack stack)
    {
        if (stack.is(ModTags.ITEMS.GREENHOUSE_TEMP_INCREASE_HIGH)
            || stack.is(ModTags.ITEMS.GREENHOUSE_TEMP_DECREASE_HIGH)
            || stack.is(ModTags.ITEMS.GREENHOUSE_HUMIDITY_INCREASE_HIGH)
            || stack.is(ModTags.ITEMS.GREENHOUSE_HUMIDITY_DECREASE_HIGH))
        {
            return Config.climateControlUnitsHigh.get();
        }

        if (stack.is(ModTags.ITEMS.GREENHOUSE_TEMP_INCREASE_MEDIUM)
            || stack.is(ModTags.ITEMS.GREENHOUSE_TEMP_DECREASE_MEDIUM)
            || stack.is(ModTags.ITEMS.GREENHOUSE_HUMIDITY_INCREASE_MEDIUM)
            || stack.is(ModTags.ITEMS.GREENHOUSE_HUMIDITY_DECREASE_MEDIUM))
        {
            return Config.climateControlUnitsMedium.get();
        }

        if (stack.is(ModTags.ITEMS.GREENHOUSE_TEMP_INCREASE_LOW)
            || stack.is(ModTags.ITEMS.GREENHOUSE_TEMP_DECREASE_LOW)
            || stack.is(ModTags.ITEMS.GREENHOUSE_HUMIDITY_INCREASE_LOW)
            || stack.is(ModTags.ITEMS.GREENHOUSE_HUMIDITY_DECREASE_LOW))
        {
            return Config.climateControlUnitsLow.get();
        }

        return 0;
    }

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
        ledgerBalances.put(ClimateItemList.INCREASE, compound.getInt(TAG_LEDGER_INCREASE));
        ledgerBalances.put(ClimateItemList.DECREASE, compound.getInt(TAG_LEDGER_DECREASE));
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

        compound.putInt(TAG_LEDGER_INCREASE, getLedgerBalance(ClimateItemList.INCREASE));
        compound.putInt(TAG_LEDGER_DECREASE, getLedgerBalance(ClimateItemList.DECREASE));
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

    /**
     * Climate modification ledgers maintained by greenhouse workers.
     */
    public enum ClimateModificationType
    {
        HOT, COLD, HUMID, DRY
    }
}
