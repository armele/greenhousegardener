package com.deathfrog.greenhousegardener.core.event;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.core.colony.buildings.jobs.JobRancher;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.BuildingModules;
import com.deathfrog.greenhousegardener.core.entity.EntityAIWorkRancher;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.StatsUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Applies the Rancher's job-specific resistance to animal attacks.
 */
public final class RancherDamageHandler
{
    private static final String RANCH_ATTACKED_BY = "ranch_attacked_by";

    public static final ResourceLocation ANIMAL_RESISTANCE =
        ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, "effects/rancher_animal_resistance");

    private static final double BASE_MITIGATION = 0.20D;
    private static final double MAX_MITIGATION = 0.80D;
    private static final double BASE_BUTCHERING_DAMAGE = 6.0D;

    private RancherDamageHandler()
    {
    }

    /**
     * Records animal attacks against Ranchers, applies skill- and
     * research-based mitigation, and disengages dangerous encounters when the
     * mitigated hit would leave the Rancher below the safety threshold.
     *
     * @param event incoming living-entity damage event
     */
    public static void onIncomingDamage(final LivingIncomingDamageEvent event)
    {
        final Animal attacker = getAnimalAttacker(event.getSource());
        if (!(event.getEntity() instanceof AbstractEntityCitizen citizen)
            || !(citizen.getCitizenJobHandler().getColonyJob() instanceof JobRancher job)
            || attacker == null)
        {
            return;
        }

        final var workBuilding = job.getCitizen().getWorkBuilding();
        if (workBuilding == null)
        {
            return;
        }

        StatsUtil.trackStatByName(
            workBuilding, RANCH_ATTACKED_BY, attacker.getType().getDescriptionId(), 1);

        if (RancherEncounterSafety.isDamageSuppressed(citizen, attacker))
        {
            event.setAmount(0.0F);
            RancherEncounterSafety.reinforcePacification(citizen, attacker);
            final EntityAIWorkRancher workerAI = job.getWorkerAI();
            if (workerAI != null)
            {
                workerAI.disengageFromAnimal(attacker);
            }
            return;
        }

        final int primarySkill = job.getCitizen().getCitizenSkillHandler()
            .getLevel(workBuilding.getModule(BuildingModules.RANCHER_WORK).getPrimarySkill());
        final double researchMitigation = job.getCitizen().getColony().getResearchManager()
            .getResearchEffects().getEffectStrength(ANIMAL_RESISTANCE);
        final double mitigation = calculateMitigation(primarySkill, researchMitigation);
        final float mitigatedDamage = (float) (event.getAmount() * (1.0D - mitigation));

        event.setAmount(mitigatedDamage);
        if (citizen.getHealth() - mitigatedDamage
            < citizen.getMaxHealth() * RancherEncounterSafety.DISENGAGE_HEALTH_RATIO)
        {
            RancherEncounterSafety.disengage(citizen, attacker);
            citizen.callForHelp(attacker, 16);
            final EntityAIWorkRancher workerAI = job.getWorkerAI();
            if (workerAI != null)
            {
                workerAI.disengageFromAnimal(attacker);
            }
        }
    }

    /**
     * Calculates animal-attack mitigation from the Rancher's primary skill and
     * colony research, subject to the overall mitigation cap.
     *
     * @param primarySkill Rancher's primary skill level
     * @param researchMitigation additive mitigation supplied by research
     * @return mitigated fraction in the range from zero through the configured
     *         cap
     */
    static double calculateMitigation(final int primarySkill, final double researchMitigation)
    {
        final double skillMultiplier = 1.0D + Math.max(0, primarySkill) / 100.0D;
        return Math.min(
            MAX_MITIGATION,
            (BASE_MITIGATION + Math.max(0.0D, researchMitigation)) * skillMultiplier);
    }

    /**
     * Calculates the Rancher's butcher-swing damage from the base damage and
     * primary skill multiplier.
     *
     * @param primarySkill Rancher's primary skill level
     * @return damage dealt by a butcher swing
     */
    public static double calculateButcheringDamage(final int primarySkill)
    {
        return BASE_BUTCHERING_DAMAGE * (1.0D + Math.max(0, primarySkill) / 100.0D);
    }

    /**
     * Resolves the animal responsible for a damage event.
     *
     * @param source incoming damage source
     * @return direct or owning animal attacker, or {@code null}
     */
    private static Animal getAnimalAttacker(final DamageSource source)
    {
        final Entity direct = source.getDirectEntity();
        if (direct instanceof Animal animal)
        {
            return animal;
        }
        return source.getEntity() instanceof Animal animal ? animal : null;
    }
}
