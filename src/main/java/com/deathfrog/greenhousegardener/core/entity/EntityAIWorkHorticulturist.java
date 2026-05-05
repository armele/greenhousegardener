package com.deathfrog.greenhousegardener.core.entity;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.api.colony.buildings.BuildingGreenhouse;
import com.deathfrog.greenhousegardener.apiimp.initializer.InteractionInitializer;
import com.deathfrog.greenhousegardener.core.ModTags;
import com.deathfrog.greenhousegardener.core.colony.buildings.jobs.JobsHorticulturist;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.FieldBiomeAssignment;
import com.deathfrog.greenhousegardener.core.world.GreenhouseBiomeOverlayService;
import com.deathfrog.greenhousegardener.core.world.GreenhouseBiomeOverlayService.ClimateSettings;
import com.deathfrog.greenhousegardener.core.world.GreenhouseBiomeOverlayService.OverlayResult;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.items.ItemCrop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

public class EntityAIWorkHorticulturist extends AbstractEntityAIInteract<JobsHorticulturist, BuildingGreenhouse>
{
    protected static final String FIELDS_TRANSFORMED_STAT = "fields_transformed";
    protected static final String BIOME_CELLS_TRANSFORMED_STAT = "biome_cells_transformed";
    protected static final String FIELD_SEEDS_UNSET_STAT = "field_seeds_unset";
    protected static final double BASE_BIOME_TRANSFORM_XP = 1.0D;
    protected static final int BIOME_CELLS_PER_BONUS_XP = 16;
    protected static final double BASE_SEED_UNSET_XP = 1.0D;
    protected static final int MAX_GREENHOUSE_ROOF_HEIGHT = 20;
    protected static final int ROOF_INSPECTION_CORNERS = 4;

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

    public enum HorticulturistState implements IAIState
    {
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

        final GreenhouseBiomeModule module = safeBiomeModule();
        final List<FarmField> fields = module.getManagedFields();
        for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++)
        {
            final FarmField field = fields.get(fieldIndex);
            if (field == null || !building.isInBuilding(field.getPosition()))
            {
                continue;
            }

            final FieldBiomeAssignment assignment = module.getAssignment(fieldIndex);
            final ClimateSettings settings = climateSettings(assignment);
            final int fieldRange = horizontalRange(field);

            if (GreenhouseBiomeOverlayService.needsOverlay(level, field.getPosition(), fieldRange, settings))
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
            if (field == null || !building.isInBuilding(field.getPosition()) || !needsSeedUnset(level, field))
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
        final FieldBiomeAssignment assignment = module.getAssignment(currentFieldIndex);
        final OverlayResult result = GreenhouseBiomeOverlayService.applyOverlay(
            level,
            fieldPosition,
            currentFieldRange,
            climateSettings(assignment),
            module.getNaturalBiomes(),
            module.getAppliedBiomes());

        if (result.changedCells() > 0)
        {
            incrementActionsDone();
            worker.getCitizenExperienceHandler().addExperience(BASE_BIOME_TRANSFORM_XP + (double) result.changedCells() / BIOME_CELLS_PER_BONUS_XP);
            StatsUtil.trackStat(building, FIELDS_TRANSFORMED_STAT, 1);
            StatsUtil.trackStat(building, BIOME_CELLS_TRANSFORMED_STAT, result.changedCells());
            module.markDirty();
            building.markDirty();
        }

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

        if (needsSeedUnset(level, currentField))
        {
            currentField.setSeed(ItemStack.EMPTY);
            incrementActionsDone();
            worker.getCitizenExperienceHandler().addExperience(BASE_SEED_UNSET_XP);
            StatsUtil.trackStat(building, FIELD_SEEDS_UNSET_STAT, 1);
            building.markDirty();
        }

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
     * Convert a field biome assignment into overlay service climate settings.
     *
     * @param assignment the field's requested temperature and humidity assignment
     * @return the climate settings used by the biome overlay service
     */
    private static ClimateSettings climateSettings(final FieldBiomeAssignment assignment)
    {
        return ClimateSettings.bySerializedNames(assignment.temperature().getSerializedName(), assignment.humidity().getSerializedName());
    }

    /**
     * Determine whether a field's configured seed can no longer grow in its current biome.
     *
     * @param level the server level containing the field
     * @param field the managed field to inspect
     * @return true when the field has a MineColonies crop seed that cannot be planted in the current biome
     */
    private static boolean needsSeedUnset(final ServerLevel level, final FarmField field)
    {
        final ItemStack seed = field.getSeed();
        if (seed == null || seed.isEmpty() || !(seed.getItem() instanceof ItemCrop itemCrop))
        {
            return false;
        }

        BlockPos pos = field.getPosition();

        if (pos == null) return false;

        final Holder<Biome> fieldBiome = level.getBiome(pos);
        return !itemCrop.canBePlantedIn(fieldBiome);
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
     * The building class this AI expects to work with.
     *
     * @return the greenhouse building class
     */
    @Override
    public Class<BuildingGreenhouse> getExpectedBuildingClass()
    {
        return BuildingGreenhouse.class;
    }
    
}
