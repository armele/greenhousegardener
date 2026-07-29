package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.IShearable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

/**
 * Resolves and invokes the runtime shearing contract supported by an animal.
 *
 * <p>NeoForge's {@link IShearable} contract is preferred. Some optional mods,
 * including Naturalist, expose equivalent public methods without declaring
 * that interface; those methods are discovered once per entity class and
 * invoked without creating a required dependency on the optional mod.</p>
 */
public final class RanchShearability
{
    private static final Map<Class<?>, ShearingAccess> ACCESS_BY_CLASS = new ConcurrentHashMap<>();
    private static final ShearingAccess UNSUPPORTED = new ShearingAccess(null, null);

    private RanchShearability()
    {
    }

    /**
     * Tests whether an animal implements a shearing contract understood by the
     * Ranch.
     *
     * @param animal animal to inspect
     * @return {@code true} for NeoForge or compatible method-based shearables
     */
    public static boolean supports(final Animal animal)
    {
        return animal instanceof IShearable || accessFor(animal) != UNSUPPORTED;
    }

    /**
     * Tests the animal's current shearing readiness through its resolved
     * runtime contract.
     *
     * @param animal animal to inspect
     * @param player player performing the readiness probe
     * @param shears shears used for the readiness check
     * @return {@code true} when the animal can currently be sheared
     */
    @SuppressWarnings("null")
    public static boolean isShearable(
        final Animal animal,
        final Player player,
        final @Nonnull ItemStack shears)
    {
        if (player == null) return false;

        if (animal instanceof IShearable shearable)
        {
            return shearable.isShearable(
                player, shears, animal.level(), animal.blockPosition());
        }

        final ShearingAccess access = accessFor(animal);
        if (access == UNSUPPORTED)
        {
            return false;
        }

        try
        {
            return (boolean) access.isShearable().invoke(
                animal, player, shears, animal.level(), animal.blockPosition());
        }
        catch (final IllegalAccessException | InvocationTargetException | ClassCastException exception)
        {
            GreenhouseGardenerMod.LOGGER.warn(
                "Unable to query compatible shearing contract on {}", animal.getType(), exception);
            return false;
        }
    }

    /**
     * Shears an animal through its resolved runtime contract.
     *
     * @param animal animal to shear
     * @param player player performing the operation
     * @param shears shears used for the operation
     * @return drops produced by shearing, or an empty list on failure
     */
    @SuppressWarnings("null")
    public static List<ItemStack> shear(
        final Animal animal,
        final Player player,
        final @Nonnull ItemStack shears)
    {

        if (player == null) return List.of();

        if (animal instanceof IShearable shearable)
        {
            return shearable.onSheared(
                player, shears, animal.level(), animal.blockPosition());
        }

        final ShearingAccess access = accessFor(animal);
        if (access == UNSUPPORTED)
        {
            return List.of();
        }

        try
        {
            final Object result = access.onSheared().invoke(
                animal, player, shears, animal.level(), animal.blockPosition());
            if (!(result instanceof List<?> drops)
                || drops.stream().anyMatch(drop -> !(drop instanceof ItemStack)))
            {
                return List.of();
            }
            return drops.stream().map(ItemStack.class::cast).toList();
        }
        catch (final IllegalAccessException | InvocationTargetException exception)
        {
            GreenhouseGardenerMod.LOGGER.warn(
                "Unable to invoke compatible shearing contract on {}", animal.getType(), exception);
            return List.of();
        }
    }

    /**
     * Spawns a shearing drop that did not fit in the Rancher's inventory.
     * Interface implementations retain their customized drop behavior; a
     * compatible method-based animal uses a normal item entity.
     *
     * @param animal animal that produced the drop
     * @param drop remaining item stack
     */
    @SuppressWarnings("null")
    public static void spawnDrop(final Animal animal, final ItemStack drop)
    {
        if (animal instanceof IShearable shearable)
        {
            shearable.spawnShearedDrop(animal.level(), animal.blockPosition(), drop);
            return;
        }

        final ItemEntity itemEntity = new ItemEntity(
            animal.level(), animal.getX(), animal.getY() + 1.0D, animal.getZ(), drop);
        itemEntity.setDefaultPickUpDelay();
        animal.level().addFreshEntity(itemEntity);
    }

    /**
     * Returns the cached compatible method contract for an animal class.
     *
     * @param animal representative animal
     * @return resolved access methods or the unsupported sentinel
     */
    private static ShearingAccess accessFor(final Animal animal)
    {
        return ACCESS_BY_CLASS.computeIfAbsent(
            animal.getClass(), RanchShearability::discoverAccess);
    }

    /**
     * Discovers public methods matching NeoForge's shearing signatures.
     *
     * @param animalClass entity implementation class
     * @return compatible method access or the unsupported sentinel
     */
    private static ShearingAccess discoverAccess(final Class<?> animalClass)
    {
        try
        {
            final Method isShearable = animalClass.getMethod(
                "isShearable", Player.class, ItemStack.class, Level.class, BlockPos.class);
            final Method onSheared = animalClass.getMethod(
                "onSheared", Player.class, ItemStack.class, Level.class, BlockPos.class);
            if (isShearable.getReturnType() != boolean.class
                || !List.class.isAssignableFrom(onSheared.getReturnType()))
            {
                return UNSUPPORTED;
            }
            return new ShearingAccess(isShearable, onSheared);
        }
        catch (final NoSuchMethodException exception)
        {
            return UNSUPPORTED;
        }
    }

    /**
     * Cached public methods for a compatible method-based shearing contract.
     *
     * @param isShearable readiness method
     * @param onSheared shearing operation method
     */
    private record ShearingAccess(Method isShearable, Method onSheared)
    {
    }
}
