package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import com.deathfrog.greenhousegardener.api.colony.buildings.BuildingRanch;
import com.deathfrog.greenhousegardener.core.ModTags;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.WorldUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Colony-tick snapshot of the managed herds currently inside a Ranch.
 */
public class RanchHerdListModule extends AbstractBuildingModule implements ITickingModule, IPersistentModule
{
    private static final String TAG_HERD_ORDER = "herdOrder";
    /** Capability flag for entity types that can be milked into bowls. */
    public static final int BOWL_MILKABLE = 1;
    /** Capability flag for entity types that can be milked into buckets. */
    public static final int BUCKET_MILKABLE = 1 << 1;
    /** Capability flag for entity types that can be sheared. */
    public static final int SHEARABLE = 1 << 2;

    private Map<ResourceLocation, HerdSnapshot> herds = Map.of();
    private List<ResourceLocation> herdOrder = List.of();

    /**
     * Rebuilds the managed-herd snapshot on the slower colony tick.
     *
     * <p>Only loaded animals within the MineColonies building boundaries are
     * considered. The module is marked dirty only when the snapshot changes,
     * avoiding unnecessary client synchronization.</p>
     *
     * @param colony colony that owns this Ranch
     */
    @SuppressWarnings("null")
    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        if (building == null || !WorldUtil.isBlockLoaded(colony.getWorld(), building.getPosition()))
        {
            return;
        }

        final Map<ResourceLocation, HerdSnapshot> currentHerds = new LinkedHashMap<>();

        for (final Animal animal : WorldUtil.getEntitiesWithinBuilding(
            colony.getWorld(), Animal.class, building, RanchHerdingModule::isManaged))
        {
            final EntityType<?> type = animal.getType();
            final ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            currentHerds.compute(id, (ignored, herd) -> herd == null
                ? new HerdSnapshot(1, capabilities(animal), preferredBreedingFood(animal))
                : new HerdSnapshot(herd.count() + 1, herd.capabilities(), herd.breedingFood()));
        }

        reconcilePresentTypes(currentHerds.keySet());

        final Map<ResourceLocation, HerdSnapshot> orderedHerds = new LinkedHashMap<>();
        herdOrder.forEach(type ->
        {
            final HerdSnapshot herd = currentHerds.get(type);
            if (herd != null)
            {
                orderedHerds.put(type, herd);
            }
        });

