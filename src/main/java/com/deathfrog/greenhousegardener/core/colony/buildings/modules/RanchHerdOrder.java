package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure first-seen ordering policy shared by Ranch persistence and tests.
 */
public final class RanchHerdOrder
{
    private RanchHerdOrder()
    {
    }

    /**
     * Retains present known types in their established order, then appends
     * newly seen types in a deterministic registry-name order.
     *
     * @param existingOrder previously observed first-seen order
     * @param presentTypes types present during the latest complete scan
     * @return reconciled immutable order
     */
    @SuppressWarnings("null")
    public static List<ResourceLocation> reconcile(
        final Collection<ResourceLocation> existingOrder,
        final Collection<ResourceLocation> presentTypes)
    {
        final Set<ResourceLocation> present = new HashSet<>(presentTypes);
        final Set<ResourceLocation> known = new HashSet<>();
        final List<ResourceLocation> reconciled = new ArrayList<>();

        for (final ResourceLocation type : existingOrder)
        {
            if (present.contains(type) && known.add(type))
            {
                reconciled.add(type);
            }
        }

        final List<ResourceLocation> newlySeen = new ArrayList<>();
        for (final ResourceLocation type : presentTypes)
        {
            if (known.add(type))
            {
                newlySeen.add(type);
            }
        }
        newlySeen.sort(Comparator.comparing(ResourceLocation::toString));
        reconciled.addAll(newlySeen);
        return List.copyOf(reconciled);
    }
}
