package com.deathfrog.greenhousegardener.core.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.deathfrog.greenhousegardener.Config;
import com.deathfrog.greenhousegardener.api.colony.buildings.BuildingGreenhouse;
import com.deathfrog.greenhousegardener.apiimp.initializer.InteractionInitializer;
import com.deathfrog.greenhousegardener.core.ModTags;
import com.deathfrog.greenhousegardener.core.colony.buildings.jobs.JobsHorticulturist;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.FieldBiomeAssignment;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.HumiditySetting;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.TemperatureSetting;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseClimateItemModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseClimateItemModule.ClimateItemList;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseHumidityModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseTemperatureModule;
import com.deathfrog.greenhousegardener.core.world.GreenhouseBiomeOverlayService;
import com.deathfrog.greenhousegardener.core.world.GreenhouseBiomeOverlayService.GreenhouseClimate;
import com.deathfrog.greenhousegardener.core.world.GreenhouseBiomeOverlayService.OverlayResult;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.api.util.constant.StatisticsConstants;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

public class EntityAIWorkHorticulturist extends AbstractEntityAIInteract<JobsHorticulturist, BuildingGreenhouse>
{
    private static final String FIELDS_TRANSFORMED_STAT = "fields_transformed";
    private static final String CLIMATE_MATERIAL_REQUEST = "Greenhouse Climate Material";
    private static final double BASE_BIOME_TRANSFORM_XP = 1.0D;
    private static final int BIOME_CELLS_PER_BONUS_XP = 16;
    private static final int MAX_GREENHOUSE_ROOF_HEIGHT = 20;
    private static final int ROOF_INSPECTION_CORNERS = 4;


    /**
     * How many times the AI should attempt to find an allegedly delivered item before giving up on it.
     */
    private int deliverAcceptanceCounter = 0;
    private static final int SOFT_DELIVERY_ACCEPTANCE_COUNTER = 10;
    private static final int HARD_DELIVERY_ACCEPTANCE_COUNTER = 20;

    private FarmField currentField;
    private int currentFieldIndex = -1;
    private int currentFieldRange = 0;
    private BlockPos currentWanderTarget;
    private BlockPos currentRoofInspectionTarget;
    private int roofValidationMinX = 0;
    private int roofValidationMaxX = 0;
    private int roofValidationMinZ = 0;
    private int roofValidationMaxZ = 0;
    private int roofInspectionCornerIndex = 0;
    private ClimateLedgerTarget currentLedgerTarget;

    public enum HorticulturistState implements IAIState
    {
        LEDGER_CLIMATE_MATERIAL,
        VALIDATE_FIELD_ROOF,
        TRANSFORM_FIELD,
        UNSET_FIELD_SEED,
        WANDER_IN_BUILDING;

        @Override
        public boolean isOkayToEat()
        {
            return true;
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
            new AITarget<IAIState>(HorticulturistState.UNSET_FIELD_SEED, this::unsetFieldSeed, 50),
            new AITarget<IAIState>(HorticulturistState.WANDER_IN_BUILDING, this::wanderInBuilding, 20));
        worker.setCanPickUpLoot(true);
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
            return IDLE;
        }

        currentField = null;
        currentFieldIndex = -1;
        currentFieldRange = 0;
        currentRoofInspectionTarget = null;
        currentLedgerTarget = null;