        if (!orderedHerds.equals(herds))
        {
            herds = Collections.unmodifiableMap(orderedHerds);
            markDirty();
        }
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        final List<ResourceLocation> loadedOrder = new ArrayList<>();
        final Set<ResourceLocation> seen = new HashSet<>();
        final ListTag entries = compound.getList(TAG_HERD_ORDER, Tag.TAG_STRING);
        for (int i = 0; i < entries.size(); i++)
        {
            final ResourceLocation id = ResourceLocation.tryParse(entries.getString(i) + "");
            if (id != null && seen.add(id))
            {
                loadedOrder.add(id);
            }
        }
        herdOrder = List.copyOf(loadedOrder);
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        final ListTag entries = new ListTag();
        herdOrder.forEach(type -> entries.add(StringTag.valueOf(type.toString() + "")));
        compound.put(TAG_HERD_ORDER, entries);
    }

    /**
     * Reconciles persisted first-seen ordering with the managed types currently
     * present in the Ranch. Absent types relinquish their place immediately.
     *
     * @param presentTypes exact managed entity types found in the Ranch
     */
    public void reconcilePresentTypes(final Collection<ResourceLocation> presentTypes)
    {
        final List<ResourceLocation> reconciled = RanchHerdOrder.reconcile(herdOrder, presentTypes);
        if (!reconciled.equals(herdOrder))
        {
            herdOrder = List.copyOf(reconciled);
            markDirty();
        }
    }

    /**
     * Reconciles ordering from exact runtime entity types.
     *
     * @param presentTypes entity types currently present
     */
    public void reconcileEntityTypes(final Collection<EntityType<?>> presentTypes)
    {
        reconcilePresentTypes(presentTypes.stream().map(BuiltInRegistries.ENTITY_TYPE::getKey).toList());
    }

    /**
     * Tests whether an exact entity type currently occupies a supported slot.
     *
     * @param type runtime entity type
     * @return true when the type is within the effective configured limit
     */
    public boolean isSupported(final @Nonnull EntityType<?> type)
    {
        final int index = herdOrder.indexOf(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        return index >= 0 && index < getSupportedTypeCapacity();
    }

    public int getSupportedTypeCapacity()
    {
        return building instanceof BuildingRanch ranch ? ranch.getSupportedHerdTypeCapacity() : 0;
    }

    public boolean hasUnsupportedTypes()
    {
        return herdOrder.size() > getSupportedTypeCapacity();
    }

    /**
     * Writes the herd capacity and latest count snapshot to the client view.
     *
     * @param buf destination module-view buffer
     */
    @SuppressWarnings("null")
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        buf.writeInt(building instanceof BuildingRanch ranch ? ranch.getHerdCapacity() : 0);
        buf.writeInt(getSupportedTypeCapacity());
        buf.writeInt(herds.size());
        herds.forEach((type, herd) ->
        {
            buf.writeResourceLocation(type);
            buf.writeInt(herd.count());
            buf.writeInt(herd.capabilities());
            buf.writeBoolean(herdOrder.indexOf(type) < getSupportedTypeCapacity());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, herd.breedingFood());
        });
    }

    /**
     * Return the latest immutable colony-tick herd snapshot.
     *
     * @return a defensive copy containing entity type IDs and their counts
     */
    public Map<ResourceLocation, Integer> getHerdCounts()
    {
        final Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        herds.forEach((type, herd) -> counts.put(type, herd.count()));
        return counts;
    }

    /**
     * Calculates the product capabilities supplied by the active server
     * entity-type tags.
     *
     * @param animal representative managed animal
     * @return bitmask composed from this module's capability constants
     */
    @SuppressWarnings("null")
    private static int capabilities(final Animal animal)
    {
        final EntityType<?> type = animal.getType();
        int capabilities = 0;
        if (type.is(ModTags.ENTITY_TYPES.RANCH_BOWL_MILKABLE))
        {
            capabilities |= BOWL_MILKABLE;
        }
        if (type.is(ModTags.ENTITY_TYPES.RANCH_BUCKET_MILKABLE))
        {
            capabilities |= BUCKET_MILKABLE;
        }
        if (type.is(ModTags.ENTITY_TYPES.RANCH_SHEARABLE)
            && RanchShearability.supports(animal))
        {
            capabilities |= SHEARABLE;
        }
        return capabilities;
    }

    /**
     * Selects the food represented in the herd row, preferring an accepted
     * item already available in Ranch storage.
     *
     * @param representative representative animal from the herd
     * @return preferred food, or an empty stack when the type cannot be bred
     */
    @SuppressWarnings("null")
    private ItemStack preferredBreedingFood(final Animal representative)
    {
        if (!representative.getType().is(ModTags.ENTITY_TYPES.RANCH_BREEDABLE))
        {
            return ItemStack.EMPTY;
        }

        final java.util.List<ItemStorage> foods = RanchBreedingFood.discover(representative);
        for (final ItemStorage food : foods)
        {
            if (InventoryUtils.hasBuildingEnoughElseCount(building, food, 1) > 0)
            {
                return food.getItemStack();
            }
        }
        return foods.isEmpty() ? ItemStack.EMPTY : foods.getFirst().getItemStack();
    }

    /**
     * Server-side colony-tick snapshot for one exact managed entity type.
     *
     * @param count number of animals currently inside the Ranch boundaries
     * @param capabilities product-capability bitmask
     * @param breedingFood preferred breeding food, or an empty stack
     */
    private record HerdSnapshot(int count, int capabilities, ItemStack breedingFood)
    {
    }
}
