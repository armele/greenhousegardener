package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import com.minecolonies.api.crafting.ItemStorage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared dynamic breeding-food discovery for Ranch AI and building views.
 */
public final class RanchBreedingFood
{
    private static final Map<EntityType<?>, List<ItemStorage>> FOOD_CACHE = new ConcurrentHashMap<>();

    private RanchBreedingFood()
    {
    }

    /**
     * Returns all items accepted as food by a representative animal.
     *
     * <p>Results are cached by exact entity type because probing every
     * registered item is relatively expensive.</p>
     *
     * @param representative animal used to test {@link Animal#isFood(ItemStack)}
     * @return immutable list of accepted food items
     */
    public static List<ItemStorage> discover(final Animal representative)
    {
        return FOOD_CACHE.computeIfAbsent(representative.getType(), ignored ->
        {
            final List<ItemStorage> foods = new ArrayList<>();
            for (final Item item : BuiltInRegistries.ITEM)
            {
                final ItemStack stack = item.getDefaultInstance();
                if (!stack.isEmpty() && representative.isFood(stack))
                {
                    foods.add(new ItemStorage(stack));
                }
            }
            return List.copyOf(foods);
        });
    }

    /**
     * Clears cached food results after datapack tags or registries reload.
     */
    public static void invalidate()
    {
        FOOD_CACHE.clear();
    }
}
