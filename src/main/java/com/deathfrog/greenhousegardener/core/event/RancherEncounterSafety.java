package com.deathfrog.greenhousegardener.core.event;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.Animal;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks animals from which a Rancher has recently disengaged and pacifies
 * animals participating in the same attack.
 *
 * <p>Cooldowns are intentionally transient and expire lazily. They prevent the
 * worker AI from restarting a dangerous encounter as soon as MineColonies'
 * citizen-level flee state releases the worker.</p>
 */
public final class RancherEncounterSafety
{
    /**
     * Health ratio below which Ranchers disengage and avoid new butchering.
     */
    public static final double DISENGAGE_HEALTH_RATIO = 0.60D;

    private static final int PACK_SEARCH_RANGE = 16;
    private static final long DAMAGE_GRACE_TICKS = 20L * 10L;
    private static final long BUTCHER_COOLDOWN_TICKS = 20L * 60L;
    private static final Map<UUID, Map<UUID, Long>> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Map<UUID, Long>> DAMAGE_GRACE = new HashMap<>();

    private RancherEncounterSafety()
    {
    }

    /**
     * Pacifies the attacker and same-species pack members currently attacking
     * the Rancher, then places those animals on a temporary butcher cooldown.
     *
     * @param rancher citizen disengaging from combat
     * @param attacker animal that landed the triggering attack
     */
    @SuppressWarnings("null")
    public static void disengage(final AbstractEntityCitizen rancher, final Animal attacker)
    {
        final Map<UUID, Long> rancherCooldowns =
            COOLDOWNS.computeIfAbsent(rancher.getUUID(), ignored -> new HashMap<>());
        final Map<UUID, Long> rancherGrace =
            DAMAGE_GRACE.computeIfAbsent(rancher.getUUID(), ignored -> new HashMap<>());
        final long gameTime = rancher.level().getGameTime();
        final long cooldownExpiration = gameTime + BUTCHER_COOLDOWN_TICKS;
        final long graceExpiration = gameTime + DAMAGE_GRACE_TICKS;

        pacify(rancher, attacker);
        rancherCooldowns.put(attacker.getUUID(), cooldownExpiration);
        rancherGrace.put(attacker.getUUID(), graceExpiration);

        for (final Animal animal : rancher.level().getEntitiesOfClass(
            Animal.class,
            rancher.getBoundingBox().inflate(PACK_SEARCH_RANGE),
            animal -> animal.getType() == attacker.getType() && isAttacking(rancher, animal)))
        {
            pacify(rancher, animal);
            rancherCooldowns.put(animal.getUUID(), cooldownExpiration);
            rancherGrace.put(animal.getUUID(), graceExpiration);
        }
    }

    /**
     * Tests whether a follow-up attack belongs to an encounter's brief damage
     * grace period.
     *
     * @param rancher Rancher receiving the follow-up attack
     * @param attacker animal attempting to deal damage
     * @return {@code true} when the follow-up damage should be suppressed
     */
    public static boolean isDamageSuppressed(
        final AbstractEntityCitizen rancher,
        final Animal attacker)
    {
        return hasActiveEntry(DAMAGE_GRACE, rancher, attacker);
    }

    /**
     * Reapplies pacification to an animal that reacquired the Rancher during
     * the damage grace period without extending either encounter timer.
     *
     * @param rancher Rancher protected by the grace period
     * @param attacker animal that reacquired the Rancher
     */
    public static void reinforcePacification(
        final AbstractEntityCitizen rancher,
        final Animal attacker)
    {
        pacify(rancher, attacker);
    }

    /**
     * Tests whether the Rancher may safely begin or continue butchering the
     * supplied animal.
     *
     * @param rancher Rancher evaluating the target
     * @param animal prospective butcher target
     * @return {@code true} while injured or while the animal is cooling down
     */
    public static boolean isButcheringUnsafe(
        final AbstractEntityCitizen rancher,
        final Animal animal)
    {
        if (rancher.getHealth() < rancher.getMaxHealth() * DISENGAGE_HEALTH_RATIO)
        {
            return true;
        }

        return hasActiveEntry(COOLDOWNS, rancher, animal);
    }

    /**
     * Lazily expires entries and checks an animal-specific timer for a
     * Rancher.
     *
     * @param timers timer collection to query
     * @param rancher Rancher that owns the timer collection
     * @param animal animal whose timer is queried
     * @return {@code true} when an unexpired entry exists
     */
    private static boolean hasActiveEntry(
        final Map<UUID, Map<UUID, Long>> timers,
        final AbstractEntityCitizen rancher,
        final Animal animal)
    {
        final Map<UUID, Long> rancherTimers = timers.get(rancher.getUUID());
        if (rancherTimers == null)
        {
            return false;
        }

        final long gameTime = rancher.level().getGameTime();
        rancherTimers.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
        if (rancherTimers.isEmpty())
        {
            timers.remove(rancher.getUUID());
            return false;
        }
        return rancherTimers.containsKey(animal.getUUID());
    }

    /**
     * Tests whether an animal is actively targeting or persistently angry at
     * the supplied Rancher.
     *
     * @param rancher possible target of the animal
     * @param animal animal whose combat state is inspected
     * @return {@code true} when the animal is attacking this Rancher
     */
    private static boolean isAttacking(
        final AbstractEntityCitizen rancher,
        final Animal animal)
    {
        if (animal instanceof Mob mob && mob.getTarget() == rancher)
        {
            return true;
        }
        return animal instanceof NeutralMob neutral
            && Objects.equals(neutral.getPersistentAngerTarget(), rancher.getUUID());
    }

    /**
     * Clears vanilla combat state directed at the Rancher without disturbing
     * aggression toward unrelated entities.
     *
     * @param rancher Rancher from whom the animal should disengage
     * @param animal animal to pacify
     */
    private static void pacify(
        final AbstractEntityCitizen rancher,
        final Animal animal)
    {
        if (animal instanceof Mob mob && mob.getTarget() == rancher)
        {
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.targetSelector.getAvailableGoals().stream()
                .filter(goal -> goal.isRunning())
                .forEach(goal -> goal.stop());
        }
        if (animal instanceof NeutralMob neutral
            && Objects.equals(neutral.getPersistentAngerTarget(), rancher.getUUID()))
        {
            neutral.stopBeingAngry();
        }
        if (animal.getLastHurtByMob() == rancher)
        {
            animal.setLastHurtByMob(null);
        }
    }
}
