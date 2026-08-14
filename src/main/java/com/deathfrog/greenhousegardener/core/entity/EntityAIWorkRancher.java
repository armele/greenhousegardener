package com.deathfrog.greenhousegardener.core.entity;

import com.google.common.reflect.TypeToken;
import com.deathfrog.greenhousegardener.api.colony.buildings.BuildingRanch;
import com.deathfrog.greenhousegardener.apiimp.initializer.InteractionInitializer;
import com.deathfrog.greenhousegardener.core.ModTags;
import com.deathfrog.greenhousegardener.core.colony.buildings.jobs.JobRancher;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.RanchBreedingFood;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.RanchHerdingModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.RanchHerdListModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.RanchShearability;
import com.deathfrog.greenhousegardener.core.event.RancherDamageHandler;
import com.deathfrog.greenhousegardener.core.event.RancherEncounterSafety;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.entity.ai.workers.production.herders.AbstractEntityAIHerder;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.StatisticsConstants.ANIMALS_BUTCHERED;
import static com.minecolonies.api.util.constant.StatisticsConstants.ITEM_OBTAINED;
import static com.minecolonies.api.util.constant.StatisticsConstants.ITEM_USED;
import static com.minecolonies.api.util.constant.StatisticsConstants.MILKING_ATTEMPTS;

/**
 * Mixed-species, entity-tag-driven ranch worker.
 */
public class EntityAIWorkRancher extends AbstractEntityAIHerder<JobRancher, BuildingRanch>
{
    private static final String RANCH_ANIMALS_BRED = "ranch_animals_bred";
    private static final String RANCH_ANIMALS_BUTCHERED = "ranch_animals_butchered";
    private static final String RANCH_ANIMALS_SHEARED = "ranch_animals_sheared";
    private static final double PICKUP_CHANCE = 0.10D;
    private static final double FEED_CHANCE = 0.10D;
    private static final int ACTION_DELAY = 20;
    private static final int MILK_COOLDOWN = 200;
    private static final int MAX_BUTCHER_TARGET_ATTEMPTS = 30;
    private static final int BREEDING_FOOD_REQUEST_COUNT = 8;
    private static final int BREEDING_FOOD_REQUEST_MINIMUM = 2;
    private static final int IDLE_ATTEMPTS_BEFORE_BREAK = 5;
    private static final int WANDER_RANGE = 20;
    private static final double WANDER_SPEED = 0.6D;
    private static final double PRODUCT_OPPORTUNITY_BASE_CHANCE = 0.35D;
    private static final double PRODUCT_OPPORTUNITY_CHANCE_STEP = 0.20D;
    private static final int PRODUCT_OPPORTUNITY_MAX_MISSES = 4;
    private static final int SEVERE_OVERCAP_MULTIPLIER = 2;
    private final Map<EntityType<?>, Integer> pendingBreedingParents = new HashMap<>();
    private Animal interactionTarget;
    private Animal butcherTarget;
    private int butcherTargetTicks;
    private int milkCooldown;
    private int idleAttempts;
    private int productOpportunityMisses;

    /**
     * Ranch-specific states not supplied by the base MineColonies herder AI.
     */
    public enum RancherState implements IAIState
    {
        SHEAR,
        MILK,
        WANDER;

        /**
         * Allows normal citizen eating interruptions during Ranch actions.
         *
         * @return {@code true} for every Ranch-specific state
         */
        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    /**
     * Creates and registers the mixed-herd Rancher AI.
     *
     * @param job Rancher job owned by the worker
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public EntityAIWorkRancher(@NotNull final JobRancher job)
    {
        super(job);
        super.registerTargets(
            new AITarget(RancherState.SHEAR, this::shearAnimal, ACTION_DELAY),
            new AITarget(RancherState.MILK, this::milkAnimal, ACTION_DELAY),
            new AITarget(RancherState.WANDER, this::wanderWithinRanch, ACTION_DELAY));
    }

    /**
     * Returns the building class supported by this worker AI.
     *
     * @return Ranch building class
     */
    @Override
    public Class<BuildingRanch> getExpectedBuildingClass()
    {
        return BuildingRanch.class;
    }

