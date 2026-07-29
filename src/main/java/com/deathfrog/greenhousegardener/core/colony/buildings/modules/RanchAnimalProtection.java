package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;

/**
 * Central safety policy for Ranch butchering.
 */
public final class RanchAnimalProtection
{
    private RanchAnimalProtection()
    {
    }

    public static boolean mayButcher(final Animal animal)
    {
        if (animal.hasCustomName() || animal.isLeashed())
        {
            return false;
        }
        if (animal instanceof Saddleable saddleable && saddleable.isSaddled())
        {
            return false;
        }
        if (animal instanceof TamableAnimal tamable && tamable.isTame())
        {
            return false;
        }
        return !(animal instanceof OwnableEntity ownable) || ownable.getOwnerUUID() == null;
    }
}
