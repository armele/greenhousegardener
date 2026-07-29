package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import com.deathfrog.greenhousegardener.core.ModTags;
import com.deathfrog.greenhousegardener.core.colony.buildings.jobs.ModJobs;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tag-driven herding module with transient, per-action species and food selection.
 */
public class RanchHerdingModule extends AnimalHerdingModule
{
    /**
     * Ranch operation currently being evaluated or performed.
     *
     * <p>The selected action narrows {@link #isCompatible(Animal)} to the
     * corresponding entity-type capability tag.</p>
     */
    public enum Action
    {
        GENERAL,
        BREED,
        FEED,
        BUTCHER,
        SHEAR,
        MILK
    }

    private EntityType<?> selectedType;
    private List<ItemStorage> breedingItems = List.of();
    private Action action = Action.GENERAL;

    /**
     * Creates the Ranch herding module for the Rancher job.
     *
     * <p>The superclass requires a default food, but the Ranch replaces it
     * with food discovered dynamically for the selected entity type.</p>
     */
    public RanchHerdingModule()
    {
        super(ModJobs.rancher.get(), animal -> true, new ItemStorage(Items.WHEAT));
    }

    /**
     * Selects one exact species and operation for the next Rancher action.
     *
     * @param type exact entity type to operate on
     * @param foods dynamically discovered food choices for that entity type
     * @param action capability operation being evaluated or performed
     */
    public void select(final EntityType<?> type, final List<ItemStorage> foods, final Action action)
    {
        this.selectedType = type;
        this.breedingItems = List.copyOf(foods);
        this.action = action;
    }

    /**
     * Clears all transient action selection, including breeding foods.
     *
     * <p>Clearing the food list prevents inherited herder preparation from
     * turning optional Ranch food into a worker-blocking request.</p>
     */
    public void clearSelection()
    {
        selectedType = null;
        breedingItems = List.of();
        action = Action.GENERAL;
    }

    /**
     * Tests whether an animal belongs to the selected herd and supports the
     * selected operation.
     *
     * @param animal animal being considered by the herder AI
     * @return {@code true} when the animal is managed, is of the selected
     *         exact type, and has the selected capability
     */
    @SuppressWarnings("null")
    /**
     * Returns the currently selected species' dynamically discovered foods.
     *
     * @return immutable list of acceptable food items, or an empty list when
     *         no Ranch action is selected
     */
    @Override
    public boolean isCompatible(@NotNull final Animal animal)
    {
        if (!isManaged(animal) || selectedType != null && animal.getType() != selectedType)
        {
            return false;
        }

        return switch (action)
        {
            case BREED -> animal.getType().is(ModTags.ENTITY_TYPES.RANCH_BREEDABLE);
            case FEED -> animal.getType().is(ModTags.ENTITY_TYPES.RANCH_FEEDABLE);
            case BUTCHER -> animal.getType().is(ModTags.ENTITY_TYPES.RANCH_BUTCHERABLE)
                && RanchAnimalProtection.mayButcher(animal);
            case SHEAR -> animal.getType().is(ModTags.ENTITY_TYPES.RANCH_SHEARABLE);
            case MILK -> animal.getType().is(ModTags.ENTITY_TYPES.RANCH_BUCKET_MILKABLE)
                || animal.getType().is(ModTags.ENTITY_TYPES.RANCH_BOWL_MILKABLE);
            default -> true;
        };
    }

    @Override
    @NotNull
    public List<ItemStorage> getBreedingItems()
    {
        return breedingItems;
    }

    /**
     * Tests whether an animal is included in the Ranch's datapack-managed set.
     *
     * @param animal animal to test
     * @return {@code true} when its entity type is in {@code ranch/animals}
     *         and not in {@code ranch/excluded}
     */
    @SuppressWarnings("null")
    public static boolean isManaged(final Animal animal)
    {
        return animal.getType().is(ModTags.ENTITY_TYPES.RANCH_ANIMALS)
            && !animal.getType().is(ModTags.ENTITY_TYPES.RANCH_EXCLUDED);
    }
}