    /**
     * Returns the uniform per-level herd-capacity multiplier.
     *
     * @return animals added to each exact species' capacity per building level
     */
    @Override
    public int getMaxAnimalMultiplier()
    {
        return BuildingRanch.HERD_CAPACITY_PER_LEVEL;
    }

    /**
     * Returns the base herder tools plus shears when a managed shearable herd
     * is present and shearing is enabled.
     *
     * @return equipment types currently required by the Rancher
     */
    @NotNull
    @Override
    public List<EquipmentTypeEntry> getExtraToolsNeeded()
    {
        final List<EquipmentTypeEntry> tools = super.getExtraToolsNeeded();
        if (building != null
            && building.getSetting(BuildingRanch.SHEARING).getValue()
            && hasShearableHerd())
        {
            tools.add(ModEquipmentTypes.shears.get());
        }
        return tools;
    }

    /**
     * Check for a managed herd capable of shearing. Readiness is intentionally
     * ignored so the worker keeps shears while an animal's coat regrows.
     *
     * @return {@code true} when at least one managed type supports shearing
     */
    @SuppressWarnings("null")
    private boolean hasShearableHerd()
    {
        return !searchForAnimals(animal ->
            RanchHerdingModule.isManaged(animal)
                && animal.getType().is(ModTags.ENTITY_TYPES.RANCH_SHEARABLE)
                && RanchShearability.supports(animal))
            .isEmpty();
    }

    /**
     * Selects the next eligible action across all exact-species herds.
     *
     * <p>Over-cap butchering is prioritized, breeding may cross the capacity
     * only when butchering is enabled, and repeated empty passes transition
     * through wandering before true idle.</p>
     *
     * @return selected work, wander, or idle state
     */
    @Override
    public IAIState decideWhatToDo()
    {
        worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);
        interactionTarget = null;
        clearButcherTarget();
        if (milkCooldown > 0)
        {
            milkCooldown--;
        }

        final RanchHerdingModule module = ranchModule();
        module.clearSelection();

        final Map<EntityType<?>, List<Animal>> byType = new LinkedHashMap<>();
        for (final Animal animal : searchForAnimals(RanchHerdingModule::isManaged))
        {
            byType.computeIfAbsent(animal.getType(), ignored -> new ArrayList<>()).add(animal);
        }

        final RanchHerdListModule herdListModule = building.getModule(RanchHerdListModule.class);
        herdListModule.reconcileEntityTypes(byType.keySet());
        final boolean tooManyTypes = herdListModule.hasUnsupportedTypes();
        updateHerdTypeInteraction(tooManyTypes, herdListModule.getSupportedTypeCapacity());

        @SuppressWarnings("null")
        final List<Map.Entry<EntityType<?>, List<Animal>>> herds = byType.entrySet().stream()
            .filter(herd -> herdListModule.isSupported(herd.getKey()))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            
        for (int i = herds.size() - 1; i > 0; i--)
        {
            Collections.swap(herds, i, worker.getRandom().nextInt(i + 1));
        }

        final IAIState severeOvercapTask = selectSevereOvercapButchering(herds, module);
        if (severeOvercapTask != null)
        {
            return foundTask(severeOvercapTask);
        }

        final IAIState productTask = selectRenewableProduct(herds, module);
        if (productTask != null)
        {
            return foundTask(productTask);
        }

