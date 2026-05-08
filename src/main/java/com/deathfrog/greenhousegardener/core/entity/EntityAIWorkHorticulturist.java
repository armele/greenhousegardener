package com.deathfrog.greenhousegardener.core.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.deathfrog.greenhousegardener.Config;
import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.ModCommands;
import com.deathfrog.greenhousegardener.api.colony.buildings.BuildingGreenhouse;
import com.deathfrog.greenhousegardener.ModResearch;
import com.deathfrog.greenhousegardener.apiimp.initializer.InteractionInitializer;
import com.deathfrog.greenhousegardener.core.ModTags;
import com.deathfrog.greenhousegardener.core.advancements.AdvancementTriggers;
import com.deathfrog.greenhousegardener.core.colony.buildings.jobs.JobsHorticulturist;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.FieldBiomeAssignment;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.HumiditySetting;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.TemperatureSetting;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseClimateItemModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseClimateItemModule.ClimateItemList;
import com.deathfrog.greenhousegardener.core.datalistener.GreenhouseClimateRemainderListener;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseHumidityModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseTemperatureModule;
import com.deathfrog.greenhousegardener.core.util.DomumOrnamentumRoofHelper;
import com.deathfrog.greenhousegardener.core.util.TraceUtils;
import com.deathfrog.greenhousegardener.core.world.GreenhouseBiomeOverlayService;
import com.deathfrog.greenhousegardener.core.world.GreenhouseBiomeOverlayService.GreenhouseClimate;
import com.deathfrog.greenhousegardener.core.world.GreenhouseBiomeOverlayService.OverlayResult;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.api.util.constant.StatisticsConstants;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.util.AdvancementUtils;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

public class EntityAIWorkHorticulturist extends AbstractEntityAIInteract<JobsHorticulturist, BuildingGreenhouse>
{
    private static final String FIELDS_TRANSFORMED_STAT = "fields_transformed";
    private static final String FIELDS_MAINTAINED_STAT = "fields_maintained";
    private static final String FIELD_TRANSFORMED_MESSAGE = "entity.horticulturist.field_transformed";
    private static final String CLIMATE_MATERIAL_REQUEST = "Greenhouse Climate Material";
    private static final double BASE_BIOME_TRANSFORM_XP = 1.0D;
    private static final double BASE_BIOME_MAINTENANCE_XP = 0.25D;
    private static final int BIOME_CELLS_PER_BONUS_XP = 11;
    private static final int FIELD_TRANSFORMED_POOF_COUNT = 96;
    private static final int MAX_GREENHOUSE_ROOF_HEIGHT = 20;
    private static final int ROOF_INSPECTION_CORNERS = 4;
    private static final int NORMAL_PRIMARY_SKILL = 20;
    private static final int MAX_PRIMARY_SKILL = 99;
    private static final double MAX_PRIMARY_SKILL_COST_DISCOUNT = 0.5D;
    private static final int NORMAL_SECONDARY_SKILL = 20;
    private static final int MAX_SECONDARY_SKILL = 99;
    private static final double MAX_SECONDARY_SKILL_SPEED_BONUS = 0.5D;


    /**
     * How many times the AI should attempt to find an allegedly delivered item before giving up on it.
     */
    private int deliverAcceptanceCounter = 0;
    private static final int SOFT_DELIVERY_ACCEPTANCE_COUNTER = 10;
    private static final int HARD_DELIVERY_ACCEPTANCE_COUNTER = 20;

    private FarmField currentField;
    private int currentFieldIndex = -1;
    private int currentFieldRange = 0;
    private BlockPos currentRoofInspectionTarget;
    private int roofValidationMinX = 0;
    private int roofValidationMaxX = 0;
    private int roofValidationMinZ = 0;
    private int roofValidationMaxZ = 0;
    private int roofInspectionCornerIndex = 0;
    private IAIState roofValidationSuccessState = DECIDE;
    private ClimateLedgerTarget currentLedgerTarget;

    public enum HorticulturistState implements IAIState
    {
        LEDGER_CLIMATE_MATERIAL,
        VALIDATE_FIELD_ROOF,
        TRANSFORM_FIELD,
        MAINTAIN_FIELD,
        UNSET_FIELD_SEED,
        WANDER_IN_BUILDING;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    private enum RoofColumnCoverage
    {
        TAGGED,
        COVERED,
        HOLE
    }

    private enum RoofValidationFailure
    {
        NONE,
        HOLE,
        INSUFFICIENT_TAGGED_RATIO
    }

    private record RoofValidationResult(
        RoofValidationFailure failure,
        BlockPos holePosition,
        int taggedColumns,
        int coveredColumns,
        int totalColumns,
        int requiredPercentage)
    {
        /**
         * Create a successful roof validation result when no footprint columns were scanned.
         *
         * @param requiredPercentage configured tagged roof percentage requirement
         * @return successful roof validation result
         */
        private static RoofValidationResult valid(final int requiredPercentage)
        {
            return valid(0, 0, 0, requiredPercentage);
        }

        /**
         * Create a successful roof validation result for a scanned field footprint.
         *
         * @param taggedColumns number of columns containing tagged greenhouse roof blocks
         * @param coveredColumns number of columns covered by tagged or untagged roof-like blocks
         * @param totalColumns number of scanned field footprint columns
         * @param requiredPercentage configured tagged roof percentage requirement
         * @return successful roof validation result
         */
        private static RoofValidationResult valid(
            final int taggedColumns,
            final int coveredColumns,
            final int totalColumns,
            final int requiredPercentage)
        {
            return new RoofValidationResult(
                RoofValidationFailure.NONE,
                null,
                taggedColumns,
                coveredColumns,
                totalColumns,
                requiredPercentage);
        }

        /**
         * Create a failed roof validation result for the first open ceiling hole.
         *
         * @param holePosition field block position whose column has no roof-like block
         * @param taggedColumns number of tagged columns counted before the hole was found
         * @param coveredColumns number of covered columns counted before the hole was found
         * @param totalColumns number of scanned columns including the hole column
         * @param requiredPercentage configured tagged roof percentage requirement
         * @return failed roof validation result identifying the hole position
         */
        private static RoofValidationResult hole(
            final BlockPos holePosition,
            final int taggedColumns,
            final int coveredColumns,
            final int totalColumns,
            final int requiredPercentage)
        {
            return new RoofValidationResult(
                RoofValidationFailure.HOLE,
                holePosition,
                taggedColumns,
                coveredColumns,
                totalColumns,
                requiredPercentage);
        }

        /**
         * Create a failed roof validation result for insufficient tagged roof coverage.
         *
         * @param taggedColumns number of columns containing tagged greenhouse roof blocks
         * @param coveredColumns number of columns covered by tagged or untagged roof-like blocks
         * @param totalColumns number of scanned field footprint columns
         * @param requiredPercentage configured tagged roof percentage requirement
         * @return failed roof validation result containing the measured coverage ratio
         */
        private static RoofValidationResult insufficientRatio(
            final int taggedColumns,
            final int coveredColumns,
            final int totalColumns,
            final int requiredPercentage)
        {
            return new RoofValidationResult(
                RoofValidationFailure.INSUFFICIENT_TAGGED_RATIO,
                null,
                taggedColumns,
                coveredColumns,
                totalColumns,
                requiredPercentage);
        }
    }

    @SuppressWarnings("unchecked")
    public EntityAIWorkHorticulturist(@NotNull JobsHorticulturist job)
    {
        super(job);
        super.registerTargets(
            new AITarget<IAIState>(IDLE, START_WORKING, 2),
            new AITarget<IAIState>(START_WORKING, DECIDE, 2),
            new AITarget<IAIState>(DECIDE, this::decide, 20),
            new AITarget<IAIState>(HorticulturistState.LEDGER_CLIMATE_MATERIAL, this::ledgerClimateMaterial, 10),
            new AITarget<IAIState>(HorticulturistState.VALIDATE_FIELD_ROOF, this::validateFieldRoof, 10),
            new AITarget<IAIState>(HorticulturistState.TRANSFORM_FIELD, this::transformField, 50),
            new AITarget<IAIState>(HorticulturistState.MAINTAIN_FIELD, this::maintainField, 50),
            new AITarget<IAIState>(HorticulturistState.UNSET_FIELD_SEED, this::unsetFieldSeed, 50),
            new AITarget<IAIState>(HorticulturistState.WANDER_IN_BUILDING, this::wanderInBuilding, 20));
        worker.setCanPickUpLoot(true);
    }

    private static void trace(final Runnable loggingStatement)
    {
        TraceUtils.dynamicTrace(ModCommands.TRACE_HORTICULTURIST, loggingStatement);
    }

    /**
     * Find the next greenhouse field whose actual biome area does not match its requested settings.
     *
     * @return the next AI state
     */
    protected IAIState decide()
    {
        final ServerLevel level = serverLevel();
        if (level == null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Horticulturist decide() has no server level; idling."));
            return IDLE;
        }

        clearDecisionState();

        final IAIState ledgerState = nextLedgerState();
        if (ledgerState != null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist decide() selected ledger state {}.", building.getColony().getID(), ledgerState));
            return ledgerState;
        }

        final GreenhouseBiomeModule module = safeBiomeModule();
        final List<FarmField> fields = module.getManagedFields();

        final IAIState conversionState = nextConversionFieldState(level, module, fields);
        if (conversionState != null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist decide() selected conversion state {} for field {}.",
                building.getColony().getID(), conversionState, formatCurrentField()));
            return conversionState;
        }

        job.resetNoGlassCounter();
        job.setBiomeLedgerShortage(false);