        final ClimateLedgerTarget ledgerTarget = findClimateLedgerTarget();
        if (ledgerTarget != null)
        {
            currentLedgerTarget = ledgerTarget;
            if (InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), ledgerTarget::matches))
            {
                return HorticulturistState.LEDGER_CLIMATE_MATERIAL;
            }

            if (InventoryUtils.hasItemInProvider(building, ledgerTarget::matches))
            {
                final BlockPos materialPosition = building.getTileEntity().getPositionOfChestWithItemStack(ledgerTarget::matches);
                if (materialPosition != null && !building.getPosition().equals(materialPosition))
                {
                    needsCurrently = new com.minecolonies.api.util.Tuple<>(ledgerTarget::matches, 1);
                    return GATHERING_REQUIRED_MATERIALS;
                }

                return HorticulturistState.LEDGER_CLIMATE_MATERIAL;
            }

            requestClimateMaterial(ledgerTarget);
        }

        final GreenhouseBiomeModule module = safeBiomeModule();
        final List<FarmField> fields = module.getManagedFields();
        final int modifiedBiomeLimit = module.getModifiedBiomeLimit();
        int maintainedModifiedBiomes = 0;
        for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++)
        {
            final FarmField field = fields.get(fieldIndex);
            if (field == null)
            {
                continue;
            }

            final FieldBiomeAssignment assignment = module.getAssignment(field.getPosition());
            final GreenhouseClimate climate = climate(assignment);
            final int fieldRange = horizontalRange(field);
            if (module.isFieldModifiedFromNatural(level, field.getPosition()))
            {
                if (maintainedModifiedBiomes >= modifiedBiomeLimit)
                {
                    continue;
                }

                maintainedModifiedBiomes++;
            }

            if (GreenhouseBiomeOverlayService.needsOverlay(level, field.getPosition(), fieldRange, climate))
            {
                currentField = field;
                currentFieldIndex = fieldIndex;
                currentFieldRange = fieldRange;
                startRoofValidation(field);
                return HorticulturistState.VALIDATE_FIELD_ROOF;
            }
        }

        job.resetNoGlassCounter();

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
            return HorticulturistState.UNSET_FIELD_SEED;
        }

        currentWanderTarget = randomBuildingPosition();
        return currentWanderTarget == null ? IDLE : HorticulturistState.WANDER_IN_BUILDING;
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
            return DECIDE;
        }

        if (!walkToBuilding())
        {
            return HorticulturistState.LEDGER_CLIMATE_MATERIAL;
        }

        if (!currentLedgerTarget.module().isLedgerUnderLimit(currentLedgerTarget.list()))
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
        final int ledgered = currentLedgerTarget.module().ledgerStack(currentLedgerTarget.list(), extracted);
        if (ledgered > 0)
        {
            incrementActionsDone();
            worker.getCitizenExperienceHandler().addExperience(.2);
            StatsUtil.trackStatByName(building, StatisticsConstants.ITEM_USED, extracted.getItem().getDescriptionId(), extracted.getCount());
            building.markDirty();
        }

        if (ledgered > 0
            && currentLedgerTarget.module().isLedgerUnderLimit(currentLedgerTarget.list())
            && InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), currentLedgerTarget::matches))
        {
            return HorticulturistState.LEDGER_CLIMATE_MATERIAL;
        }

        currentLedgerTarget = null;
        needsCurrently = null;
        worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return DECIDE;
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
            resetCurrentField();
            return DECIDE;
        }

        if (!walkToSafePos(currentRoofInspectionTarget))
        {
            return HorticulturistState.VALIDATE_FIELD_ROOF;
        }

        if (advanceRoofInspectionTarget())
        {
            return HorticulturistState.VALIDATE_FIELD_ROOF;
        }

        final BlockPos missingRoofPos = findMissingGreenhouseRoof(level, currentField);
        if (missingRoofPos != null)
        {
            job.tickNoGlass();
            if (job.checkNoGlass())
            {
                worker.getCitizenData().triggerInteraction(new StandardInteraction(
                    Component.translatable(InteractionInitializer.GREENHOUSE_NOGLASS_AT, formatBlockPos(missingRoofPos)),
                    Component.translatable(InteractionInitializer.GREENHOUSE_NOGLASS_AT),
                    ChatPriority.BLOCKING));
            }

            return DECIDE;
        }

        job.resetNoGlassCounter();
        return HorticulturistState.TRANSFORM_FIELD;
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
            return DECIDE;
        }

        final BlockPos fieldPosition = currentField.getPosition();
        if (!walkToSafePos(fieldPosition))
        {
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
            worker.getCitizenData().triggerInteraction(new StandardInteraction(
                Component.translatable(InteractionInitializer.GREENHOUSE_BIOME_LEDGER_SHORTAGE, formatBlockPos(fieldPosition), shortage),
                Component.translatable(InteractionInitializer.GREENHOUSE_BIOME_LEDGER_SHORTAGE),
                ChatPriority.BLOCKING));
            resetCurrentField();
            return DECIDE;
        }

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
            incrementActionsDone();
            worker.getCitizenExperienceHandler().addExperience(BASE_BIOME_TRANSFORM_XP + (double) result.changedCells() / BIOME_CELLS_PER_BONUS_XP);
            StatsUtil.trackStat(building, FIELDS_TRANSFORMED_STAT, 1);
            module.markDirty();
            building.markDirty();
        }
        trackSeedCleared(module.clearInvalidSeedForActualBiome(level, currentField));

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
            return DECIDE;
        }

        final BlockPos fieldPosition = currentField.getPosition();
        if (!walkToSafePos(fieldPosition))
        {
            return HorticulturistState.UNSET_FIELD_SEED;
        }

        trackSeedCleared(safeBiomeModule().clearInvalidSeedForActualBiome(level, currentField));

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
        if (currentWanderTarget == null)
        {
            currentWanderTarget = randomBuildingPosition();
        }

        if (currentWanderTarget == null)
        {
            return IDLE;
        }

        if (!walkToSafePos(currentWanderTarget))
        {
            return HorticulturistState.WANDER_IN_BUILDING;
        }

        currentWanderTarget = null;
        return DECIDE;
    }

    @Override
    public IAIState getStateAfterPickUp()
    {
        if (currentLedgerTarget != null && InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), currentLedgerTarget::matches))
        {
            return HorticulturistState.LEDGER_CLIMATE_MATERIAL;
        }

        if (currentLedgerTarget != null && InventoryUtils.hasItemInProvider(building, currentLedgerTarget::matches))
        {
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
                if (!module.isLedgerUnderLimit(list))
                {
                    continue;
                }

                for (final ItemStorage item : module.getItems(list))
                {
                    final ItemStack stack = item.getItemStack();
                    final int requestCount = module.getLedgerRequestCount(list, stack);
                    if (requestCount <= 0)
                    {
                        continue;
                    }

                    final ClimateLedgerTarget target = new ClimateLedgerTarget(module, list, stack.copy(), requestCount, Math.max(0, item.getAmount()));
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
        if (hasClimateMaterialRequestOutstanding(target) || hasUnprocessedClimateMaterialInHut(target))
        {
            return;
        }

        worker.getCitizenData().createRequestAsync(new StackList(
            List.of(target.requestStack()),
            target.requestDescription(),
            target.requestCount(),
            1,
            target.protectedQuantity()));
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
    private static BiomeConversionCost biomeConversionCost(
        final ServerLevel level,
        final FarmField field,
        final FieldBiomeAssignment assignment,
        final GreenhouseBiomeModule module)
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
            conversionCost(hotStepCells),
            conversionCost(coldStepCells),
            conversionCost(humidStepCells),
            conversionCost(dryStepCells));
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
     * Convert step-weighted X/Z blocks into rounded-up ledger cost.
     *
     * @param stepCells number of block positions multiplied by climate steps
     * @return ledger cost for those step-weighted positions
     */
    private static int conversionCost(final int stepCells)
    {
        if (stepCells <= 0)
        {
            return 0;
        }

        return ((stepCells + BIOME_CELLS_PER_BONUS_XP - 1) / BIOME_CELLS_PER_BONUS_XP) * Config.baseConversionCost.get();
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
     * Check a single field column for an accepted roof block.
     *
     * @param level the server level containing the field column
     * @param fieldBlock the block position at field height whose vertical column should be inspected
     * @return true when a block tagged as a valid greenhouse roof is found above the field block within the allowed height
     */
    private static boolean hasGreenhouseRoofAbove(final ServerLevel level, final BlockPos fieldBlock)
    {
        final int maxY = Math.min(level.getMaxBuildHeight() - 1, fieldBlock.getY() + MAX_GREENHOUSE_ROOF_HEIGHT);

        for (int y = fieldBlock.getY() + 1; y <= maxY; y++)
        {
            final BlockPos roofPos = new BlockPos(fieldBlock.getX(), y, fieldBlock.getZ());
            final BlockState roofState = level.getBlockState(roofPos);
            if (roofState.is(ModTags.BLOCKS.GREENHOUSE_ROOF))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Find the first field block whose vertical column lacks an accepted greenhouse roof block.
     *
     * @param level the server level containing the field
     * @param field the field whose footprint should be scanned
     * @return the first field block requiring roof repair, or null when the complete footprint passes
     */
    private static BlockPos findMissingGreenhouseRoof(final ServerLevel level, final FarmField field)
    {
        final BlockPos center = field.getPosition();
        if (center == null)
        {
            return null;
        }

        final int minX = center.getX() - field.getRadius(Direction.WEST);
        final int maxX = center.getX() + field.getRadius(Direction.EAST);
        final int minZ = center.getZ() - field.getRadius(Direction.NORTH);
        final int maxZ = center.getZ() + field.getRadius(Direction.SOUTH);

        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                final BlockPos fieldBlock = new BlockPos(x, center.getY(), z);
                if (!hasGreenhouseRoofAbove(level, fieldBlock))
                {
                    return fieldBlock;
                }
            }
        }

        return null;
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
     * Format a block position for player-facing interaction text.
     *
     * @param pos the block position to display
     * @return a compact coordinate string
     */
    private static String formatBlockPos(final BlockPos pos)
    {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
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
    }

    /**
     * Pick a random position inside the greenhouse building bounds for idle wandering.
     *
     * @return a random in-building position, or the building position when no random position can be selected
     */
    private BlockPos randomBuildingPosition()
    {
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        if (corners == null || corners.getA() == null || corners.getB() == null)
        {
            return building.getPosition();
        }

        final BlockPos first = corners.getA();
        final BlockPos second = corners.getB();
        final int minX = Math.min(first.getX(), second.getX());
        final int minY = Math.min(first.getY(), second.getY());
        final int minZ = Math.min(first.getZ(), second.getZ());
        final int maxX = Math.max(first.getX(), second.getX());
        final int maxY = Math.max(first.getY(), second.getY());
        final int maxZ = Math.max(first.getZ(), second.getZ());

        for (int attempt = 0; attempt < 20; attempt++)
        {
            final BlockPos target = new BlockPos(
                minX + worker.getRandom().nextInt(maxX - minX + 1),
                minY + worker.getRandom().nextInt(maxY - minY + 1),
                minZ + worker.getRandom().nextInt(maxZ - minZ + 1));

            if (building.isInBuilding(target))
            {
                return target;
            }
        }

        return building.getPosition();
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
     * A selected climate material and the module ledger it should feed.
     */
    private record ClimateLedgerTarget(
        GreenhouseClimateItemModule module,
        ClimateItemList list,
        ItemStack stack,
        int requestCount,
        int protectedQuantity)
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
                building.markRequestAsAccepted(citizen, id);
                cleared = true;
            }
        }

        return cleared;
    }

}