        for (final Map.Entry<EntityType<?>, List<Animal>> herd : herds)
        {
            final EntityType<?> type = herd.getKey();
            final List<Animal> animals = herd.getValue();
            final List<ItemStorage> foods = preferredFood(RanchBreedingFood.discover(animals.getFirst()));
            current_module = module;

            if (worker.getRandom().nextDouble() < PICKUP_CHANCE && !searchForItemsInArea().isEmpty())
            {
                module.select(type, foods, RanchHerdingModule.Action.GENERAL);
                return foundTask(HERDER_PICKUP);
            }

            module.select(type, foods, RanchHerdingModule.Action.BUTCHER);
            final List<? extends Animal> butcherable = searchForAnimals(animal ->
                module.isCompatible(animal)
                    && !RancherEncounterSafety.isButcheringUnsafe(worker, animal));
            final int herdCapacity = building.getHerdCapacity();
            final boolean butcheringEnabled = building.getSetting(BuildingRanch.BUTCHERING).getValue();
            final boolean overCapacity = animals.size() > herdCapacity;
            if (butcheringEnabled && overCapacity && hasButcherableAdult(butcherable))
            {
                return foundTask(HERDER_BUTCHER);
            }

            module.select(type, foods, RanchHerdingModule.Action.BREED);
            final boolean breedingNeeded = !tooManyTypes
                && building.getSetting(AbstractBuilding.BREEDING).getValue()
                && (animals.size() < herdCapacity || animals.size() == herdCapacity && butcheringEnabled)
                && !foods.isEmpty()
                && searchForAnimals(animal -> module.isCompatible(animal) && isBreedAble(animal)).size() >= 2;
            if (breedingNeeded)
            {
                pullFoodFromRanch(foods.getFirst(), 2);
            }
            if (breedingNeeded && hasFoodItems(foods, 2))
            {
                return foundTask(HERDER_BREED);
            }
            if (breedingNeeded)
            {
                requestBreedingFood(foods.getFirst());
            }

            module.select(type, foods, RanchHerdingModule.Action.FEED);
            final boolean feedingNeeded = building.getSetting(BuildingRanch.FEEDING).getValue()
                && !foods.isEmpty()
                && !searchForAnimals(animal -> module.isCompatible(animal) && isFeedAble(animal)).isEmpty();
            if (feedingNeeded)
            {
                pullFoodFromRanch(foods.getFirst(), 1);
            }
            if (feedingNeeded && hasFoodItems(foods, 1)
                && worker.getRandom().nextDouble() < FEED_CHANCE)
            {
                return foundTask(HERDER_FEED);
            }
            if (feedingNeeded && !hasFoodItems(foods, 1))
            {
                requestBreedingFood(foods.getFirst());
            }

            if (building.getSetting(BuildingRanch.SHEARING).getValue())
            {
                module.select(type, foods, RanchHerdingModule.Action.SHEAR);
                interactionTarget = animals.stream()
                    .filter(module::isCompatible)
                    .filter(this::isReadyToShear)
                    .findFirst().orElse(null);
                if (interactionTarget != null)
                {
                    return foundTask(RancherState.SHEAR);
                }
            }

            if (milkCooldown == 0 && building.getSetting(BuildingRanch.MILKING).getValue())
            {
                module.select(type, foods, RanchHerdingModule.Action.MILK);
                interactionTarget = animals.stream()
                    .filter(module::isCompatible)
                    .filter(animal -> !animal.isBaby())
                    .findFirst().orElse(null);
                if (interactionTarget != null)
                {
                    return foundTask(RancherState.MILK);
                }
            }
        }

