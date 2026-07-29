package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import com.deathfrog.greenhousegardener.api.colony.buildings.BuildingRanch;
import com.deathfrog.greenhousegardener.core.ModTags;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.WorldUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Colony-tick snapshot of the managed herds currently inside a Ranch.
 */
public class RanchHerdListModule extends AbstractBuildingModule implements ITickingModule
{
    /** Capability flag for entity types that can be milked into bowls. */
    public static final int BOWL_MILKABLE = 1;
    /** Capability flag for entity types that can be milked into buckets. */
    public static final int BUCKET_MILKABLE = 1 << 1;
    /** Capability flag for entity types that can be sheared. */
    public static final int SHEARABLE = 1 << 2;

    private Map<ResourceLocation, HerdSnapshot> herds = Map.of();

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

        final Map<ResourceLocation, HerdSnapshot> currentHerds =
            new TreeMap<>(Comparator.comparing(ResourceLocation::toString));

        for (final Animal animal : WorldUtil.getEntitiesWithinBuilding(
            colony.getWorld(), Animal.class, building, RanchHerdingModule::isManaged))
        {
            final EntityType<?> type = animal.getType();
            final ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            currentHerds.compute(id, (ignored, herd) -> herd == null
                ? new HerdSnapshot(1, capabilities(animal), preferredBreedingFood(animal))
                : new HerdSnapshot(herd.count() + 1, herd.capabilities(), herd.breedingFood()));
        }

        if (!currentHerds.equals(herds))
        {
            herds = Collections.unmodifiableMap(new LinkedHashMap<>(currentHerds));
            markDirty();
        }
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
        buf.writeInt(herds.size());
        herds.forEach((type, herd) ->
        {
            buf.writeResourceLocation(type);
            buf.writeInt(herd.count());
            buf.writeInt(herd.capabilities());
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