        final IAIState maintenanceState = nextMaintenanceFieldState(level, module, fields);
        if (maintenanceState != null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist decide() selected maintenance state {} for field {}.",
                building.getColony().getID(), maintenanceState, formatCurrentField()));
            return maintenanceState;
        }

        final IAIState seedState = nextSeedUnsetState(level, module, fields);
        if (seedState != null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist decide() selected seed clearing for field {}.",
                building.getColony().getID(), formatCurrentField()));
            return seedState;
        }

        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist decide() found no direct work; wandering in building.", building.getColony().getID()));
        return wanderInBuilding();
    }

    /**
     * Clear transient task-selection state before evaluating the next action.
     */
    private void clearDecisionState()
    {
        currentField = null;
        currentFieldIndex = -1;
        currentFieldRange = 0;
        currentRoofInspectionTarget = null;
        currentLedgerTarget = null;
    }

    /**
     * Resolve the next immediate climate ledger action, or request material and allow field scanning to continue.
     *
     * @return next ledgering/gathering state, or null when no immediate ledger action should interrupt field selection
     */
    private IAIState nextLedgerState()
    {
        final ClimateLedgerTarget ledgerTarget = findClimateLedgerTarget();
        if (ledgerTarget == null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist found no climate ledger below top-up levels.", building.getColony().getID()));
            return null;
        }

        currentLedgerTarget = ledgerTarget;
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist selected climate ledger target {}.",
            building.getColony().getID(), formatLedgerTarget(ledgerTarget)));
        final IAIState materialState = materialHandlingState(ledgerTarget);
        if (materialState != null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist climate ledger target {} can proceed with state {}.",
                building.getColony().getID(), formatLedgerTarget(ledgerTarget), materialState));
            return materialState;
        }

        requestClimateMaterial(ledgerTarget);
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist requested climate material for {} and continued field scanning.",
            building.getColony().getID(), formatLedgerTarget(ledgerTarget)));
        return null;
    }

    /**
     * Resolve how the worker should handle an available climate material target.
     *
     * @param target climate material target to consume or gather
     * @return ledgering or gathering state, or null when no matching material is currently available
     */
    private IAIState materialHandlingState(final ClimateLedgerTarget target)
    {
        if (InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), target::matches))
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist has climate material on worker for {}.",
                building.getColony().getID(), formatLedgerTarget(target)));
            return HorticulturistState.LEDGER_CLIMATE_MATERIAL;
        }

        if (!InventoryUtils.hasItemInProvider(building, target::matches))
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist has no available climate material in hut for {}.",
                building.getColony().getID(), formatLedgerTarget(target)));
            return null;
        }

        final BlockPos materialPosition = building.getTileEntity().getPositionOfChestWithItemStack(target::matches);
        if (materialPosition != null && !building.getPosition().equals(materialPosition))
        {
            needsCurrently = new com.minecolonies.api.util.Tuple<>(target::matches, 1);
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist gathering climate material for {} from {}.",
                building.getColony().getID(), formatLedgerTarget(target), formatBlockPos(materialPosition)));
            return GATHERING_REQUIRED_MATERIALS;
        }

        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist will ledger climate material for {} from hut inventory.",
            building.getColony().getID(), formatLedgerTarget(target)));
        return HorticulturistState.LEDGER_CLIMATE_MATERIAL;
    }

    /**
     * Find the next field that needs conversion and can either proceed or trigger immediate material handling.
     *
     * @param level server level containing managed fields
     * @param module biome module holding assignments and field limits
     * @param fields managed farm fields to scan in priority order
     * @return next AI state for conversion work, material handling, or null when no conversion field is actionable
     */
    private IAIState nextConversionFieldState(final ServerLevel level, final GreenhouseBiomeModule module, final List<FarmField> fields)
    {
        final int modifiedBiomeLimit = module.getModifiedBiomeLimit();
        final long colonyDay = building.getColony().getDay();
        int maintainedModifiedBiomes = 0;
        for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++)
        {
            final FarmField field = fields.get(fieldIndex);
            final BlockPos fieldPosition = field == null ? null : field.getPosition();
            if (fieldPosition == null)
            {
                continue;
            }

            final FieldBiomeAssignment assignment = module.getAssignment(fieldPosition);
            final GreenhouseClimate climate = climate(assignment);
            final int fieldRange = horizontalRange(field);
            if (module.isFieldModifiedFromNatural(level, fieldPosition))
            {
                if (maintainedModifiedBiomes >= modifiedBiomeLimit)
                {
                    continue;
                }

                maintainedModifiedBiomes++;
            }

            if (module.wasFieldConversionBlockedOnDay(fieldPosition, colonyDay))
            {
                final String fieldDescription = formatField(fieldIndex, fieldPosition);
                trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist skipped conversion field {} because conversion was already blocked on colony day {}.",
                    building.getColony().getID(), fieldDescription, colonyDay));
                continue;
            }

            if (GreenhouseBiomeOverlayService.needsOverlay(level, fieldPosition, fieldRange, climate))
            {
                final String fieldDescription = formatField(fieldIndex, fieldPosition);
                final ConversionPreflightResult preflight = conversionPreflight(level, module, field, assignment);
                if (preflight.skipField())
                {
                    trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist skipped conversion field {} because shortage restocking was deferred.",
                        building.getColony().getID(), fieldDescription));
                    continue;
                }

                if (preflight.nextState() != null)
                {
                    trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist conversion field {} preflight selected state {}.",
                        building.getColony().getID(), fieldDescription, preflight.nextState()));
                    return preflight.nextState();
                }

                trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist selected conversion field {} with range {}.",
                    building.getColony().getID(), fieldDescription, fieldRange));
                return beginFieldRoofValidation(field, fieldIndex, fieldRange, HorticulturistState.TRANSFORM_FIELD);
            }
        }

        return null;
    }

    /**
     * Find the next modified field that needs its daily maintenance visit.
     *
     * @param level server level containing managed fields
     * @param module biome module holding field tracking state
     * @param fields managed farm fields to scan in priority order
     * @return next AI state for maintenance roof validation, or null when no maintenance field is due
     */
    private IAIState nextMaintenanceFieldState(final ServerLevel level, final GreenhouseBiomeModule module, final List<FarmField> fields)
    {
        final long colonyDay = building.getColony().getDay();
        for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++)
        {
            final FarmField field = fields.get(fieldIndex);
            if (field == null || !needsMaintenanceVisit(level, module, field, colonyDay))
            {
                continue;
            }

            final String fieldDescription = formatField(fieldIndex, field.getPosition());
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist selected maintenance field {} for colony day {}.",
                building.getColony().getID(), fieldDescription, colonyDay));
            return beginFieldRoofValidation(field, fieldIndex, horizontalRange(field), HorticulturistState.MAINTAIN_FIELD);
        }

        return null;
    }

    /**
     * Find the next field whose seed must be cleared because it is invalid for the actual biome.
     *
     * @param level server level containing managed fields
     * @param module biome module used to classify seed validity
     * @param fields managed farm fields to scan in priority order
     * @return next AI state for seed clearing, or null when no seed needs clearing
     */
    private IAIState nextSeedUnsetState(final ServerLevel level, final GreenhouseBiomeModule module, final List<FarmField> fields)
    {
        for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++)
        {
            final FarmField field = fields.get(fieldIndex);
            if (field == null || !module.needsSeedUnsetForActualBiome(level, field))
            {
                continue;
            }

            currentField = field;
            currentFieldIndex = fieldIndex;
            currentFieldRange = horizontalRange(field);
            final String fieldDescription = formatField(fieldIndex, field.getPosition());
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist selected seed clearing field {}.",
                building.getColony().getID(), fieldDescription));
            return HorticulturistState.UNSET_FIELD_SEED;
        }

        return null;
    }

    /**
     * Initialize common field state and send the worker to inspect the field roof.
     *
     * @param field selected field
     * @param fieldIndex selected field index in the managed field list
     * @param fieldRange selected field horizontal range
     * @param successState state to enter after successful roof validation
     * @return roof validation AI state
     */
    private IAIState beginFieldRoofValidation(
        final FarmField field,
        final int fieldIndex,
        final int fieldRange,
        final IAIState successState)
    {
        currentField = field;
        currentFieldIndex = fieldIndex;
        currentFieldRange = fieldRange;
        roofValidationSuccessState = successState;
        startRoofValidation(field);
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist beginning roof validation for field {} range {} then state {}.",
            building.getColony().getID(), formatCurrentField(), fieldRange, successState));
        return HorticulturistState.VALIDATE_FIELD_ROOF;
    }

    /**
     * Check whether a conversion field is ready, needs immediate material handling, or should be skipped for now.
     *
     * @param level server level containing the field
     * @param module biome module holding field assignments
     * @param field field that needs biome overlay work
     * @param assignment requested field climate assignment
     * @return conversion preflight result describing the next action
     */
    private ConversionPreflightResult conversionPreflight(
        final ServerLevel level,
        final GreenhouseBiomeModule module,
        final FarmField field,
        final FieldBiomeAssignment assignment)
    {
        final BiomeConversionCost conversionCost = biomeConversionCost(level, field, assignment, module);
        if (ledgerShortage(conversionCost, safeTemperatureModule(), safeHumidityModule()).isBlank())
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist conversion preflight ready for field {} with cost {}.",
                building.getColony().getID(), formatBlockPos(field.getPosition()), conversionCost));
            return ConversionPreflightResult.ready();
        }

        final IAIState restockState = restockShortageLedger(conversionCost, safeTemperatureModule(), safeHumidityModule());
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist conversion preflight shortage for field {}; restock state {}.",
            building.getColony().getID(), formatBlockPos(field.getPosition()), restockState));
        return restockState == DECIDE ? ConversionPreflightResult.skippedField() : ConversionPreflightResult.handle(restockState);
    }

    /**
     * Walk back to the greenhouse hut and convert a carried climate material stack into ledger balance.
     *
     * @return the next AI state
     */
    @SuppressWarnings("null")
    protected IAIState ledgerClimateMaterial()
    {
        if (currentLedgerTarget == null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist ledgerClimateMaterial() has no current target.", building.getColony().getID()));
            return DECIDE;
        }

        if (!walkToBuildingWithSkillSpeed())
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist walking to hut to ledger {}.",
                building.getColony().getID(), formatLedgerTarget(currentLedgerTarget)));
            return HorticulturistState.LEDGER_CLIMATE_MATERIAL;
        }

        if (!currentLedgerTarget.module().isLedgerUnderTarget(currentLedgerTarget.list(), currentLedgerTarget.targetBalance()))
        {
            currentLedgerTarget = null;
            needsCurrently = null;
            return DECIDE;
        }

        if (!InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), currentLedgerTarget::matches)
            && !transferClimateMaterialFromBuildingToWorker())
        {
            currentLedgerTarget = null;
            needsCurrently = null;
            worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return DECIDE;
        }

        final int slot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(), currentLedgerTarget::matches);
        if (slot < 0)
        {
            currentLedgerTarget = null;
            needsCurrently = null;
            worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return DECIDE;
        }

        final ItemStack heldStack = worker.getInventoryCitizen().getStackInSlot(slot).copyWithCount(1);
        worker.setItemInHand(InteractionHand.MAIN_HAND, heldStack);
        CitizenItemUtils.hitBlockWithToolInHand(worker, building.getPosition());

        final ItemStack extracted = worker.getInventoryCitizen().extractItem(slot, 1, false);
        final int ledgered = currentLedgerTarget.module().ledgerStack(currentLedgerTarget.list(), extracted, currentLedgerTarget.targetBalance());
        if (ledgered > 0)
        {
            returnClimateMaterialRemainder(extracted);
            incrementActionsDone();
            worker.getCitizenExperienceHandler().addExperience(.2);
            StatsUtil.trackStatByName(building, StatisticsConstants.ITEM_USED, extracted.getItem().getDescriptionId(), extracted.getCount());
            building.markDirty();
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist ledgered {} for {}.",
                building.getColony().getID(), extracted, formatLedgerTarget(currentLedgerTarget)));
        }

        if (ledgered > 0
            && currentLedgerTarget.module().isLedgerUnderTarget(currentLedgerTarget.list(), currentLedgerTarget.targetBalance())
            && (InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), currentLedgerTarget::matches)
                || InventoryUtils.hasItemInProvider(building, currentLedgerTarget::matches)))
        {
            return HorticulturistState.LEDGER_CLIMATE_MATERIAL;
        }

        currentLedgerTarget = null;
        needsCurrently = null;
        worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return DECIDE;
    }

    /**
     * Return any empty container produced by a consumed climate material to the greenhouse inventory.
     *
     * @param consumed stack consumed by the climate material ledger
     */
    @SuppressWarnings("null")
    private void returnClimateMaterialRemainder(final ItemStack consumed)
    {
        ItemStack remainder = GreenhouseClimateRemainderListener.INSTANCE.getRemainder(consumed);
        if (remainder.isEmpty())
        {
            return;
        }

        remainder = InventoryUtils.addItemStackToProviderWithResult(building, remainder);
        if (!remainder.isEmpty())
        {
            remainder = InventoryUtils.addItemStackToItemHandlerWithResult(worker.getInventoryCitizen(), remainder);
        }

        if (!remainder.isEmpty())
        {
            Containers.dropItemStack(
                worker.level(),
                building.getPosition().getX() + 0.5D,
                building.getPosition().getY() + 1.0D,
                building.getPosition().getZ() + 0.5D,
                remainder);
        }
    }

    /**
     * Walk the corners of the selected field, then validate its complete roof footprint.
     *
     * @return the next AI state
     */
    protected IAIState validateFieldRoof()
    {
        final ServerLevel level = serverLevel();
        if (level == null || currentField == null || currentRoofInspectionTarget == null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist roof validation lost context; level present? {}, field {}, target {}.",
                building.getColony().getID(), level != null, formatCurrentField(), currentRoofInspectionTarget));
            resetCurrentField();
            return DECIDE;
        }

        if (!walkToSafePosWithSkillSpeed(currentRoofInspectionTarget))
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist walking to roof inspection target {} for field {}.",
                building.getColony().getID(), formatBlockPos(currentRoofInspectionTarget), formatCurrentField()));
            return HorticulturistState.VALIDATE_FIELD_ROOF;
        }

        if (advanceRoofInspectionTarget())
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist advanced to roof inspection target {} for field {}.",
                building.getColony().getID(), formatBlockPos(currentRoofInspectionTarget), formatCurrentField()));
            return HorticulturistState.VALIDATE_FIELD_ROOF;
        }

        final RoofValidationResult roofValidation = validateGreenhouseRoof(level, currentField);
        if (roofValidation.failure() != RoofValidationFailure.NONE)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist roof validation failed for field {}: {}.",
                building.getColony().getID(), formatCurrentField(), formatRoofValidation(roofValidation)));
            handleRoofValidationFailure(level, roofValidation);
            resetCurrentField();
            return DECIDE;
        }

        job.resetNoGlassCounter();
        final IAIState nextState = roofValidationSuccessState;
        roofValidationSuccessState = DECIDE;
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist roof validation passed for field {}; next state {}.",
            building.getColony().getID(), formatCurrentField(), nextState));
        return nextState == null ? DECIDE : nextState;
    }

    /**
     * Record and report a roof validation failure for conversion or daily maintenance.
     *
     * @param level server level containing the field
     * @param roofValidation failed roof validation result
     */
    private void handleRoofValidationFailure(final ServerLevel level, final RoofValidationResult roofValidation)
    {
        if (roofValidationSuccessState == HorticulturistState.MAINTAIN_FIELD && currentField != null)
        {
            final GreenhouseBiomeModule module = safeBiomeModule();
            final BlockPos fieldPosition = currentField.getPosition();
            final long colonyDay = building.getColony().getDay();
            final long firstMissedDay = module.recordFieldMaintenanceMissed(fieldPosition, colonyDay);
            job.setBiomeLedgerShortage(false);
            triggerRoofInteraction(maintenanceRoofFailureMessage(fieldPosition, roofValidation), roofFailureInteractionKey(roofValidation, true));

            if (colonyDay - firstMissedDay >= Config.maintenanceRevertDays.get())
            {
                module.revertFieldToNaturalBiome(fieldPosition);
                trackSeedCleared(module.clearInvalidSeedForActualBiome(level, currentField));
                building.markDirty();
                trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist reverted field {} to natural biome after missed maintenance since day {}.",
                    building.getColony().getID(), formatBlockPos(fieldPosition), firstMissedDay));
            }

            return;
        }

        if (roofValidationSuccessState == HorticulturistState.TRANSFORM_FIELD && currentField != null)
        {
            final GreenhouseBiomeModule module = safeBiomeModule();
            final BlockPos fieldPosition = currentField.getPosition();
            final long colonyDay = building.getColony().getDay();
            module.recordFieldConversionBlocked(fieldPosition, colonyDay);
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist blocked conversion field {} for colony day {} after roof validation failure.",
                building.getColony().getID(), formatBlockPos(fieldPosition), colonyDay));
        }

        triggerRoofInteraction(conversionRoofFailureMessage(roofValidation), roofFailureInteractionKey(roofValidation, false));
    }

    /**
     * Show the roof repair interaction after enough repeated roof validation failures.
     *
     * @param message player-facing interaction message
     * @param translationKey interaction translation key
     */
    private void triggerRoofInteraction(final Component message, final @Nonnull String translationKey)
    {
        job.tickNoGlass();
        if (!job.checkNoGlass())
        {
            return;
        }

        worker.getCitizenData().triggerInteraction(new StandardInteraction(
            message,
            Component.translatable(translationKey),
            ChatPriority.BLOCKING));
    }

    /**
     * Walk to the selected field and apply the configured biome overlay.
     *
     * @return the next AI state
     */
    protected IAIState transformField()
    {
        final ServerLevel level = serverLevel();
        if (level == null || currentField == null || currentFieldIndex < 0)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist transformField() missing context; level present? {}, field {}, index {}.",
                building.getColony().getID(), level != null, formatCurrentField(), currentFieldIndex));
            return DECIDE;
        }

        final BlockPos fieldPosition = currentField.getPosition();
        if (!walkToSafePosWithSkillSpeed(fieldPosition))
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist walking to transform field {}.",
                building.getColony().getID(), formatCurrentField()));
            return HorticulturistState.TRANSFORM_FIELD;
        }

        final GreenhouseBiomeModule module = safeBiomeModule();
        final FieldBiomeAssignment assignment = module.getAssignment(fieldPosition);
        final BiomeConversionCost conversionCost = biomeConversionCost(level, currentField, assignment, module);
        final GreenhouseTemperatureModule temperatureModule = safeTemperatureModule();
        final GreenhouseHumidityModule humidityModule = safeHumidityModule();
        final String shortage = ledgerShortage(conversionCost, temperatureModule, humidityModule);
        if (!shortage.isBlank())
        {
            final IAIState restockState = restockShortageLedger(conversionCost, temperatureModule, humidityModule);
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist transform field {} shortage {}; restock state {}.",
                building.getColony().getID(), formatCurrentField(), shortage, restockState));
            if (restockState != null)
            {
                resetCurrentField();
                return restockState;
            }

            job.setBiomeLedgerShortage(true);
            module.recordFieldVisited(fieldPosition, building.getColony().getDay());
            worker.getCitizenData().triggerInteraction(new StandardInteraction(
                Component.translatable(InteractionInitializer.GREENHOUSE_BIOME_LEDGER_SHORTAGE, formatBlockPos(fieldPosition), shortage),
                Component.translatable(InteractionInitializer.GREENHOUSE_BIOME_LEDGER_SHORTAGE),
                ChatPriority.BLOCKING));
            resetCurrentField();
            return DECIDE;
        }

        job.setBiomeLedgerShortage(false);

        final OverlayResult result = GreenhouseBiomeOverlayService.applyOverlay(
            level,
            fieldPosition,
            currentFieldRange,
            climate(assignment),
            module.getNaturalBiomes(),
            module.getAppliedBiomes());

        if (result.changedCells() > 0)
        {
            debitConversionLedgers(conversionCost, temperatureModule, humidityModule);
            module.recordFieldConverted(fieldPosition, building.getColony().getDay());
            announceFieldTransformed(level, fieldPosition, currentFieldRange, assignment);
            incrementActionsDone();
            worker.getCitizenExperienceHandler().addExperience(BASE_BIOME_TRANSFORM_XP + (double) result.changedCells() / BIOME_CELLS_PER_BONUS_XP);
            StatsUtil.trackStat(building, FIELDS_TRANSFORMED_STAT, 1);
            AdvancementUtils.TriggerAdvancementPlayersForColony(building.getColony(), AdvancementTriggers.FIELD_BIOME_MODIFIED.get()::trigger);
            module.markDirty();
            building.markDirty();
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist transformed field {} with {} changed biome cells and cost {}.",
                building.getColony().getID(), formatCurrentField(), result.changedCells(), conversionCost));
        }
        final boolean seedCleared = module.clearInvalidSeedForActualBiome(level, currentField);
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist transform cleanup for field {} cleared invalid seed? {}.",
            building.getColony().getID(), formatCurrentField(), seedCleared));
        trackSeedCleared(seedCleared);

        resetCurrentField();
        return DECIDE;
    }

    /**
     * Walk to the selected field and pay its daily biome maintenance cost.
     *
     * @return the next AI state
     */
    protected IAIState maintainField()
    {
        final ServerLevel level = serverLevel();
        if (level == null || currentField == null || currentFieldIndex < 0)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist maintainField() missing context; level present? {}, field {}, index {}.",
                building.getColony().getID(), level != null, formatCurrentField(), currentFieldIndex));
            return DECIDE;
        }

        final BlockPos fieldPosition = currentField.getPosition();
        if (!walkToSafePosWithSkillSpeed(fieldPosition))
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist walking to maintain field {}.",
                building.getColony().getID(), formatCurrentField()));
            return HorticulturistState.MAINTAIN_FIELD;
        }

        final long colonyDay = building.getColony().getDay();
        final GreenhouseBiomeModule module = safeBiomeModule();
        final FieldBiomeAssignment assignment = module.getAssignment(fieldPosition);
        final BiomeConversionCost maintenanceCost = biomeMaintenanceCost(level, currentField, assignment, module, maintenanceDiscount());
        final GreenhouseTemperatureModule temperatureModule = safeTemperatureModule();
        final GreenhouseHumidityModule humidityModule = safeHumidityModule();
        final String shortage = ledgerShortage(maintenanceCost, temperatureModule, humidityModule);
        if (!shortage.isBlank())
        {
            final IAIState restockState = restockShortageLedger(maintenanceCost, temperatureModule, humidityModule);
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist maintenance field {} shortage {}; restock state {}.",
                building.getColony().getID(), formatCurrentField(), shortage, restockState));
            if (restockState != null)
            {
                resetCurrentField();
                return restockState;
            }

            final long firstMissedDay = module.recordFieldMaintenanceMissed(fieldPosition, colonyDay);
            job.setBiomeLedgerShortage(true);
            worker.getCitizenData().triggerInteraction(new StandardInteraction(
                Component.translatable(InteractionInitializer.GREENHOUSE_BIOME_MAINTENANCE_SHORTAGE, formatBlockPos(fieldPosition), shortage),
                Component.translatable(InteractionInitializer.GREENHOUSE_BIOME_MAINTENANCE_SHORTAGE),
                ChatPriority.BLOCKING));

            if (colonyDay - firstMissedDay >= Config.maintenanceRevertDays.get())
            {
                module.revertFieldToNaturalBiome(fieldPosition);
                trackSeedCleared(module.clearInvalidSeedForActualBiome(level, currentField));
                building.markDirty();
                trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist reverted field {} to natural biome after maintenance shortage since day {}.",
                    building.getColony().getID(), formatBlockPos(fieldPosition), firstMissedDay));
            }

            resetCurrentField();
            return DECIDE;
        }

        debitConversionLedgers(maintenanceCost, temperatureModule, humidityModule);
        module.recordFieldMaintained(fieldPosition, colonyDay);
        job.setBiomeLedgerShortage(false);
        incrementActionsDone();
        worker.getCitizenExperienceHandler().addExperience(BASE_BIOME_MAINTENANCE_XP);
        StatsUtil.trackStat(building, FIELDS_MAINTAINED_STAT, 1);
        module.markDirty();
        building.markDirty();
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist maintained field {} for day {} with cost {}.",
            building.getColony().getID(), formatCurrentField(), colonyDay, maintenanceCost));

        resetCurrentField();
        return DECIDE;
    }

    /**
     * Walk to the selected field and clear its seed when the current biome cannot support it.
     *
     * @return the next AI state
     */
    protected IAIState unsetFieldSeed()
    {
        final ServerLevel level = serverLevel();
        if (level == null || currentField == null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist unsetFieldSeed() missing context; level present? {}, field {}.",
                building.getColony().getID(), level != null, formatCurrentField()));
            return DECIDE;
        }

        final BlockPos fieldPosition = currentField.getPosition();
        if (!walkToSafePosWithSkillSpeed(fieldPosition))
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist walking to clear seed for field {}.",
                building.getColony().getID(), formatCurrentField()));
            return HorticulturistState.UNSET_FIELD_SEED;
        }

        final boolean seedCleared = safeBiomeModule().clearInvalidSeedForActualBiome(level, currentField);
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist cleared invalid seed for field {}? {}.",
            building.getColony().getID(), formatCurrentField(), seedCleared));
        trackSeedCleared(seedCleared);

        resetCurrentField();
        return DECIDE;
    }

    /**
     * Wander to a random location inside the greenhouse when no direct work is pending.
     *
     * @return the next AI state
     */
    protected IAIState wanderInBuilding()
    {
        if (!EntityNavigationUtils.walkToRandomPosAround(worker, building.getPosition(), 10, .8))
        {
            return HorticulturistState.WANDER_IN_BUILDING;
        }

        return DECIDE;
    }

    @Override
    public IAIState getStateAfterPickUp()
    {
        if (currentLedgerTarget != null && InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), currentLedgerTarget::matches))
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist picked up material and will ledger {}.",
                building.getColony().getID(), formatLedgerTarget(currentLedgerTarget)));
            return HorticulturistState.LEDGER_CLIMATE_MATERIAL;
        }

        if (currentLedgerTarget != null && InventoryUtils.hasItemInProvider(building, currentLedgerTarget::matches))
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist pickup ended but hut still has material for {}; ledgering.",
                building.getColony().getID(), formatLedgerTarget(currentLedgerTarget)));
            return HorticulturistState.LEDGER_CLIMATE_MATERIAL;
        }

        currentLedgerTarget = null;
        needsCurrently = null;
        return super.getStateAfterPickUp();
    }

    /**
     * Move one climate material from the greenhouse inventory into the worker before ledgering it.
     *
     * @return true when the worker now carries a matching material
     */
    private boolean transferClimateMaterialFromBuildingToWorker()
    {
        if (currentLedgerTarget == null)
        {
            return false;
        }

        InventoryUtils.transferXOfFirstSlotInProviderWithIntoNextFreeSlotInItemHandlerWithResult(
            building,
            currentLedgerTarget::matches,
            1,
            worker.getInventoryCitizen());

        return InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), currentLedgerTarget::matches);
    }

    /**
     * Try to stock a ledger up to the amount required by a pending conversion or maintenance cost.
     *
     * @param cost required climate costs
     * @param temperatureModule temperature ledger module
     * @param humidityModule humidity ledger module
     * @return next state while stocking can proceed or wait, otherwise null to report the shortage
     */
    private IAIState restockShortageLedger(
        final BiomeConversionCost cost,
        final GreenhouseTemperatureModule temperatureModule,
        final GreenhouseHumidityModule humidityModule)
    {
        final ClimateLedgerTarget target = findShortageLedgerTarget(cost, temperatureModule, humidityModule);
        if (target == null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist found no selected material that can restock cost {}.",
                building.getColony().getID(), cost));
            return null;
        }

        currentLedgerTarget = target;
        job.setBiomeLedgerShortage(false);
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist restocking shortage cost {} with target {}.",
            building.getColony().getID(), cost, formatLedgerTarget(target)));

        final IAIState materialState = materialHandlingState(target);
        if (materialState != null)
        {
            return materialState;
        }

        requestClimateMaterial(target);
        return DECIDE;
    }

    /**
     * Find the first selected material that can feed a ledger whose balance is below a required cost.
     *
     * @param cost required climate costs
     * @param temperatureModule temperature ledger module
     * @param humidityModule humidity ledger module
     * @return target material and required balance, or null when no selected material can satisfy a shortage
     */
    private ClimateLedgerTarget findShortageLedgerTarget(
        final BiomeConversionCost cost,
        final GreenhouseTemperatureModule temperatureModule,
        final GreenhouseHumidityModule humidityModule)
    {
        ClimateLedgerTarget target = shortageLedgerTarget(temperatureModule, ClimateItemList.INCREASE, cost.hot());
        if (target != null)
        {
            return target;
        }

        target = shortageLedgerTarget(temperatureModule, ClimateItemList.DECREASE, cost.cold());
        if (target != null)
        {
            return target;
        }

        target = shortageLedgerTarget(humidityModule, ClimateItemList.INCREASE, cost.humid());
        if (target != null)
        {
            return target;
        }

        return shortageLedgerTarget(humidityModule, ClimateItemList.DECREASE, cost.dry());
    }

    /**
     * Build a demand-aware ledger target for one climate material list.
     *
     * @param module climate item module
     * @param list increase or decrease list
     * @param requiredBalance required ledger balance
     * @return selected material target, or null when the list is already sufficient or has no valid selection
     */
    private ClimateLedgerTarget shortageLedgerTarget(
        final GreenhouseClimateItemModule module,
        final ClimateItemList list,
        final int requiredBalance)
    {
        if (!module.isLedgerUnderTarget(list, requiredBalance))
        {
            return null;
        }

        for (final ItemStorage item : module.getItems(list))
        {
            final ClimateLedgerTarget target = climateLedgerTarget(module, list, item, requiredBalance);
            if (target != null)
            {
                return target;
            }
        }

        return null;
    }

    /**
     * Find the next selected climate item whose ledger is below its configured limit.
     *
     * @return target climate material to fetch or consume, or null when no ledger needs topping up
     */
    private ClimateLedgerTarget findClimateLedgerTarget()
    {
        ClimateLedgerTarget requestableTarget = null;
        for (final GreenhouseClimateItemModule module : List.of(safeTemperatureModule(), safeHumidityModule()))
        {
            for (final ClimateItemList list : ClimateItemList.values())
            {
                final int targetBalance = module.getLedgerLimit(list);
                if (!module.isLedgerUnderTarget(list, targetBalance))
                {
                    continue;
                }

                for (final ItemStorage item : module.getItems(list))
                {
                    final ClimateLedgerTarget target = climateLedgerTarget(module, list, item, targetBalance);
                    if (target == null)
                    {
                        continue;
                    }

                    if (InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), target::matches)
                        || InventoryUtils.hasItemInProvider(building, target::matches))
                    {
                        return target;
                    }

                    if (requestableTarget == null && !hasClimateMaterialRequestOutstanding(target) && !hasUnprocessedClimateMaterialInHut(target))
                    {
                        requestableTarget = target;
                    }
                }
            }
        }

        return requestableTarget;
    }

    /**
     * Request the selected climate material through the colony request system.
     *
     * @param target material target to request
     */
    private void requestClimateMaterial(final ClimateLedgerTarget target)
    {
        final boolean outstandingRequest = hasClimateMaterialRequestOutstanding(target);
        final boolean materialInHut = hasUnprocessedClimateMaterialInHut(target);
        if (outstandingRequest || materialInHut)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist suppressed climate material request for {}; outstanding request? {}, material in hut? {}.",
                building.getColony().getID(), formatLedgerTarget(target), outstandingRequest, materialInHut));
            return;
        }

        worker.getCitizenData().createRequestAsync(new StackList(
            List.of(target.requestStack()),
            target.requestDescription(),
            target.requestCount(),
            1,
            target.protectedQuantity()));
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist created climate material request for {}.",
            building.getColony().getID(), formatLedgerTarget(target)));
    }

    /**
     * Build a climate ledger target and cap requests to the nearest warehouse surplus over the protected stack count.
     *
     * @param module climate item module
     * @param list increase or decrease list
     * @param item selected material and protected stack count
     * @param targetBalance desired ledger balance
     * @return requestable material target, or null when the item has no surplus available
     */
    private ClimateLedgerTarget climateLedgerTarget(
        final GreenhouseClimateItemModule module,
        final ClimateItemList list,
        final ItemStorage item,
        final int targetBalance)
    {
        final ItemStack stack = item.getItemStack();
        int requestCount = module.getLedgerRequestCount(list, stack, targetBalance);
        if (requestCount <= 0)
        {
            return null;
        }

        final int protectedStacks = Math.max(0, item.getAmount());
        final int protectedQuantity = protectedItemCount(stack, protectedStacks);
        final OptionalInt warehouseQuantity = closestWarehouseQuantity(stack);
        if (warehouseQuantity.isPresent())
        {
            final int surplus = warehouseQuantity.getAsInt() - protectedQuantity;
            if (surplus <= 0)
            {
                trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist skipped climate material {} because closest warehouse has {} and protects {}.",
                    building.getColony().getID(), stack, warehouseQuantity.getAsInt(), protectedQuantity));
                return null;
            }

            requestCount = Math.min(requestCount, surplus);
        }

        return new ClimateLedgerTarget(module, list, stack.copy(), requestCount, protectedStacks, protectedQuantity, targetBalance);
    }

    /**
     * Count matching material in the nearest warehouse, if the colony has one.
     *
     * @param stack selected material to count
     * @return matching item count, or empty when no warehouse exists
     */
    private OptionalInt closestWarehouseQuantity(final ItemStack stack)
    {
        final IWareHouse warehouse = building.getColony().getServerBuildingManager().getClosestWarehouseInColony(building.getPosition());
        if (warehouse == null)
        {
            return OptionalInt.empty();
        }

        return OptionalInt.of(InventoryUtils.getCountFromBuilding(
            warehouse,
            candidate -> !candidate.isEmpty() && ItemStackUtils.compareItemStacksIgnoreStackSize(candidate, stack, true, true)));
    }

    /**
     * Convert the UI's protected stack count to the request system's item count.
     *
     * @param stack selected material stack
     * @param protectedStacks number of stacks to protect
     * @return protected item count
     */
    private static int protectedItemCount(final ItemStack stack, final int protectedStacks)
    {
        final long protectedItems = (long) Math.max(0, protectedStacks) * (long) stack.getMaxStackSize();
        return protectedItems > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) protectedItems;
    }

    /**
     * Check whether this worker already has an unresolved request for this climate modification type.
     *
     * @param target material target to compare against open or completed requests
     * @return true when a matching climate material request is already pending pickup or delivery
     */
    private boolean hasClimateMaterialRequestOutstanding(final ClimateLedgerTarget target)
    {
        return building.hasWorkerOpenRequestsFiltered(worker.getCitizenData().getId(), request -> isClimateMaterialRequest(request, target))
            || building.getCompletedRequestsOfCitizenOrBuilding(worker.getCitizenData()).stream().anyMatch(request -> isClimateMaterialRequest(request, target));
    }

    /**
     * Check whether the greenhouse hut already holds unprocessed material for this climate modification type.
     *
     * @param target material target whose climate type should be checked
     * @return true when the hut inventory contains any tagged material for the target type
     */
    private boolean hasUnprocessedClimateMaterialInHut(final ClimateLedgerTarget target)
    {
        return InventoryUtils.hasItemInProvider(
            building,
            stack -> !stack.isEmpty()
                && stack.is(target.module().getAllowedTag(target.list()))
                && GreenhouseClimateItemModule.climateModificationUnit(stack) > 0);
    }

    /**
     * Check whether a request belongs to a target climate modification type.
     *
     * @param request colony request to inspect
     * @param target climate material target
     * @return true when the request description matches the target climate type
     */
    private static boolean isClimateMaterialRequest(final IRequest<?> request, final ClimateLedgerTarget target)
    {
        final IRequestable requestable = request.getRequest();
        return requestable instanceof StackList stackList && target.requestDescription().equals(stackList.getDescription());
    }

    /**
     * Resolve the colony world as a server level.
     *
     * @return the server level for this worker, or null when unavailable or client-side
     */
    private ServerLevel serverLevel()
    {
        final Level level = building.getColony().getWorld();
        if (level instanceof ServerLevel serverLevel && !serverLevel.isClientSide())
        {
            return serverLevel;
        }

        return null;
    }

    /**
     * Retrieve the greenhouse biome module required by this AI.
     *
     * @return the greenhouse biome module
     * @throws IllegalStateException when the building is missing its biome module
     */
    private GreenhouseBiomeModule safeBiomeModule()
    {
        final GreenhouseBiomeModule module = building.getModule(GreenhouseBiomeModule.class, candidate -> true);
        if (module == null)
        {
            throw new IllegalStateException("Greenhouse biome module not found in greenhouse building.");
        }

        return module;
    }

    /**
     * Retrieve the greenhouse temperature module required by the climate ledger.
     *
     * @return the greenhouse temperature module
     */
    private GreenhouseTemperatureModule safeTemperatureModule()
    {
        final GreenhouseTemperatureModule module = building.getModule(GreenhouseTemperatureModule.class, candidate -> true);
        if (module == null)
        {
            throw new IllegalStateException("Greenhouse temperature module not found in greenhouse building.");
        }

        return module;
    }

    /**
     * Retrieve the greenhouse humidity module required by the climate ledger.
     *
     * @return the greenhouse humidity module
     */
    private GreenhouseHumidityModule safeHumidityModule()
    {
        final GreenhouseHumidityModule module = building.getModule(GreenhouseHumidityModule.class, candidate -> true);
        if (module == null)
        {
            throw new IllegalStateException("Greenhouse humidity module not found in greenhouse building.");
        }

        return module;
    }

    /**
     * Get the fractional maintenance discount unlocked by efficient conversion research.
     *
     * @return discount from 0.0 to 1.0
     */
    private double maintenanceDiscount()
    {
        if (building == null || building.getColony() == null)
        {
            return 0.0D;
        }

        return building.getColony().getResearchManager().getResearchEffects().getEffectStrength(ModResearch.RESEARCH_EFFICIENT_CONVERSION);
    }

    /**
     * Check whether an owned field should receive its one maintenance visit for a colony day.
     *
     * @param level the server level containing the field
     * @param module biome module holding field tracking state
     * @param field field to inspect
     * @param colonyDay current colony day
     * @return true when this field is modified, not converted today, and not already visited today
     */
    private static boolean needsMaintenanceVisit(
        final ServerLevel level,
        final GreenhouseBiomeModule module,
        final FarmField field,
        final long colonyDay)
    {
        final BlockPos fieldPosition = field.getPosition();
        return fieldPosition != null
            && module.isFieldModifiedFromNatural(level, fieldPosition)
            && !module.wasFieldConvertedOnDay(fieldPosition, colonyDay)
            && !module.wasFieldVisitedOnDay(fieldPosition, colonyDay);
    }

    /**
     * Convert a field biome assignment into shared greenhouse climate settings.
     *
     * @param assignment the field's requested temperature and humidity assignment
     * @return the shared greenhouse climate settings
     */
    private static GreenhouseClimate climate(final FieldBiomeAssignment assignment)
    {
        return new GreenhouseClimate(assignment.temperature(), assignment.humidity());
    }

    /**
     * Calculate ledger costs for converting every X/Z block in a field away from its native biome climate.
     *
     * @param level the server level containing the field
     * @param field the field to convert
     * @param assignment requested field climate
     * @param module biome module holding natural biome captures
     * @return aggregated conversion costs by climate ledger direction
     */
    private BiomeConversionCost biomeConversionCost(
        final ServerLevel level,
        final FarmField field,
        final FieldBiomeAssignment assignment,
        final GreenhouseBiomeModule module)
    {
        return biomeClimateCost(level, field, assignment, module, Config.baseConversionCost.get(), primarySkillCostMultiplier(), 0.0D);
    }

    /**
     * Calculate ledger costs for maintaining every X/Z block in a field away from its native biome climate.
     *
     * @param level the server level containing the field
     * @param field the field to maintain
     * @param assignment requested field climate
     * @param module biome module holding natural biome captures
     * @return aggregated maintenance costs by climate ledger direction
     */
    private BiomeConversionCost biomeMaintenanceCost(
        final ServerLevel level,
        final FarmField field,
        final FieldBiomeAssignment assignment,
        final GreenhouseBiomeModule module,
        final double discount)
    {
        return biomeClimateCost(level, field, assignment, module, Config.baseMaintenanceCost.get(), primarySkillCostMultiplier(), discount);
    }

    /**
     * Calculate ledger costs for every X/Z block in a field away from its native biome climate.
     *
     * @param level the server level containing the field
     * @param field the field to price
     * @param assignment requested field climate
     * @param module biome module holding natural biome captures
     * @param unitCost configured cost per rounded group of step-weighted cells
     * @return aggregated costs by climate ledger direction
     */
    private static BiomeConversionCost biomeClimateCost(
        final ServerLevel level,
        final FarmField field,
        final FieldBiomeAssignment assignment,
        final GreenhouseBiomeModule module,
        final int unitCost,
        final double skillMultiplier,
        final double discount)
    {
        final BlockPos center = field.getPosition();
        if (center == null)
        {
            return BiomeConversionCost.NONE;
        }

        int hotStepCells = 0;
        int coldStepCells = 0;
        int humidStepCells = 0;
        int dryStepCells = 0;
        final int minX = center.getX() - field.getRadius(Direction.WEST);
        final int maxX = center.getX() + field.getRadius(Direction.EAST);
        final int minZ = center.getZ() - field.getRadius(Direction.NORTH);
        final int maxZ = center.getZ() + field.getRadius(Direction.SOUTH);

        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                final GreenhouseClimate nativeClimate = nativeClimate(level, module, new BlockPos(x, center.getY(), z));
                final int temperatureSteps = assignment.temperature().ordinal() - nativeClimate.temperature().ordinal();
                final int humiditySteps = assignment.humidity().ordinal() - nativeClimate.humidity().ordinal();

                if (temperatureSteps > 0)
                {
                    hotStepCells += temperatureSteps;
                }
                else
                {
                    coldStepCells += -temperatureSteps;
                }

                if (humiditySteps > 0)
                {
                    humidStepCells += humiditySteps;
                }
                else
                {
                    dryStepCells += -humiditySteps;
                }
            }
        }

        return new BiomeConversionCost(
            climateCost(hotStepCells, unitCost, skillMultiplier, discount),
            climateCost(coldStepCells, unitCost, skillMultiplier, discount),
            climateCost(humidStepCells, unitCost, skillMultiplier, discount),
            climateCost(dryStepCells, unitCost, skillMultiplier, discount));
    }

    /**
     * Scale conversion and maintenance costs by the worker's primary job skill.
     *
     * @return cost multiplier applied before research discounts
     */
    private double primarySkillCostMultiplier()
    {
        if (worker == null || worker.getCitizenData() == null)
        {
            return 1.0D;
        }

        final int primarySkill = Math.max(0, Math.min(MAX_PRIMARY_SKILL,
            worker.getCitizenData().getCitizenSkillHandler().getLevel(getModuleForJob().getPrimarySkill())));
        final double discountPerSkill = MAX_PRIMARY_SKILL_COST_DISCOUNT / (MAX_PRIMARY_SKILL - NORMAL_PRIMARY_SKILL);
        return 1.0D - ((primarySkill - NORMAL_PRIMARY_SKILL) * discountPerSkill);
    }

    /**
     * Walk to the greenhouse building with the secondary-skill speed bonus.
     *
     * @return true when the worker has arrived
     */
    private boolean walkToBuildingWithSkillSpeed()
    {
        if (building == null)
        {
            return true;
        }

        return walkToPosInBuildingWithSkillSpeed(building.getPosition(), EntityNavigationUtils.BUILDING_REACH_DIST);
    }

    /**
     * Walk to a safe position with the secondary-skill speed bonus.
     *
     * @param pos target position
     * @return true when the worker has arrived
     */
    private boolean walkToSafePosWithSkillSpeed(final BlockPos pos)
    {
        return EntityNavigationUtils.walkToPos(worker, pos, 4, true, secondarySkillSpeedMultiplier());
    }

    /**
     * Walk within the greenhouse building with the secondary-skill speed bonus.
     *
     * @param pos target position
     * @param reachDistance distance considered close enough
     * @return true when the worker has arrived
     */
    private boolean walkToPosInBuildingWithSkillSpeed(final BlockPos pos, final int reachDistance)
    {
        if (building == null)
        {
            return walkToSafePosWithSkillSpeed(pos);
        }

        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        final BlockPos center = new BlockPos(
            (corners.getA().getX() + corners.getB().getX()) / 2,
            building.getPosition().getY(),
            (corners.getA().getZ() + corners.getB().getZ()) / 2);
        return EntityNavigationUtils.walkCloseToXNearY(worker, pos, center, reachDistance, true, secondarySkillSpeedMultiplier());
    }

    /**
     * Scale walking speed by the worker's secondary job skill, with upside only above normal.
     *
     * @return navigation speed multiplier
     */
    private double secondarySkillSpeedMultiplier()
    {
        if (worker == null || worker.getCitizenData() == null)
        {
            return 1.0D;
        }

        final int secondarySkill = Math.max(0, Math.min(MAX_SECONDARY_SKILL,
            worker.getCitizenData().getCitizenSkillHandler().getLevel(getModuleForJob().getSecondarySkill())));
        if (secondarySkill <= NORMAL_SECONDARY_SKILL)
        {
            return 1.0D;
        }

        final double bonusPerSkill = MAX_SECONDARY_SKILL_SPEED_BONUS / (MAX_SECONDARY_SKILL - NORMAL_SECONDARY_SKILL);
        return 1.0D + ((secondarySkill - NORMAL_SECONDARY_SKILL) * bonusPerSkill);
    }

    /**
     * Resolve native climate at a field block, preferring persisted natural biome captures over current overlays.
     *
     * @param level the server level containing the block
     * @param module biome module holding natural biome captures
     * @param pos block position to classify
     * @return native temperature and humidity axes
     */
    @SuppressWarnings("null")
    private static GreenhouseClimate nativeClimate(final ServerLevel level, final GreenhouseBiomeModule module, final BlockPos pos)
    {
        final ResourceLocation naturalBiomeId = module.getNaturalBiomes().get(quantizedBlockPos(pos));
        if (naturalBiomeId != null)
        {
            final Optional<Holder.Reference<Biome>> naturalBiome = level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolder(ResourceKey.create(Registries.BIOME, naturalBiomeId));
            if (naturalBiome.isPresent())
            {
                return climate(naturalBiomeId, naturalBiome.get().value());
            }
        }

        final Holder<Biome> currentBiome = level.getBiome(pos);
        final ResourceLocation currentBiomeId = currentBiome.unwrapKey().map(ResourceKey::location).orElse(null);
        return climate(currentBiomeId, currentBiome.value());
    }

    /**
     * Classify a biome into the greenhouse temperature and humidity axes.
     *
     * @param biomeId biome id when available
     * @param biome biome instance to inspect
     * @return corresponding greenhouse climate axes
     */
    private static GreenhouseClimate climate(final ResourceLocation biomeId, final Biome biome)
    {
        if (biomeId != null)
        {
            final Optional<GreenhouseClimate> configuredReferenceClimate = GreenhouseBiomeOverlayService.climateFor(biomeId);
            if (configuredReferenceClimate.isPresent())
            {
                return configuredReferenceClimate.get();
            }
        }

        final Biome.ClimateSettings settings = biome.getModifiedClimateSettings();
        final TemperatureSetting temperature = settings.temperature() <= 0.3F
            ? TemperatureSetting.COLD
            : settings.temperature() >= 0.9F ? TemperatureSetting.HOT : TemperatureSetting.TEMPERATE;
        final HumiditySetting humidity = settings.downfall() <= 0.3F
            ? HumiditySetting.DRY
            : settings.downfall() >= 0.8F ? HumiditySetting.HUMID : HumiditySetting.NORMAL;
        return new GreenhouseClimate(temperature, humidity);
    }

    /**
     * Convert step-weighted X/Z blocks into rounded-up ledger cost with skill scaling and an optional discount.
     *
     * @param stepCells number of block positions multiplied by climate steps
     * @param unitCost configured cost per rounded group of step-weighted cells
     * @param skillMultiplier multiplier from the worker's primary skill
     * @param discount fractional discount clamped to 0.0 through 1.0
     * @return discounted ledger cost for those step-weighted positions
     */
    private static int climateCost(final int stepCells, final int unitCost, final double skillMultiplier, final double discount)
    {
        if (stepCells <= 0)
        {
            return 0;
        }

        final int cost = ((stepCells + BIOME_CELLS_PER_BONUS_XP - 1) / BIOME_CELLS_PER_BONUS_XP) * unitCost;
        final double clampedSkillMultiplier = Math.max(0.0D, skillMultiplier);
        final double clampedDiscount = Math.max(0.0D, Math.min(1.0D, discount));
        return (int) Math.ceil(cost * clampedSkillMultiplier * (1.0D - clampedDiscount));
    }

    /**
     * Describe any ledger balances that are too low for a conversion.
     *
     * @param cost required conversion costs
     * @param temperatureModule temperature ledger module
     * @param humidityModule humidity ledger module
     * @return comma-separated shortage text, or blank when every required ledger has enough balance
     */
    private static String ledgerShortage(
        final BiomeConversionCost cost,
        final GreenhouseTemperatureModule temperatureModule,
        final GreenhouseHumidityModule humidityModule)
    {
        final List<String> shortages = new ArrayList<>();
        addShortage(shortages, "hot", cost.hot(), temperatureModule.getLedgerBalance(ClimateItemList.INCREASE));
        addShortage(shortages, "cold", cost.cold(), temperatureModule.getLedgerBalance(ClimateItemList.DECREASE));
        addShortage(shortages, "humid", cost.humid(), humidityModule.getLedgerBalance(ClimateItemList.INCREASE));
        addShortage(shortages, "dry", cost.dry(), humidityModule.getLedgerBalance(ClimateItemList.DECREASE));
        return String.join(", ", shortages);
    }

    private static void addShortage(final List<String> shortages, final String label, final int required, final int balance)
    {
        if (required > balance)
        {
            shortages.add(label + " " + balance + "/" + required);
        }
    }

    /**
     * Deduct all required conversion costs from the greenhouse climate ledgers.
     *
     * @param cost required conversion costs
     * @param temperatureModule temperature ledger module
     * @param humidityModule humidity ledger module
     */
    private static void debitConversionLedgers(
        final BiomeConversionCost cost,
        final GreenhouseTemperatureModule temperatureModule,
        final GreenhouseHumidityModule humidityModule)
    {
        temperatureModule.tryDebitLedger(ClimateItemList.INCREASE, cost.hot());
        temperatureModule.tryDebitLedger(ClimateItemList.DECREASE, cost.cold());
        humidityModule.tryDebitLedger(ClimateItemList.INCREASE, cost.humid());
        humidityModule.tryDebitLedger(ClimateItemList.DECREASE, cost.dry());
    }

    private static BlockPos quantizedBlockPos(final BlockPos pos)
    {
        return new BlockPos(quantize(pos.getX()), quantize(pos.getY()), quantize(pos.getZ()));
    }

    private static int quantize(final int value)
    {
        return QuartPos.toBlock(QuartPos.fromBlock(value));
    }

    /**
     * Track worker progress for a seed cleared by the greenhouse biome module.
     *
     * @param seedCleared true when a field seed was unset
     */
    private void trackSeedCleared(final boolean seedCleared)
    {
        if (!seedCleared)
        {
            return;
        }

        building.markDirty();
    }

    /**
     * Validate field roof coverage and greenhouse material ratio.
     *
     * @param level the server level containing the field
     * @param field the field whose footprint should be scanned
     * @return roof validation result for the complete footprint
     */
    private static RoofValidationResult validateGreenhouseRoof(final ServerLevel level, final FarmField field)
    {
        final BlockPos center = field.getPosition();
        if (center == null)
        {
            return RoofValidationResult.valid(Config.roofPercentage.get());
        }

        final int minX = center.getX() - field.getRadius(Direction.WEST);
        final int maxX = center.getX() + field.getRadius(Direction.EAST);
        final int minZ = center.getZ() - field.getRadius(Direction.NORTH);
        final int maxZ = center.getZ() + field.getRadius(Direction.SOUTH);
        final int requiredPercentage = Config.roofPercentage.get();
        int taggedColumns = 0;
        int coveredColumns = 0;
        int totalColumns = 0;

        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                final BlockPos fieldBlock = new BlockPos(x, center.getY(), z);
                final RoofColumnCoverage coverage = roofColumnCoverage(level, fieldBlock);
                totalColumns++;
                if (coverage == RoofColumnCoverage.HOLE)
                {
                    return RoofValidationResult.hole(fieldBlock, taggedColumns, coveredColumns, totalColumns, requiredPercentage);
                }

                coveredColumns++;
                if (coverage == RoofColumnCoverage.TAGGED)
                {
                    taggedColumns++;
                }
            }
        }

        if (totalColumns > 0 && (double) taggedColumns / (double) totalColumns < requiredPercentage / 100.0D)
        {
            return RoofValidationResult.insufficientRatio(taggedColumns, coveredColumns, totalColumns, requiredPercentage);
        }

        return RoofValidationResult.valid(taggedColumns, coveredColumns, totalColumns, requiredPercentage);
    }

    /**
     * Classify a single field column as tagged roof, covered ceiling, or hole.
     *
     * @param level the server level containing the field column
     * @param fieldBlock the block position at field height whose vertical column should be inspected
     * @return roof coverage classification for the column
     */
    private static RoofColumnCoverage roofColumnCoverage(final ServerLevel level, final BlockPos fieldBlock)
    {
        final int maxY = Math.min(level.getMaxBuildHeight() - 1, fieldBlock.getY() + MAX_GREENHOUSE_ROOF_HEIGHT);
        boolean covered = false;

        for (int y = fieldBlock.getY() + 1; y <= maxY; y++)
        {
            final BlockPos roofPos = new BlockPos(fieldBlock.getX(), y, fieldBlock.getZ());
            final BlockState roofState = level.getBlockState(roofPos);

            if (roofState == null) return RoofColumnCoverage.HOLE;

            if (isGreenhouseRoofMaterial(level, roofPos, roofState))
            {
                return RoofColumnCoverage.TAGGED;
            }

            if (isRoofLikeBlock(level, roofPos, roofState))
            {
                covered = true;
            }
        }

        return covered ? RoofColumnCoverage.COVERED : RoofColumnCoverage.HOLE;
    }

    /**
     * Check whether a block counts toward the greenhouse roof material ratio.
     *
     * @param level server level containing the block
     * @param roofPos block position to inspect
     * @param roofState block state to inspect
     * @return true when the block or one of its Domum Ornamentum component materials is tagged as greenhouse roof material
     */
    private static boolean isGreenhouseRoofMaterial(final @Nonnull ServerLevel level, final @Nonnull BlockPos roofPos, final @Nonnull BlockState roofState)
    {
        return roofState.is(ModTags.BLOCKS.GREENHOUSE_ROOF)
            || DomumOrnamentumRoofHelper.hasTaggedDomumMaterial(level, roofPos, roofState);
    }

    /**
     * Check whether an untagged block can count as a physical ceiling for hole detection.
     *
     * @param level server level containing the block
     * @param roofPos block position to inspect
     * @param roofState block state to inspect
     * @return true when the block is substantial enough to prevent an open-sky hole
     */
    @SuppressWarnings("deprecation") // Vanilla still uses blocksMotion() for rain; no great alternative for this use case.
    private static boolean isRoofLikeBlock(final @Nonnull ServerLevel level, final @Nonnull BlockPos roofPos, final @Nonnull BlockState roofState)
    {
        return !roofState.isAir()
            && (roofState.blocksMotion() || roofState.isCollisionShapeFullBlock(level, roofPos) || roofState.isFaceSturdy(level, roofPos, Direction.DOWN));
    }

    /**
     * Resolve the interaction key for a roof validation failure.
     *
     * @param roofValidation failed roof validation result
     * @param maintenance true when the failure happened during daily maintenance
     * @return translation key matching the failure type and work context
     */
    private static @Nonnull String roofFailureInteractionKey(final RoofValidationResult roofValidation, final boolean maintenance)
    {
        if (roofValidation.failure() == RoofValidationFailure.INSUFFICIENT_TAGGED_RATIO)
        {
            return maintenance
                ? InteractionInitializer.GREENHOUSE_BIOME_MAINTENANCE_ROOF_RATIO
                : InteractionInitializer.GREENHOUSE_ROOF_RATIO;
        }

        return maintenance
            ? InteractionInitializer.GREENHOUSE_BIOME_MAINTENANCE_NOGLASS
            : InteractionInitializer.GREENHOUSE_NOGLASS_AT;
    }

    /**
     * Build the conversion-specific roof validation failure message.
     *
     * @param roofValidation failed roof validation result
     * @return translated interaction message for conversion roof failure
     */
    private static Component conversionRoofFailureMessage(final RoofValidationResult roofValidation)
    {
        if (roofValidation.failure() == RoofValidationFailure.INSUFFICIENT_TAGGED_RATIO)
        {
            return Component.translatable(
                InteractionInitializer.GREENHOUSE_ROOF_RATIO,
                formatRoofCoveragePercentage(roofValidation),
                roofValidation.requiredPercentage());
        }

        return Component.translatable(
            InteractionInitializer.GREENHOUSE_NOGLASS_AT,
            formatBlockPos(roofValidation.holePosition()));
    }

    /**
     * Build the maintenance-specific roof validation failure message.
     *
     * @param fieldPosition field anchor position being maintained
     * @param roofValidation failed roof validation result
     * @return translated interaction message for maintenance roof failure
     */
    private static Component maintenanceRoofFailureMessage(final BlockPos fieldPosition, final RoofValidationResult roofValidation)
    {
        if (roofValidation.failure() == RoofValidationFailure.INSUFFICIENT_TAGGED_RATIO)
        {
            return Component.translatable(
                InteractionInitializer.GREENHOUSE_BIOME_MAINTENANCE_ROOF_RATIO,
                formatBlockPos(fieldPosition),
                formatRoofCoveragePercentage(roofValidation),
                roofValidation.requiredPercentage());
        }

        return Component.translatable(
            InteractionInitializer.GREENHOUSE_BIOME_MAINTENANCE_NOGLASS,
            formatBlockPos(fieldPosition),
            formatBlockPos(roofValidation.holePosition()));
    }

    /**
     * Initialize the corner-walking roof inspection for the selected field.
     *
     * @param field the field whose horizontal footprint should be walked around and checked
     */
    private void startRoofValidation(final FarmField field)
    {
        final BlockPos center = field.getPosition();
        if (center == null)
        {
            currentRoofInspectionTarget = null;
            return;
        }

        roofValidationMinX = center.getX() - field.getRadius(Direction.WEST);
        roofValidationMaxX = center.getX() + field.getRadius(Direction.EAST);
        roofValidationMinZ = center.getZ() - field.getRadius(Direction.NORTH);
        roofValidationMaxZ = center.getZ() + field.getRadius(Direction.SOUTH);
        roofInspectionCornerIndex = 0;
        currentRoofInspectionTarget = roofInspectionCorner(center.getY(), roofInspectionCornerIndex);
    }

    /**
     * Advance roof inspection to the next field corner.
     *
     * @return true when another field corner remains to be walked
     */
    private boolean advanceRoofInspectionTarget()
    {
        if (currentField == null)
        {
            currentRoofInspectionTarget = null;
            return false;
        }

        roofInspectionCornerIndex++;
        if (roofInspectionCornerIndex >= ROOF_INSPECTION_CORNERS)
        {
            currentRoofInspectionTarget = null;
            return false;
        }

        final BlockPos center = currentField.getPosition();
        if (center == null)
        {
            currentRoofInspectionTarget = null;
            return false;
        }

        currentRoofInspectionTarget = roofInspectionCorner(center.getY(), roofInspectionCornerIndex);
        return true;
    }

    /**
     * Resolve an inspection corner position for the current field footprint.
     *
     * @param y the field height for the corner target
     * @param cornerIndex the zero-based corner index
     * @return the block position for the requested corner
     */
    private BlockPos roofInspectionCorner(final int y, final int cornerIndex)
    {
        return switch (cornerIndex)
        {
            case 0 -> new BlockPos(roofValidationMinX, y, roofValidationMinZ);
            case 1 -> new BlockPos(roofValidationMaxX, y, roofValidationMinZ);
            case 2 -> new BlockPos(roofValidationMaxX, y, roofValidationMaxZ);
            default -> new BlockPos(roofValidationMinX, y, roofValidationMaxZ);
        };
    }

    /**
     * Format tagged roof coverage as a percentage for player-facing interaction text.
     *
     * @param roofValidation roof validation result containing scanned column counts
     * @return formatted percentage without the percent sign
     */
    private static String formatRoofCoveragePercentage(final RoofValidationResult roofValidation)
    {
        if (roofValidation.totalColumns() <= 0)
        {
            return "0";
        }

        final double percentage = (double) roofValidation.taggedColumns() * 100.0D / (double) roofValidation.totalColumns();
        final double rounded = Math.floor(percentage * 10.0D) / 10.0D;
        if (rounded == Math.floor(rounded))
        {
            return String.format(Locale.ROOT, "%.0f", rounded);
        }

        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    /**
     * Format a block position for player-facing interaction text.
     *
     * @param pos the block position to display
     * @return a compact coordinate string
     */
    private static String formatBlockPos(final BlockPos pos)
    {
        if (pos == null)
        {
            return "unknown";
        }

        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private String formatCurrentField()
    {
        return currentField == null ? "none" : formatField(currentFieldIndex, currentField.getPosition());
    }

    private static String formatField(final int fieldIndex, final BlockPos fieldPosition)
    {
        return "#" + fieldIndex + " at " + formatBlockPos(fieldPosition);
    }

    private static String formatLedgerTarget(final ClimateLedgerTarget target)
    {
        if (target == null)
        {
            return "none";
        }

        return target.requestDescription()
            + " item=" + target.stack()
            + " requestCount=" + target.requestCount()
            + " protectedStacks=" + target.protectedStacks()
            + " protectedQuantity=" + target.protectedQuantity()
            + " targetBalance=" + target.targetBalance();
    }

    private static String formatRoofValidation(final RoofValidationResult roofValidation)
    {
        return "failure=" + roofValidation.failure()
            + ", hole=" + formatBlockPos(roofValidation.holePosition())
            + ", tagged=" + roofValidation.taggedColumns()
            + ", covered=" + roofValidation.coveredColumns()
            + ", total=" + roofValidation.totalColumns()
            + ", required=" + roofValidation.requiredPercentage();
    }

    /**
     * Notify nearby colony players and show a soft particle burst over a newly converted field.
     *
     * @param level server level containing the field
     * @param fieldPosition field anchor position
     * @param fieldRange horizontal field range
     * @param assignment climate applied to the field
     */
    @SuppressWarnings("null")
    private void announceFieldTransformed(
        final ServerLevel level,
        final BlockPos fieldPosition,
        final int fieldRange,
        final FieldBiomeAssignment assignment)
    {
        MessageUtils.format(Component.translatable(
            FIELD_TRANSFORMED_MESSAGE,
            formatBlockPos(fieldPosition),
            Component.translatable("com.greenhousegardener.core.gui.biome.temperature." + assignment.temperature().getSerializedName()),
            Component.translatable("com.greenhousegardener.core.gui.biome.humidity." + assignment.humidity().getSerializedName())))
            .sendTo(building.getColony())
            .forAllPlayers();

        final double spread = Math.max(1.0D, fieldRange);
        level.sendParticles(
            ParticleTypes.POOF,
            fieldPosition.getX() + 0.5D,
            fieldPosition.getY() + 1.0D,
            fieldPosition.getZ() + 0.5D,
            FIELD_TRANSFORMED_POOF_COUNT,
            spread,
            0.75D,
            spread,
            0.03D);
    }

    /**
     * Clear the current field and roof validation state.
     */
    private void resetCurrentField()
    {
        currentField = null;
        currentFieldIndex = -1;
        currentFieldRange = 0;
        currentRoofInspectionTarget = null;
        roofValidationSuccessState = DECIDE;
    }

    /**
     * Calculate the largest horizontal field radius in any cardinal direction.
     *
     * @param field the field whose radius values should be inspected
     * @return the maximum north, south, east, or west field radius
     */
    private static int horizontalRange(final FarmField field)
    {
        return Math.max(
            Math.max(field.getRadius(Direction.NORTH), field.getRadius(Direction.SOUTH)),
            Math.max(field.getRadius(Direction.EAST), field.getRadius(Direction.WEST)));
    }

    /**
     * Conversion costs grouped by the ledgers that pay for each climate direction.
     */
    private record BiomeConversionCost(int hot, int cold, int humid, int dry)
    {
        private static final BiomeConversionCost NONE = new BiomeConversionCost(0, 0, 0, 0);
    }

    /**
     * Conversion preflight outcome used while scanning candidate fields.
     *
     * @param nextState immediate material-handling state, or null when no immediate state should run
     * @param skipField true when the current field is blocked for now and the scan should continue
     */
    private record ConversionPreflightResult(IAIState nextState, boolean skipField)
    {
        /**
         * Create a result for a field that can proceed to roof validation and conversion.
         *
         * @return ready preflight result
         */
        private static ConversionPreflightResult ready()
        {
            return new ConversionPreflightResult(null, false);
        }

        /**
         * Create a result for a field that has to wait for requested material.
         *
         * @return skipped-field preflight result
         */
        private static ConversionPreflightResult skippedField()
        {
            return new ConversionPreflightResult(null, true);
        }

        /**
         * Create a result for an immediate material-handling state.
         *
         * @param nextState state that should run before field conversion continues
         * @return material-handling preflight result
         */
        private static ConversionPreflightResult handle(final IAIState nextState)
        {
            return new ConversionPreflightResult(nextState, false);
        }
    }

    /**
     * A selected climate material and the module ledger it should feed.
     */
    private record ClimateLedgerTarget(
        GreenhouseClimateItemModule module,
        ClimateItemList list,
        ItemStack stack,
        int requestCount,
        int protectedStacks,
        int protectedQuantity,
        int targetBalance)
    {
        /**
         * Check whether a stack is the target climate material.
         *
         * @param candidate stack to compare
         * @return true when the item matches the selected material
         */
        private boolean matches(final ItemStack candidate)
        {
            return !candidate.isEmpty() && ItemStackUtils.compareItemStacksIgnoreStackSize(candidate, stack, true, true);
        }

        /**
         * Create the stack used by the request system.
         *
         * @return requested stack
         */
        private ItemStack requestStack()
        {
            final ItemStack requestStack = stack.copy();
            requestStack.setCount(requestCount);
            return requestStack;
        }

        /**
         * Build a request description unique to this climate modification type.
         *
         * @return request description used to identify duplicate climate requests
         */
        private String requestDescription()
        {
            return CLIMATE_MATERIAL_REQUEST + ": " + module.getModificationType(list).name().toLowerCase();
        }
    }

    /**
     * The building class this AI expects to work with.
     *
     * @return the greenhouse building class
     */
    @Override
    public Class<BuildingGreenhouse> getExpectedBuildingClass()
    {
        return BuildingGreenhouse.class;
    }
 
    /**
     * Waits for requests, with a failback to eventually give up after a certain amount of time 
     * for waiting if there isn't an associated requets for which we believe we should wait. 
     */
    @Override
    protected @NotNull IAIState waitForRequests() 
    {
        IAIState state = super.waitForRequests();
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist waitForRequests() state {}, open sync request? {}, completed pickup requests {}, deliverAcceptanceCounter {}.",
            building.getColony().getID(),
            state,
            building.hasOpenSyncRequest(worker.getCitizenData()),
            building.getCompletedRequestsOfCitizenOrBuilding(worker.getCitizenData()).size(),
            deliverAcceptanceCounter));

        if (state != AIWorkerState.NEEDS_ITEM) 
        {
            deliverAcceptanceCounter = 0;
            return state;
        }

        if (deliverAcceptanceCounter++ < SOFT_DELIVERY_ACCEPTANCE_COUNTER || building.hasOpenSyncRequest(worker.getCitizenData())) 
        {
            return state;
        }

        boolean clearedSomething = cleanStuckRequests(deliverAcceptanceCounter);

        if (clearedSomething)
        {
            deliverAcceptanceCounter = 0;
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist cleared stuck requests while waiting for items.", building.getColony().getID()));
        }

        // If we didn't clear anything, staying in NEEDS_ITEM is more honest than DECIDE.
        return clearedSomething ? AIWorkerState.DECIDE : AIWorkerState.NEEDS_ITEM;
    }

    /**
     * Cleans stuck requests from the building's request queue that are not deliverable anymore (for example, if a request is async, but the
     * citizen is not available to pick it up anymore).
     * 
     * @return true if any requests were cleared, false otherwise.
     */
    protected boolean cleanStuckRequests(int tryCounter)
    {
        ICitizenData citizen = worker.getCitizenData();
        Collection<IRequest<?>> completed = building.getCompletedRequestsOfCitizenOrBuilding(citizen);

        boolean cleared = false;

        // Copy IDs to avoid concurrent modification surprises.
        List<IRequest<?>> snapshot = new ArrayList<>(completed);

        for (IRequest<?> request : snapshot)
        {
            IToken<?> id = request.getId();
            if (!request.canBeDelivered() || citizen.isRequestAsync(id) || tryCounter > HARD_DELIVERY_ACCEPTANCE_COUNTER)
            {
                trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - Horticulturist accepting stuck request {} after {} tries. deliverable? {}, async? {}.",
                    building.getColony().getID(), id, tryCounter, request.canBeDelivered(), citizen.isRequestAsync(id)));
                building.markRequestAsAccepted(citizen, id);
                cleared = true;
            }
        }

        return cleared;
    }

}