        module.clearSelection();
        idleAttempts = Math.min(IDLE_ATTEMPTS_BEFORE_BREAK, idleAttempts + 1);
        if (idleAttempts < IDLE_ATTEMPTS_BEFORE_BREAK)
        {
            return RancherState.WANDER;
        }
        worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.HOUSE);
        return IDLE;
    }

    /**
     * Maintains one blocking interaction while more managed entity types are
     * present than this Ranch can support.
     */
    private void updateHerdTypeInteraction(final boolean overloaded, final int supportedTypes)
    {
        final boolean wasOverloaded = job.hasHerdTypeOverload();
        job.setHerdTypeOverload(overloaded);
        if (overloaded && !wasOverloaded)
        {
            worker.getCitizenData().triggerInteraction(new StandardInteraction(
                Component.translatable(InteractionInitializer.RANCH_TOO_MANY_ANIMAL_TYPES, supportedTypes),
                Component.translatable(InteractionInitializer.RANCH_TOO_MANY_ANIMAL_TYPES_TITLE),
                ChatPriority.BLOCKING));
        }
    }

    /**
     * Selects forced population control before all renewable-product work when
     * an exact-species herd exceeds twice its capacity.
     *
     * @param herds shuffled exact-species herds
     * @param module Ranch action-selection module
     * @return butcher state for severe overcrowding, or {@code null}
     */
    private IAIState selectSevereOvercapButchering(
        final List<Map.Entry<EntityType<?>, List<Animal>>> herds,
        final RanchHerdingModule module)
    {
        if (!building.getSetting(BuildingRanch.BUTCHERING).getValue())
        {
            return null;
        }

        final int severeLimit = building.getHerdCapacity() * SEVERE_OVERCAP_MULTIPLIER;
        for (final Map.Entry<EntityType<?>, List<Animal>> herd : herds)
        {
            if (herd.getValue().size() <= severeLimit)
            {
                continue;
            }

            final List<ItemStorage> foods =
                preferredFood(RanchBreedingFood.discover(herd.getValue().getFirst()));
            module.select(herd.getKey(), foods, RanchHerdingModule.Action.BUTCHER);
            current_module = module;
            final List<? extends Animal> butcherable = searchForAnimals(animal ->
                module.isCompatible(animal)
                    && !RancherEncounterSafety.isButcheringUnsafe(worker, animal));
            if (hasButcherableAdult(butcherable))
            {
                return HERDER_BUTCHER;
            }
        }
        return null;
    }

    /**
     * Gives ready shearing and milking work an escalating opportunity to run
     * before normal population, breeding, and feeding decisions.
     *
     * <p>Repeated misses raise the probability until the fifth eligible pass
     * is guaranteed. The counter resets when no renewable product is ready or
     * when the selected product is successfully collected.</p>
     *
     * @param herds shuffled exact-species herds
     * @param module Ranch action-selection module
     * @return selected product state, or {@code null} when normal priorities
     *         should continue
     */
    private IAIState selectRenewableProduct(
        final List<Map.Entry<EntityType<?>, List<Animal>>> herds,
        final RanchHerdingModule module)
    {
        final List<ProductTask> candidates = new ArrayList<>();
        for (final Map.Entry<EntityType<?>, List<Animal>> herd : herds)
        {
            final List<ItemStorage> foods =
                preferredFood(RanchBreedingFood.discover(herd.getValue().getFirst()));

            if (building.getSetting(BuildingRanch.SHEARING).getValue())
            {
                module.select(herd.getKey(), foods, RanchHerdingModule.Action.SHEAR);
                herd.getValue().stream()
                    .filter(module::isCompatible)
                    .filter(this::isReadyToShear)
                    .forEach(animal -> candidates.add(
                        new ProductTask(animal, herd.getKey(), foods, RancherState.SHEAR)));
            }

            if (milkCooldown == 0 && building.getSetting(BuildingRanch.MILKING).getValue())
            {
                module.select(herd.getKey(), foods, RanchHerdingModule.Action.MILK);
                herd.getValue().stream()
                    .filter(module::isCompatible)
                    .filter(animal -> !animal.isBaby())
                    .forEach(animal -> candidates.add(
                        new ProductTask(animal, herd.getKey(), foods, RancherState.MILK)));
            }
        }

        if (candidates.isEmpty())
        {
            productOpportunityMisses = 0;
            return null;
        }

        final double chance = Math.min(
            1.0D,
            PRODUCT_OPPORTUNITY_BASE_CHANCE
                + productOpportunityMisses * PRODUCT_OPPORTUNITY_CHANCE_STEP);
        if (worker.getRandom().nextDouble() >= chance)
        {
            productOpportunityMisses = Math.min(
                PRODUCT_OPPORTUNITY_MAX_MISSES, productOpportunityMisses + 1);
            return null;
        }

        final ProductTask selected = candidates.get(worker.getRandom().nextInt(candidates.size()));
        interactionTarget = selected.animal();
        final RanchHerdingModule.Action action = selected.state() == RancherState.SHEAR
            ? RanchHerdingModule.Action.SHEAR
            : RanchHerdingModule.Action.MILK;
        module.select(selected.type(), selected.foods(), action);
        current_module = module;
        return selected.state();
    }

    /**
     * Tests whether a compatible herd contains an adult that the inherited
     * butcher action can actually select.
     *
     * @param animals protected-filtered butcher candidates
     * @return {@code true} when at least one adult is not currently breeding
     */
    private static boolean hasButcherableAdult(final List<? extends Animal> animals)
    {
        return animals.stream().anyMatch(animal -> !animal.isBaby() && !animal.isInLove());
    }

    /**
     * Records that the current decision pass found legitimate Ranch work.
     *
     * @param state selected action state
     * @return the supplied state
     */
    private IAIState foundTask(final IAIState state)
    {
        idleAttempts = 0;
        return state;
    }

    /**
     * Walks to a random navigable ground position within the Ranch boundaries
     * before checking the herds for work again.
     *
     * @return this state while walking, then {@link
     *         com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState#DECIDE}
     */
    private IAIState wanderWithinRanch()
    {
        if (!EntityNavigationUtils.walkToRandomPosWithin(
            worker, WANDER_RANGE, WANDER_SPEED, building.getCorners(), true))
        {
            return getState();
        }
        return DECIDE;
    }

    /**
     * Keep one butcher target across AI ticks. The base implementation selects
     * a fresh animal on every invocation, which causes navigation thrashing in
     * large, moving herds.
     *
     * @return butcher while pursuing a retained target, otherwise the next
     *         preparation or decision state
     */
    @SuppressWarnings("null")
    @Override
    protected IAIState butcherAnimals()
    {
        if (current_module == null)
        {
            clearButcherTarget();
            return DECIDE;
        }

        if (!equipTool(InteractionHand.MAIN_HAND, ModEquipmentTypes.axe.get()))
        {
            clearButcherTarget();
            return START_WORKING;
        }

        final List<? extends Animal> eligible = searchForAnimals(animal ->
            current_module.isCompatible(animal)
                && !animal.isBaby()
                && !animal.isInLove()
                && !RancherEncounterSafety.isButcheringUnsafe(worker, animal));

        if (butcherTarget == null)
        {
            butcherTarget = eligible.stream()
                .min(Comparator
                    .comparingDouble(Animal::getHealth)
                    .thenComparingDouble(animal -> animal.distanceToSqr(worker)))
                .orElse(null);
            butcherTargetTicks = 0;
        }
        else if (!butcherTarget.isAlive()
            || eligible.stream().noneMatch(animal -> animal.getUUID().equals(butcherTarget.getUUID())))
        {
            clearButcherTarget();
            return DECIDE;
        }

        if (butcherTarget == null)
        {
            return DECIDE;
        }

        final Animal target = butcherTarget;
        butcherTargetTicks++;
        butcherAnimal(target);

        if (!target.isAlive())
        {
            StatsUtil.trackStat(building, ANIMALS_BUTCHERED, 1);
            StatsUtil.trackStatByName(
                building, RANCH_ANIMALS_BUTCHERED, target.getType().getDescriptionId(), 1);
            worker.getCitizenExperienceHandler().addExperience(XP_PER_ACTION);
            incrementActionsDone();
            worker.decreaseSaturationForContinuousAction();
            clearButcherTarget();
            return DECIDE;
        }

        if (butcherTargetTicks >= MAX_BUTCHER_TARGET_ATTEMPTS)
        {
            clearButcherTarget();
            return DECIDE;
        }

        return HERDER_BUTCHER;
    }

    /**
     * Clears the retained butcher target and its pursuit-attempt counter.
     */
    private void clearButcherTarget()
    {
        butcherTarget = null;
        butcherTargetTicks = 0;
    }

    /**
     * Abandons a dangerous butchering encounter after an animal has reduced
     * this Rancher below the disengagement health threshold.
     *
     * @param attacker animal responsible for the triggering attack
     */
    public void disengageFromAnimal(final Animal attacker)
    {
        if (interactionTarget == attacker)
        {
            interactionTarget = null;
        }
        clearButcherTarget();
        current_module = null;
    }

    /**
     * Preserve the FakePlayer damage source used for drops and Looting, but
     * make a surviving animal recognize the visible Rancher as its attacker.
     *
     * @param fakePlayer MineColonies attack proxy
     * @param animal animal receiving the butcher attack
     */
    /**
     * Calculates butcher damage from the configured Rancher base damage and
     * the worker's primary skill.
     *
     * @return damage dealt by each butcher swing
     */
    @Override
    protected void butcherSwing(final FakePlayer fakePlayer, final Animal animal)
    {
        super.butcherSwing(fakePlayer, animal);
        if (animal.isAlive())
        {
            animal.setLastHurtByMob(worker);
        }
    }

    @Override
    public double getButcheringAttackDamage()
    {
        return RancherDamageHandler.calculateButcheringDamage(getPrimarySkillLevel());
    }

    /**
     * Preserve the base breeding-attempt and item-use statistics and add one
     * Ranch breeding result for every pair of parents placed into love mode.
     *
     * @return next breeding or decision state from the inherited implementation
     */
    /**
     * Tests NeoForge's runtime shearable contract for the supplied animal.
     *
     * @param animal animal to inspect
     * @return {@code true} when the animal can currently be sheared
     */
    @SuppressWarnings("null")
    @Override
    protected IAIState breedAnimals()
    {
        final List<? extends Animal> candidates = current_module == null
            ? List.of()
            : searchForAnimals(current_module::isCompatible);
        final Map<java.util.UUID, Boolean> previouslyInLove = new HashMap<>();
        candidates.forEach(animal -> previouslyInLove.put(animal.getUUID(), animal.isInLove()));

        final IAIState result = super.breedAnimals();
        final Map<EntityType<?>, Integer> newlyBredParents = new HashMap<>();
        candidates.stream()
            .filter(Animal::isInLove)
            .filter(animal -> !previouslyInLove.getOrDefault(animal.getUUID(), false))
            .forEach(animal -> newlyBredParents.merge(animal.getType(), 1, Integer::sum));

        newlyBredParents.forEach((type, count) ->
        {
            final int parents = pendingBreedingParents.getOrDefault(type, 0) + count;
            final int pairs = parents / 2;
            if (pairs > 0)
            {
                StatsUtil.trackStatByName(
                    building, RANCH_ANIMALS_BRED, type.getDescriptionId(), pairs);
            }
            pendingBreedingParents.put(type, parents % 2);
        });
        return result;
    }

    /**
     * Handle shearing activity.
     * 
     * @return next AI state
     */
    @SuppressWarnings("null")
    private IAIState shearAnimal()
    {
        if (interactionTarget == null
            || !RanchShearability.supports(interactionTarget)
            || !interactionTarget.isAlive())
        {
            return DECIDE;
        }
        if (!equipTool(InteractionHand.MAIN_HAND, ModEquipmentTypes.shears.get()))
        {
            return START_WORKING;
        }
        if (walkingToAnimal(interactionTarget))
        {
            return getState();
        }

        Level targetLevel = interactionTarget.level();

        if (targetLevel == null) return DECIDE;

        BlockPos targetBlockPos = interactionTarget.blockPosition();

        if (targetBlockPos == null) return DECIDE;

        final ItemStack shears = worker.getMainHandItem();
        final FakePlayer fakePlayer = getFakePlayer();
        if (shears == null || fakePlayer == null
            || !RanchShearability.isShearable(interactionTarget, fakePlayer, shears))
        {
            return DECIDE;
        }

        worker.swing(InteractionHand.MAIN_HAND);
        final List<ItemStack> drops =
            RanchShearability.shear(interactionTarget, fakePlayer, shears);
        for (final ItemStack drop : drops)
        {
            final ItemStack remainder =
                InventoryUtils.transferItemStackIntoNextBestSlotInItemHandlerWithResult(
                    drop.copy(), worker.getInventoryCitizen());
            final int inserted = drop.getCount() - remainder.getCount();
            if (inserted > 0)
            {
                StatsUtil.trackStatByName(building, ITEM_OBTAINED, drop.getHoverName(), inserted);
            }
            if (!remainder.isEmpty())
            {
                RanchShearability.spawnDrop(interactionTarget, remainder);
            }
        }
        StatsUtil.trackStatByName(
            building, RANCH_ANIMALS_SHEARED, interactionTarget.getType().getDescriptionId(), 1);
        interactionTarget.gameEvent(GameEvent.SHEAR, fakePlayer);
        CitizenItemUtils.damageItemInHand(worker, InteractionHand.MAIN_HAND, 1);
        worker.getCitizenExperienceHandler().addExperience(XP_PER_ACTION);
        incrementActionsDoneAndDecSaturation();
        productOpportunityMisses = 0;
        interactionTarget = null;
        return DECIDE;
    }

    @SuppressWarnings("null")
    private boolean isReadyToShear(final Animal animal)
    {
        final ItemStack shears = new ItemStack(Items.SHEARS);
        final FakePlayer fakePlayer = getFakePlayer();
        return fakePlayer != null
            && RanchShearability.isShearable(animal, fakePlayer, shears);
    }

    /**
     * Handles milking operation.
     * 
     * @return next AI state
     */
    @SuppressWarnings("null")
    private IAIState milkAnimal()
    {
        final Animal animal = interactionTarget;
        if (animal == null || !animal.isAlive() || animal.isBaby())
        {
            return DECIDE;
        }

        final Item container = animal.getType().is(ModTags.ENTITY_TYPES.RANCH_BOWL_MILKABLE)
            ? Items.BOWL : Items.BUCKET;
        final List<ItemStorage> containers = List.of(new ItemStorage(container));
        if (!equipItem(InteractionHand.MAIN_HAND, containers))
        {
            return START_WORKING;
        }
        if (walkingToAnimal(animal))
        {
            return getState();
        }

        final FakePlayer fakePlayer = getFakePlayer();
        if (fakePlayer == null)
        {
            milkCooldown = MILK_COOLDOWN;
            return DECIDE;
        }

        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(container));
        final InteractionResult result = animal.mobInteract(fakePlayer, InteractionHand.MAIN_HAND);
        final ItemStack output = fakePlayer.getMainHandItem().copy();
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        if (result.consumesAction() && !output.isEmpty() && !output.is(container)
            && InventoryUtils.addItemStackToItemHandler(worker.getInventoryCitizen(), output))
        {
            InventoryUtils.tryRemoveStackFromItemHandler(worker.getInventoryCitizen(), new ItemStack(container));
            StatsUtil.trackStatByName(building, ITEM_USED, container.getDescriptionId(), 1);
            StatsUtil.trackStatByName(building, ITEM_OBTAINED, output.getHoverName(), output.getCount());
            StatsUtil.trackStat(building, MILKING_ATTEMPTS, 1);
            worker.getCitizenExperienceHandler().addExperience(XP_PER_ACTION);
            incrementActionsDoneAndDecSaturation();
            productOpportunityMisses = 0;
        }

        milkCooldown = MILK_COOLDOWN;
        interactionTarget = null;
        return DECIDE;
    }

    /**
     * Ready renewable-product work discovered during the decision pre-pass.
     *
     * @param animal animal supplying the product
     * @param type exact managed entity type
     * @param foods dynamically discovered breeding foods for module context
     * @param state Rancher product-collection state
     */
    private record ProductTask(
        Animal animal,
        EntityType<?> type,
        List<ItemStorage> foods,
        IAIState state)
    {
    }

    /**
     * Returns the Ranch's transient action-selection module.
     *
     * @return Ranch herding module
     */
    private RanchHerdingModule ranchModule()
    {
        return building.getModule(RanchHerdingModule.class);
    }

    /**
     * Counts acceptable food across the worker inventory.
     *
     * @param foods acceptable foods for the selected exact species
     * @param count minimum combined item count
     * @return {@code true} when the worker holds at least the requested count
     */
    @SuppressWarnings("null")
    private boolean hasFoodItems(final List<ItemStorage> foods, final int count)
    {
        return InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(),
            stack -> foods.stream().anyMatch(food -> food.getItemStack().is(stack.getItem()))) >= count;
    }

    /**
     * Move available optional food from Ranch storage into the worker inventory
     * without creating a citizen-owned request.
     *
     * @param food preferred food to transfer
     * @param desiredCount target count in the worker inventory
     */
    private void pullFoodFromRanch(final ItemStorage food, final int desiredCount)
    {
        final ItemStack requested = food.getItemStack();
        final int held = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(),
            stack -> ItemStackUtils.compareItemStacksIgnoreStackSize(stack, requested));
        final int missing = Math.max(0, desiredCount - held);
        if (missing == 0
            || InventoryUtils.hasBuildingEnoughElseCount(building, food, missing) < missing)
        {
            return;
        }

        InventoryUtils.transferXOfFirstSlotInProviderWithIntoNextFreeSlotInItemHandlerWithResult(
            building,
            stack -> ItemStackUtils.compareItemStacksIgnoreStackSize(stack, requested),
            missing,
            worker.getInventoryCitizen());
    }

    /**
     * Keep optional animal food in Ranch storage without attaching a blocking
     * material request to the Rancher.
     *
     * @param food preferred food to request for the Ranch
     */
    private void requestBreedingFood(final ItemStorage food)
    {
        final ItemStack requested = food.getItemStack();
        if (requested.isEmpty()
            || InventoryUtils.hasBuildingEnoughElseCount(building, food, BREEDING_FOOD_REQUEST_MINIMUM)
                >= BREEDING_FOOD_REQUEST_MINIMUM)
        {
            return;
        }

        final java.util.Collection<IToken<?>> requests = building.getOpenRequestsByRequestableType()
            .getOrDefault(TypeToken.of(Stack.class), List.of());
        for (final IToken<?> token : requests)
        {
            final IRequest<?> request = building.getColony().getRequestManager().getRequestForToken(token);
            if (request != null && request.getRequest() instanceof Stack stack
                && ItemStackUtils.compareItemStacksIgnoreStackSize(stack.getStack(), requested))
            {
                return;
            }
        }

        building.createRequest(new Stack(
            requested,
            true,
            true,
            ItemStackUtils.EMPTY,
            BREEDING_FOOD_REQUEST_COUNT,
            BREEDING_FOOD_REQUEST_MINIMUM,
            false), true);
    }

    /**
     * Invalidates shared dynamic animal-food discovery after a data reload.
     */
    public static void invalidateFoodCaches()
    {
        RanchBreedingFood.invalidate();
    }

    /**
     * Keep the inherited herder request logic focused on one acceptable food.
     * Otherwise it requests every item for which a modded animal returns true.
     *
     * @param foods dynamically discovered acceptable foods
     * @return a singleton preferred-food list, or an empty list
     */
    @SuppressWarnings("null")
    private List<ItemStorage> preferredFood(final List<ItemStorage> foods)
    {
        for (final ItemStorage food : foods)
        {
            if (InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(),
                stack -> stack.is(food.getItemStack().getItem())) > 0)
            {
                return List.of(food);
            }
        }
        for (final ItemStorage food : foods)
        {
            if (InventoryUtils.hasBuildingEnoughElseCount(building, food, 1) > 0)
            {
                return List.of(food);
            }
        }
        return foods.isEmpty() ? List.of() : List.of(foods.getFirst());
    }
}
