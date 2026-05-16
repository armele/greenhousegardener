package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.Config;
import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.ModCommands;
import com.deathfrog.greenhousegardener.core.blocks.ModBlocks;
import com.deathfrog.greenhousegardener.ModResearch;
import com.deathfrog.greenhousegardener.core.blocks.BlockClimateControlHub;
import com.deathfrog.greenhousegardener.core.blocks.BlockClimateControlHub.VisualClimate;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseClimateItemModule.ClimateItemList;
import com.deathfrog.greenhousegardener.core.datalistener.GreenhouseClimateItemValueListener;
import com.deathfrog.greenhousegardener.core.util.FieldLocationComponents;
import com.deathfrog.greenhousegardener.core.util.TraceUtils;
import com.deathfrog.greenhousegardener.core.world.biomeservice.FieldBiomeFootprint;
import com.deathfrog.greenhousegardener.core.world.biomeservice.GreenhouseBiomeOverlayService;
import com.deathfrog.greenhousegardener.core.world.biomeservice.GreenhouseClimate;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IAltersRequiredItems;
import com.minecolonies.api.colony.buildings.modules.IBuildingEventsModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer.FarmerFieldsModule;
import com.minecolonies.core.items.ItemCrop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class GreenhouseBiomeModule extends AbstractBuildingModule implements IPersistentModule, IBuildingEventsModule, IAltersRequiredItems, ITickingModule
{
    private static final int MAX_FIELD_SLOTS = 4;
    private static final int BIOMES_LEVEL1 = 1;
    private static final int BIOMES_LEVEL2PLUS = 2;

    private static final String TAG_ASSIGNMENTS = "assignments";
    private static final String TAG_FIELD_POS = "fieldPos";
    private static final String TAG_OWNED_FIELDS = "ownedFields";
    private static final String TAG_TEMPERATURE = "temperature";
    private static final String TAG_HUMIDITY = "humidity";
    private static final String TAG_LAST_CONVERTED_DAYS = "lastConvertedDays";
    private static final String TAG_LAST_FIELD_VISIT_DAYS = "lastFieldVisitDays";
    private static final String TAG_LAST_MAINTENANCE_VISIT_DAYS = "lastMaintenanceVisitDays";
    private static final String TAG_LAST_CONVERSION_BLOCKED_DAYS = "lastConversionBlockedDays";
    private static final String TAG_LAST_REVERTED_DAYS = "lastRevertedDays";
    private static final String TAG_FIRST_MISSED_MAINTENANCE_DAYS = "firstMissedMaintenanceDays";
    private static final String TAG_LAST_MAINTENANCE_WARNING_DAYS = "lastMaintenanceWarningDays";
    private static final String TAG_LAST_BIOME_CONTENTION_WARNING_DAYS = "lastBiomeContentionWarningDays";
    private static final String TAG_LAST_MAINTENANCE_DECAY_SCAN_DAY = "lastMaintenanceDecayScanDay";
    private static final String TAG_DAY = "day";
    private static final String TAG_FIRST_FIELD_POS = "firstFieldPos";
    private static final String TAG_SECOND_FIELD_POS = "secondFieldPos";
    private static final String TAG_NATURAL_BIOMES = "naturalBiomes";
    private static final String TAG_APPLIED_BIOMES = "appliedBiomes";
    private static final String TAG_POS = "pos";
    private static final String TAG_BIOME = "biome";
    private static final String FIELD_MAINTENANCE_WARNING_MESSAGE = "com.greenhousegardener.biome_maintenance.warning";
    private static final String FIELD_REVERTED_NOTICE = "com.greenhousegardener.biome_maintenance.reverted";

    private final Map<BlockPos, FieldBiomeAssignment> assignments = new HashMap<>();
    private final Set<BlockPos> ownedFields = new HashSet<>();
    private final Map<BlockPos, Long> lastConvertedDays = new HashMap<>();
    private final Map<BlockPos, Long> lastFieldVisitDays = new HashMap<>();
    private final Map<BlockPos, Long> lastMaintenanceVisitDays = new HashMap<>();
    private final Map<BlockPos, Long> lastConversionBlockedDays = new HashMap<>();
    private final Map<BlockPos, Long> lastRevertedDays = new HashMap<>();
    private final Map<BlockPos, Long> firstMissedMaintenanceDays = new HashMap<>();
    private final Map<BlockPos, Long> lastMaintenanceWarningDays = new HashMap<>();
    private final Map<FieldPairKey, Long> lastBiomeContentionWarningDays = new HashMap<>();
    private final Map<BlockPos, ResourceLocation> naturalBiomes = new HashMap<>();
    private final Map<BlockPos, ResourceLocation> appliedBiomes = new HashMap<>();
    private long lastMaintenanceDecayScanDay = Long.MIN_VALUE;

    public GreenhouseBiomeModule()
    {
        super();
    }

    private static void trace(final Runnable loggingStatement)
    {
        TraceUtils.dynamicTrace(ModCommands.TRACE_BIOME_MODULE, loggingStatement);
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        ownedFields.clear();
        for (final Tag tag : compound.getList(TAG_OWNED_FIELDS, Tag.TAG_COMPOUND))
        {
            final CompoundTag fieldTag = (CompoundTag) tag;

            if (fieldTag != null)
            {
                NbtUtils.readBlockPos(fieldTag, TAG_POS).ifPresent(pos -> ownedFields.add(pos.immutable()));
            }
        }

        assignments.clear();
        for (final Tag tag : compound.getList(TAG_ASSIGNMENTS, Tag.TAG_COMPOUND))
        {
            final CompoundTag assignmentTag = (CompoundTag) tag;
            if (assignmentTag != null)
            {
                NbtUtils.readBlockPos(assignmentTag, TAG_FIELD_POS).ifPresent(pos -> assignments.put(pos.immutable(),
                    new FieldBiomeAssignment(
                        TemperatureSetting.bySerializedName(assignmentTag.getString(TAG_TEMPERATURE)),
                        HumiditySetting.bySerializedName(assignmentTag.getString(TAG_HUMIDITY)))));
            }
        }

        naturalBiomes.clear();
        readBiomeMap(compound.getList(TAG_NATURAL_BIOMES, Tag.TAG_COMPOUND), naturalBiomes);

        appliedBiomes.clear();
        readBiomeMap(compound.getList(TAG_APPLIED_BIOMES, Tag.TAG_COMPOUND), appliedBiomes);

        lastConvertedDays.clear();
        readDayMap(compound.getList(TAG_LAST_CONVERTED_DAYS, Tag.TAG_COMPOUND), lastConvertedDays);

        lastFieldVisitDays.clear();
        readDayMap(compound.getList(TAG_LAST_FIELD_VISIT_DAYS, Tag.TAG_COMPOUND), lastFieldVisitDays);

        lastMaintenanceVisitDays.clear();
        readDayMap(compound.getList(TAG_LAST_MAINTENANCE_VISIT_DAYS, Tag.TAG_COMPOUND), lastMaintenanceVisitDays);

        lastConversionBlockedDays.clear();
        readDayMap(compound.getList(TAG_LAST_CONVERSION_BLOCKED_DAYS, Tag.TAG_COMPOUND), lastConversionBlockedDays);

        lastRevertedDays.clear();
        readDayMap(compound.getList(TAG_LAST_REVERTED_DAYS, Tag.TAG_COMPOUND), lastRevertedDays);

        firstMissedMaintenanceDays.clear();
        readDayMap(compound.getList(TAG_FIRST_MISSED_MAINTENANCE_DAYS, Tag.TAG_COMPOUND), firstMissedMaintenanceDays);

        lastMaintenanceWarningDays.clear();
        readDayMap(compound.getList(TAG_LAST_MAINTENANCE_WARNING_DAYS, Tag.TAG_COMPOUND), lastMaintenanceWarningDays);

        lastBiomeContentionWarningDays.clear();
        readFieldPairDayMap(compound.getList(TAG_LAST_BIOME_CONTENTION_WARNING_DAYS, Tag.TAG_COMPOUND), lastBiomeContentionWarningDays);

        lastMaintenanceDecayScanDay = compound.contains(TAG_LAST_MAINTENANCE_DECAY_SCAN_DAY)
            ? compound.getLong(TAG_LAST_MAINTENANCE_DECAY_SCAN_DAY)
            : Long.MIN_VALUE;
    }

    @SuppressWarnings("null")
    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        final ListTag ownedTags = new ListTag();
        ownedFields.stream().sorted(Comparator.comparing(BlockPos::asLong)).forEach(pos -> {
            final CompoundTag fieldTag = new CompoundTag();
            fieldTag.put(TAG_POS, NbtUtils.writeBlockPos(pos));
            ownedTags.add(fieldTag);
        });
        compound.put(TAG_OWNED_FIELDS, ownedTags);

        final ListTag assignmentTags = new ListTag();
        assignments.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().asLong())).forEach(entry -> {
            final CompoundTag assignmentTag = new CompoundTag();
            assignmentTag.put(TAG_FIELD_POS, NbtUtils.writeBlockPos(entry.getKey()));
            assignmentTag.putString(TAG_TEMPERATURE, entry.getValue().temperature().getSerializedName() + "");
            assignmentTag.putString(TAG_HUMIDITY, entry.getValue().humidity().getSerializedName() + "");
            assignmentTags.add(assignmentTag);
        });
        compound.put(TAG_ASSIGNMENTS, assignmentTags);

        final ListTag natural = writeBiomeMap(naturalBiomes);
        if (natural != null)
        {
            compound.put(TAG_NATURAL_BIOMES, natural);
        }

        final ListTag altered = writeBiomeMap(appliedBiomes);
        if (altered != null)
        {
            compound.put(TAG_APPLIED_BIOMES, altered);
        }

        compound.put(TAG_LAST_CONVERTED_DAYS, writeDayMap(lastConvertedDays));
        compound.put(TAG_LAST_FIELD_VISIT_DAYS, writeDayMap(lastFieldVisitDays));
        compound.put(TAG_LAST_MAINTENANCE_VISIT_DAYS, writeDayMap(lastMaintenanceVisitDays));
        compound.put(TAG_LAST_CONVERSION_BLOCKED_DAYS, writeDayMap(lastConversionBlockedDays));
        compound.put(TAG_LAST_REVERTED_DAYS, writeDayMap(lastRevertedDays));
        compound.put(TAG_FIRST_MISSED_MAINTENANCE_DAYS, writeDayMap(firstMissedMaintenanceDays));
        compound.put(TAG_LAST_MAINTENANCE_WARNING_DAYS, writeDayMap(lastMaintenanceWarningDays));
        compound.put(TAG_LAST_BIOME_CONTENTION_WARNING_DAYS, writeFieldPairDayMap(lastBiomeContentionWarningDays));
        compound.putLong(TAG_LAST_MAINTENANCE_DECAY_SCAN_DAY, lastMaintenanceDecayScanDay);
    }

    @SuppressWarnings("null")
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        final int supportedFields = getSupportedFieldCount();
        cleanupInvalidOwnedFields();
        final List<FarmField> fields = getVisibleFields();
        final GreenhouseTemperatureModule temperatureModule = building == null ? null : building.getModule(GreenhouseTemperatureModule.class, ignored -> true);
        final GreenhouseHumidityModule humidityModule = building == null ? null : building.getModule(GreenhouseHumidityModule.class, ignored -> true);
        final ServerLevel level = getServerLevel();

        buf.writeInt(supportedFields);
        buf.writeInt(getOwnedFieldCount());
        buf.writeInt(getModifiedBiomeLimit());
        buf.writeInt(getModifiedBiomeCount(level));
        buf.writeInt(ledgerBalance(temperatureModule, ClimateItemList.INCREASE));
        buf.writeInt(ledgerBalance(temperatureModule, ClimateItemList.DECREASE));
        buf.writeInt(ledgerBalance(humidityModule, ClimateItemList.INCREASE));
        buf.writeInt(ledgerBalance(humidityModule, ClimateItemList.DECREASE));
        buf.writeInt(fields.size());
        for (int i = 0; i < fields.size(); i++)
        {
            final FarmField field = fields.get(i);
            final BlockPos fieldPosition = field.getPosition().immutable();
            final FieldBiomeAssignment assignment = getAssignment(fieldPosition);
            final GreenhouseClimate naturalClimate = naturalClimate(level, fieldPosition);
            final int daysSinceLastMaintenance = daysSinceLastMaintenance(fieldPosition);
            buf.writeInt(i);
            buf.writeBlockPos(fieldPosition);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, field.getSeed());
            buf.writeEnum(assignment.temperature());
            buf.writeEnum(assignment.humidity());
            buf.writeEnum(naturalClimate.temperature());
            buf.writeEnum(naturalClimate.humidity());
            buf.writeBoolean(isOwned(fieldPosition));
            buf.writeInt(daysSinceLastMaintenance);
        }
    }

    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        final ServerLevel level = getServerLevel();
        if (building == null || colony == null || level == null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("BiomeModule colony tick skipped; building present? {}, colony present? {}, level present? {}.",
                building != null, colony != null, level != null));
            return;
        }

        final long colonyDay = colony.getDay();
        if (lastMaintenanceDecayScanDay == colonyDay)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule skipped maintenance decay scan for day {}; already scanned.",
                colony.getID(), colonyDay));
            return;
        }

        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule running maintenance decay scan for day {}; owned fields {}, tracked cells {}.",
            colony.getID(), colonyDay, ownedFields.size(), appliedBiomes.size()));
        lastMaintenanceDecayScanDay = colonyDay;
        final boolean changed = expireUnmaintainedFieldOverlays(level, colonyDay);
        markDirty();
        if (changed)
        {
            building.markDirty();
        }
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule completed maintenance decay scan for day {}; changed? {}.",
            colony.getID(), colonyDay, changed));
    }

    /**
     * Revert modified field overlays whose maintenance grace window has elapsed.
     *
     * @param level server level containing the field overlays
     * @param colonyDay current colony day
     * @return true when any field state changed
     */
    private boolean expireUnmaintainedFieldOverlays(final ServerLevel level, final long colonyDay)
    {
        boolean changed = false;
        for (final FarmField field : getManagedFields())
        {
            if (field == null)
            {
                continue;
            }

            final BlockPos fieldPosition = field.getPosition();
            final boolean modified = fieldPosition != null && isFieldModifiedFromNatural(level, fieldPosition);
            final boolean trackedOverlay = fieldPosition != null && hasTrackedOverlay(field);
            if (fieldPosition == null || !modified || !trackedOverlay)
            {
                trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule skipped decay for field {}; modified? {}, tracked overlay? {}.",
                    colonyId(), formatBlockPos(fieldPosition), modified, trackedOverlay));
                changed |= firstMissedMaintenanceDays.remove(fieldPosition) != null;
                changed |= lastMaintenanceWarningDays.remove(fieldPosition) != null;
                continue;
            }

            final MaintenanceDecayStatus decayStatus = maintenanceDecayStatus(fieldPosition, colonyDay);
            if (decayStatus == null)
            {
                trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule found no decay status for modified field {} on day {}; no successful climate work recorded.",
                    colonyId(), formatBlockPos(fieldPosition), colonyDay));
                continue;
            }

            if (decayStatus.daysSinceMaintenance() <= 0)
            {
                trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule field {} was maintained today; clearing missed-maintenance warning state.",
                    colonyId(), formatBlockPos(fieldPosition)));
                changed |= firstMissedMaintenanceDays.remove(fieldPosition) != null;
                changed |= lastMaintenanceWarningDays.remove(fieldPosition) != null;
                continue;
            }

            final BlockPos immutablePosition = fieldPosition.immutable();

            if (immutablePosition == null) continue;

            firstMissedMaintenanceDays.computeIfAbsent(immutablePosition, ignored -> decayStatus.lastMaintenanceDay() + 1);
            if (decayStatus.shouldRevert())
            {
                trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule reverting field {} on day {}; last maintained day {}, days since {}, configured delay {}.",
                    colonyId(), formatBlockPos(immutablePosition), colonyDay, decayStatus.lastMaintenanceDay(),
                    decayStatus.daysSinceMaintenance(), maintenanceRevertDays()));
                revertFieldToNaturalBiome(immutablePosition, colonyDay);
                changed = true;
                continue;
            }

            if (shouldSendMaintenanceWarning(immutablePosition, decayStatus))
            {
                sendMaintenanceWarning(field, immutablePosition, decayStatus);
                lastMaintenanceWarningDays.put(immutablePosition, colonyDay);
                changed = true;
            }
            else if (decayStatus.shouldWarn())
            {
                trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule suppressed maintenance warning for field {} on day {}; last warning day {}, reverted today? {}, config enabled? {}.",
                    colonyId(), formatBlockPos(immutablePosition), colonyDay, lastMaintenanceWarningDays.get(immutablePosition),
                    wasFieldRevertedOnDay(immutablePosition, colonyDay), Config.fieldReversionWarning.get()));
            }
        }

        return changed;
    }

    /**
     * Check whether the system should warn players for a newly completed missed-maintenance day.
     *
     * @param fieldPosition field anchor position
     * @param decayStatus current maintenance decay status
     * @return true when this tick represents a completed missed-maintenance day that has not been warned yet
     */
    private boolean shouldSendMaintenanceWarning(final BlockPos fieldPosition, final MaintenanceDecayStatus decayStatus)
    {
        return decayStatus.shouldWarn()
            && !dayEquals(lastMaintenanceWarningDays.get(fieldPosition), decayStatus.colonyDay())
            && !wasFieldRevertedOnDay(fieldPosition, decayStatus.colonyDay())
            && Config.fieldReversionWarning.get();
    }

    /**
     * Warn colony players that a modified field will revert if maintenance does not resume.
     *
     * @param field field being monitored
     * @param fieldPosition field anchor position
     * @param decayStatus current maintenance decay status
     */
    private void sendMaintenanceWarning(final FarmField field, final @Nonnull BlockPos fieldPosition, final MaintenanceDecayStatus decayStatus)
    {
        if (building == null || building.getColony() == null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("BiomeModule suppressed maintenance warning for field {}; building/colony unavailable.",
                formatBlockPos(fieldPosition)));
            return;
        }

        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule sending maintenance warning for field {}; last maintained day {}, days since {}, days until reversion {}.",
            building.getColony().getID(), formatBlockPos(fieldPosition), decayStatus.lastMaintenanceDay(),
            decayStatus.daysSinceMaintenance(), decayStatus.daysUntilReversion()));
        MessageUtils.format(Component.translatable(
            FIELD_MAINTENANCE_WARNING_MESSAGE,
            fieldDescription(field, fieldPosition),
            decayStatus.daysUntilReversion()))
            .withPriority(MessageUtils.MessagePriority.IMPORTANT)
            .sendTo(building.getColony(), true)
            .forManagers();
    }

    /**
     * Format a warning-friendly field location with the selected crop when one exists.
     *
     * @param field field to inspect
     * @param fieldPosition field anchor position
     * @return location text, optionally followed by the crop name
     */
    private static Component fieldDescription(final FarmField field, final @Nonnull BlockPos fieldPosition)
    {
        final Component position = FieldLocationComponents.fieldLocation(fieldPosition);
        final ItemStack seed = field == null ? ItemStack.EMPTY : field.getSeed();
        if (seed == null || seed.isEmpty())
        {
            return position;
        }

        return Component.translatable(
            "com.greenhousegardener.biome_maintenance.warning.field_crop",
            position,
            seed.getHoverName());
    }

    /**
     * Get the number of colony days since a field was last successfully maintained.
     *
     * @param fieldPosition position of the farm field anchor
     * @return zero for today, positive day count for older maintenance, or -1 when no maintenance is recorded
     */
    private int daysSinceLastMaintenance(final BlockPos fieldPosition)
    {
        if (building == null || building.getColony() == null)
        {
            return -1;
        }

        final MaintenanceDecayStatus decayStatus = maintenanceDecayStatus(fieldPosition, building.getColony().getDay());
        return decayStatus == null ? -1 : decayStatus.daysSinceMaintenance();
    }

    /**
     * Read a climate item module balance without requiring the module to exist.
     *
     * @param module climate item module, or null when unavailable
     * @param list increase or decrease ledger
     * @return current ledger balance, or zero when the module is unavailable
     */
    private static int ledgerBalance(final GreenhouseClimateItemModule module, final ClimateItemList list)
    {
        return module == null ? 0 : module.getLedgerBalance(list);
    }

    /**
     * Set the requested climate assignment for an owned field.
     *
     * @param fieldPosition position of the farm field anchor
     * @param temperature requested temperature setting
     * @param humidity requested humidity setting
     */
    public void setAssignment(final BlockPos fieldPosition, final TemperatureSetting temperature, final HumiditySetting humidity)
    {
        if (fieldPosition == null || !isOwned(fieldPosition))
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule rejected assignment {} for field {}; field owned? {}.",
                colonyId(), formatAssignment(temperature, humidity), formatBlockPos(fieldPosition), isOwned(fieldPosition)));
            return;
        }

        final BlockPos immutablePosition = fieldPosition.immutable();
        final FieldBiomeAssignment current = getAssignment(immutablePosition);
        if (current.temperature() == temperature && current.humidity() == humidity)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule assignment for field {} already {}; refreshing seed and hub state.",
                colonyId(), formatBlockPos(immutablePosition), formatAssignment(current)));
            clearInvalidSeedForAssignedClimate(immutablePosition);
            updateHubVisualClimate(immutablePosition, current);
            return;
        }

        final FieldBiomeAssignment assignment = new FieldBiomeAssignment(temperature, humidity);
        if (!canSetAssignment(immutablePosition, assignment))
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule rejected assignment {} for field {}; modified slots {}/{}.",
                colonyId(), formatAssignment(assignment), formatBlockPos(immutablePosition),
                getModifiedBiomeCount(getServerLevel()), getModifiedBiomeLimit()));
            return;
        }

        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule changed assignment for field {} from {} to {}.",
            colonyId(), formatBlockPos(immutablePosition), formatAssignment(current), formatAssignment(assignment)));
        assignments.put(immutablePosition, assignment);
        clearInvalidSeedForClimate(getServerLevel(), getField(immutablePosition), climate(assignment));
        updateHubVisualClimate(immutablePosition, assignment);
        markDirty();
    }

    /**
     * Get the requested climate assignment for a field.
     *
     * @param fieldPosition position of the farm field anchor
     * @return the saved assignment, or the field's natural climate when the player has not configured it
     */
    public FieldBiomeAssignment getAssignment(final BlockPos fieldPosition)
    {
        if (fieldPosition == null)
        {
            return FieldBiomeAssignment.DEFAULT;
        }

        final FieldBiomeAssignment assignment = assignments.get(fieldPosition);
        return assignment == null ? assignmentForNaturalClimate(getServerLevel(), fieldPosition) : assignment;
    }

    /**
     * Claim or release an eligible field for this greenhouse.
     *
     * @param fieldPosition position of the farm field anchor
     * @param owned true to claim the field, false to release it
     * @return true when ownership changed
     */
    public boolean setFieldOwned(final BlockPos fieldPosition, final boolean owned)
    {
        if (fieldPosition == null || building == null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("BiomeModule rejected field ownership change; field {}, owned {}, building present? {}.",
                formatBlockPos(fieldPosition), owned, building != null));
            return false;
        }

        final BlockPos immutablePosition = fieldPosition.immutable();
        if (owned)
        {
            if (ownedFields.contains(immutablePosition))
            {
                trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule field {} is already owned.",
                    colonyId(), formatBlockPos(immutablePosition)));
                return false;
            }
            if (ownedFields.size() >= getSupportedFieldCount() || !isEligibleUnownedField(immutablePosition))
            {
                trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule rejected field {} claim; owned fields {}/{}, eligible? {}.",
                    colonyId(), formatBlockPos(immutablePosition), ownedFields.size(), getSupportedFieldCount(), isEligibleUnownedField(immutablePosition)));
                return false;
            }

            ownedFields.add(immutablePosition);
            final ServerLevel level = getServerLevel();
            final FieldBiomeAssignment assignment = getAssignment(immutablePosition);
            clearInvalidSeedForClimate(level, getField(immutablePosition), climate(assignment));
            updateHubVisualClimate(immutablePosition, assignment);
            markDirty();
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule claimed field {} with initial assignment {}; owned fields {}/{}.",
                colonyId(), formatBlockPos(immutablePosition), formatAssignment(assignment), ownedFields.size(), getSupportedFieldCount()));
            return true;
        }

        return releaseOwnedField(immutablePosition);
    }

    /**
     * Check whether this greenhouse owns a field.
     *
     * @param fieldPosition position of the farm field anchor
     * @return true when the field is owned by this module
     */
    public boolean isOwned(final BlockPos fieldPosition)
    {
        return fieldPosition != null && ownedFields.contains(fieldPosition);
    }

    /**
     * Get the number of fields currently claimed by this greenhouse.
     *
     * @return the owned field count
     */
    public int getOwnedFieldCount()
    {
        return ownedFields.size();
    }

    /**
     * Get the number of owned fields that can be maintained away from their natural biome climate.
     *
     * @return configured base slots plus unlocked research slots
     */
    public int getModifiedBiomeLimit()
    {
        if (building == null || building.getColony() == null)
        {
            return 0;
        }

        if (building.getBuildingLevel() == 1) return BIOMES_LEVEL1;

        return BIOMES_LEVEL2PLUS
            + (int) Math.floor(building.getColony().getResearchManager().getResearchEffects().getEffectStrength(ModResearch.RESEARCH_EXTRA_BIOMES));
    }

    /**
     * Count owned fields whose requested climate differs from their persisted or current natural biome climate.
     *
     * @param level server level containing biome data
     * @return current modified-biome slot usage
     */
    public int getModifiedBiomeCount(final ServerLevel level)
    {
        return modifiedBiomeCount(level, ownedFields, assignments);
    }

    /**
     * Count distinct modified target climates in a proposed field state.
     *
     * @param level server level containing biome data
     * @param proposedOwned proposed owned fields
     * @param proposedAssignments proposed explicit assignments
     * @return distinct modified-biome variation count
     */
    private int modifiedBiomeCount(
        final ServerLevel level,
        final Collection<BlockPos> proposedOwned,
        final Map<BlockPos, FieldBiomeAssignment> proposedAssignments)
    {
        final Set<FieldBiomeAssignment> modifiedAssignments = new HashSet<>();
        for (final BlockPos fieldPosition : proposedOwned)
        {
            final FieldBiomeAssignment assignment = proposedAssignments.getOrDefault(fieldPosition, assignmentForNaturalClimate(level, fieldPosition));
            if (isAssignmentModifiedFromNatural(level, fieldPosition, assignment))
            {
                modifiedAssignments.add(assignment);
            }
        }
        return modifiedAssignments.size();
    }

    /**
     * Check whether a managed field currently consumes a modified-biome slot.
     *
     * @param level server level containing biome data
     * @param fieldPosition position of the farm field anchor
     * @return true when the saved assignment differs from the field's natural biome climate
     */
    public boolean isFieldModifiedFromNatural(final ServerLevel level, final BlockPos fieldPosition)
    {
        return isAssignmentModifiedFromNatural(level, fieldPosition, getAssignment(fieldPosition));
    }

    /**
     * Get the field's natural biome climate, preferring persisted captures when available.
     *
     * @param level server level containing biome data
     * @param fieldPosition position of the farm field anchor
     * @return natural temperature and humidity axes
     */
    public GreenhouseClimate getNaturalClimate(final ServerLevel level, final BlockPos fieldPosition)
    {
        if (fieldPosition == null)
        {
            return climate(FieldBiomeAssignment.DEFAULT);
        }

        return naturalClimate(level, fieldPosition);
    }

    /**
     * Check whether this field may change to the requested assignment under the modified-biome cap.
     *
     * @param fieldPosition position of the farm field anchor
     * @param assignment requested assignment
     * @return true when the change is allowed
     */
    public boolean canSetAssignment(final BlockPos fieldPosition, final FieldBiomeAssignment assignment)
    {
        if (fieldPosition == null || assignment == null || !isOwned(fieldPosition))
        {
            return false;
        }

        final ServerLevel level = getServerLevel();
        final Map<BlockPos, FieldBiomeAssignment> proposedAssignments = new HashMap<>(assignments);
        proposedAssignments.put(fieldPosition.immutable(), assignment);
        return modifiedBiomeCount(level, ownedFields, proposedAssignments) <= getModifiedBiomeLimit();
    }

    /**
     * Apply a complete batch of field ownership and assignment changes after validating the final state.
     *
     * @param changes requested field changes
     * @return true when the full batch was valid and applied
     */
    public boolean applyFieldChanges(final Collection<FieldBiomeChange> changes)
    {
        if (changes == null || changes.isEmpty() || building == null)
        {
            return false;
        }

        final ProposedFieldState proposed = proposedFieldState(changes);
        if (proposed == null || modifiedBiomeCount(getServerLevel(), proposed.ownedFields(), proposed.assignments()) > getModifiedBiomeLimit())
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule rejected batched field changes; proposed modified variations {}/{}.",
                colonyId(),
                proposed == null ? -1 : modifiedBiomeCount(getServerLevel(), proposed.ownedFields(), proposed.assignments()),
                getModifiedBiomeLimit()));
            return false;
        }

        for (final FieldBiomeChange change : changes)
        {
            if (change.owned())
            {
                setFieldOwned(change.fieldPosition(), true);
                setAssignment(change.fieldPosition(), change.assignment().temperature(), change.assignment().humidity());
            }
            else
            {
                setFieldOwned(change.fieldPosition(), false);
            }
        }
        return true;
    }

    /**
     * Build and validate the final owned/assignment state for a batched save.
     *
     * @param changes requested field changes
     * @return proposed final state, or null when the request is invalid
     */
    private ProposedFieldState proposedFieldState(final Collection<FieldBiomeChange> changes)
    {
        final Set<BlockPos> proposedOwned = new HashSet<>(ownedFields);
        final Map<BlockPos, FieldBiomeAssignment> proposedAssignments = new HashMap<>(assignments);

        for (final FieldBiomeChange change : changes)
        {
            if (change == null || change.fieldPosition() == null || change.assignment() == null)
            {
                return null;
            }

            final BlockPos fieldPosition = change.fieldPosition().immutable();
            if (change.owned())
            {
                if (!proposedOwned.contains(fieldPosition))
                {
                    if (proposedOwned.size() >= getSupportedFieldCount() || !isEligibleUnownedField(fieldPosition))
                    {
                        return null;
                    }
                    proposedOwned.add(fieldPosition);
                }
                proposedAssignments.put(fieldPosition, change.assignment());
            }
            else
            {
                proposedOwned.remove(fieldPosition);
                proposedAssignments.remove(fieldPosition);
            }
        }

        return new ProposedFieldState(proposedOwned, proposedAssignments);
    }

    /**
     * Get the persisted natural biome cells captured before greenhouse overlays were applied.
     *
     * @return mutable map of quantized biome cell positions to natural biome ids
     */
    public Map<BlockPos, ResourceLocation> getNaturalBiomes()
    {
        return naturalBiomes;
    }

    /**
     * Get the persisted biome cells last written by the greenhouse overlay service.
     *
     * @return mutable map of quantized biome cell positions to applied biome ids
     */
    public Map<BlockPos, ResourceLocation> getAppliedBiomes()
    {
        return appliedBiomes;
    }

    /**
     * Record that a field was converted on a colony day.
     *
     * @param fieldPosition position of the farm field anchor
     * @param colonyDay current colony day
     */
    public void recordFieldConverted(final BlockPos fieldPosition, final long colonyDay)
    {
        if (fieldPosition == null)
        {
            return;
        }

        final BlockPos immutablePosition = fieldPosition.immutable();
        lastConvertedDays.put(immutablePosition, colonyDay);
        lastFieldVisitDays.put(immutablePosition, colonyDay);
        lastMaintenanceVisitDays.put(immutablePosition, colonyDay);
        firstMissedMaintenanceDays.remove(immutablePosition);
        lastMaintenanceWarningDays.remove(immutablePosition);
        markDirty();
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule recorded conversion for field {} on day {}; assignment {}.",
            colonyId(), formatBlockPos(immutablePosition), colonyDay, formatAssignment(getAssignment(immutablePosition))));
    }

    /**
     * Record that the horticulturist visited a field on a colony day.
     *
     * @param fieldPosition position of the farm field anchor
     * @param colonyDay current colony day
     */
    public void recordFieldVisited(final BlockPos fieldPosition, final long colonyDay)
    {
        if (fieldPosition == null)
        {
            return;
        }

        lastFieldVisitDays.put(fieldPosition.immutable(), colonyDay);
        markDirty();
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule recorded field visit for {} on day {}.",
            colonyId(), formatBlockPos(fieldPosition), colonyDay));
    }

    /**
     * Check whether the horticulturist has already visited a field on a colony day.
     *
     * @param fieldPosition position of the farm field anchor
     * @param colonyDay current colony day
     * @return true when any field trip already happened that day
     */
    public boolean wasFieldVisitedOnDay(final BlockPos fieldPosition, final long colonyDay)
    {
        return dayEquals(lastFieldVisitDays.get(fieldPosition), colonyDay);
    }

    /**
     * Check whether a field was converted on a colony day.
     *
     * @param fieldPosition position of the farm field anchor
     * @param colonyDay current colony day
     * @return true when the field was converted on that day
     */
    public boolean wasFieldConvertedOnDay(final BlockPos fieldPosition, final long colonyDay)
    {
        return dayEquals(lastConvertedDays.get(fieldPosition), colonyDay);
    }

    /**
     * Check whether the horticulturist has already visited a field for maintenance on a colony day.
     *
     * @param fieldPosition position of the farm field anchor
     * @param colonyDay current colony day
     * @return true when a maintenance visit already happened that day
     */
    public boolean wasFieldVisitedForMaintenanceOnDay(final BlockPos fieldPosition, final long colonyDay)
    {
        return dayEquals(lastMaintenanceVisitDays.get(fieldPosition), colonyDay);
    }

    /**
     * Check whether a field has already been successfully maintained on a colony day.
     *
     * @param fieldPosition position of the farm field anchor
     * @param colonyDay current colony day
     * @return true when maintenance succeeded on that day
     */
    public boolean wasFieldMaintainedOnDay(final BlockPos fieldPosition, final long colonyDay)
    {
        return dayEquals(lastMaintenanceVisitDays.get(fieldPosition), colonyDay)
            && !firstMissedMaintenanceDays.containsKey(fieldPosition);
    }

    /**
     * Check whether a field maintenance visit failed on a colony day.
     *
     * @param fieldPosition position of the farm field anchor
     * @param colonyDay current colony day
     * @return true when maintenance was attempted but not completed on that day
     */
    public boolean wasFieldMaintenanceMissedOnDay(final BlockPos fieldPosition, final long colonyDay)
    {
        return dayEquals(lastMaintenanceVisitDays.get(fieldPosition), colonyDay)
            && firstMissedMaintenanceDays.containsKey(fieldPosition);
    }

    /**
     * Check whether a field has ever had successful climate conversion or maintenance recorded.
     *
     * @param fieldPosition position of the farm field anchor
     * @return true when this field has prior successful climate work
     */
    public boolean hasRecordedClimateWork(final BlockPos fieldPosition)
    {
        return lastRecordedMaintenanceDay(fieldPosition) != null;
    }

    /**
     * Check whether a modified field should still show active conditioning effects.
     *
     * <p>Ambient particles are suppressed once the field enters the same stale-maintenance window that produces
     * maintenance warnings: no successful maintenance yesterday, and no successful maintenance yet today.</p>
     *
     * @param fieldPosition position of the farm field anchor
     * @param colonyDay current colony day
     * @return true when the field was successfully maintained or converted today or yesterday
     */
    public boolean isFieldConditioningActiveForDay(final BlockPos fieldPosition, final long colonyDay)
    {
        final MaintenanceDecayStatus decayStatus = maintenanceDecayStatus(fieldPosition, colonyDay);
        return decayStatus != null && decayStatus.isConditioningActive();
    }

    /**
     * Check whether a modified field should emit ambient conditioning particles.
     *
     * <p>Particles are only visualized while the field still has an active, tracked greenhouse overlay. This keeps
     * particle effects aligned with the real overlay lifecycle rather than only the configured target climate.</p>
     *
     * @param level server level containing biome data
     * @param field farm field to inspect
     * @param colonyDay current colony day
     * @return true when the field currently has active conditioning effects to show
     */
    public boolean shouldEmitConditioningParticles(final ServerLevel level, final FarmField field, final long colonyDay)
    {
        if (level == null || field == null || field.getPosition() == null)
        {
            return false;
        }

        final BlockPos fieldPosition = field.getPosition();
        return isFieldModifiedFromNatural(level, fieldPosition)
            && hasTrackedOverlay(field)
            && isFieldConditioningActiveForDay(fieldPosition, colonyDay)
            && !wasFieldRevertedOnDay(fieldPosition, colonyDay);
    }

    /**
     * Get how many colony days remain before the field reverts to its natural biome.
     *
     * @param fieldPosition position of the farm field anchor
     * @param colonyDay current colony day
     * @return remaining colony days, or the configured grace period when no prior maintenance exists
     */
    public int daysUntilMaintenanceReversion(final BlockPos fieldPosition, final long colonyDay)
    {
        final MaintenanceDecayStatus decayStatus = maintenanceDecayStatus(fieldPosition, colonyDay);
        return decayStatus == null ? maintenanceRevertDays() : decayStatus.daysUntilReversion();
    }

    /**
     * After how many days without maintenance will the field revert to its natural biome?
     * Note that the addition of 1 here implicitly allows all downstream logic to effectively respect the 'rule':
     * "Maintenance yesterday and no maintenance today is not yet a day without maintenance, as maintenance still may happen today."
     * 
     * @return
     */
    public static int maintenanceRevertDays()
    {
        return Config.maintenanceRevertDays.get() + 1;
    }

    /**
     * Record that conversion for a field could not proceed on a colony day.
     *
     * @param fieldPosition position of the field whose conversion is blocked
     * @param colonyDay current colony day
     */
    public void recordFieldConversionBlocked(final BlockPos fieldPosition, final long colonyDay)
    {
        if (fieldPosition == null)
        {
            return;
        }

        lastConversionBlockedDays.put(fieldPosition.immutable(), colonyDay);
        markDirty();
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule recorded conversion block for field {} on day {}.",
            colonyId(), formatBlockPos(fieldPosition), colonyDay));
    }

    /**
     * Check whether conversion for a field already failed validation on a colony day.
     *
     * @param fieldPosition position of the field to inspect
     * @param colonyDay current colony day
     * @return true when conversion should be skipped until a later colony day
     */
    public boolean wasFieldConversionBlockedOnDay(final BlockPos fieldPosition, final long colonyDay)
    {
        return dayEquals(lastConversionBlockedDays.get(fieldPosition), colonyDay);
    }

    /**
     * Clear conversion blocks recorded for a colony day.
     *
     * @param colonyDay colony day whose conversion blocks should be retried
     */
    public void clearConversionBlocksForDay(final long colonyDay)
    {
        if (lastConversionBlockedDays.entrySet().removeIf(entry -> dayEquals(entry.getValue(), colonyDay)))
        {
            markDirty();
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule cleared conversion blocks for day {}.",
                colonyId(), colonyDay));
        }
    }

    /**
     * Check whether nearby fields with conflicting biome targets have already warned players today.
     *
     * @param firstFieldPosition first field anchor position
     * @param secondFieldPosition second field anchor position
     * @param colonyDay current colony day
     * @return true when this field pair already produced today's contention warning
     */
    public boolean wasBiomeContentionWarnedOnDay(
        final BlockPos firstFieldPosition,
        final BlockPos secondFieldPosition,
        final long colonyDay)
    {
        return dayEquals(lastBiomeContentionWarningDays.get(FieldPairKey.of(firstFieldPosition, secondFieldPosition)), colonyDay);
    }

    /**
     * Record that nearby fields with conflicting biome targets warned players today.
     *
     * @param firstFieldPosition first field anchor position
     * @param secondFieldPosition second field anchor position
     * @param colonyDay current colony day
     */
    public void recordBiomeContentionWarning(
        final BlockPos firstFieldPosition,
        final BlockPos secondFieldPosition,
        final long colonyDay)
    {
        final FieldPairKey key = FieldPairKey.of(firstFieldPosition, secondFieldPosition);
        if (key == null)
        {
            return;
        }

        lastBiomeContentionWarningDays.put(key, colonyDay);
        markDirty();
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule recorded biome contention warning between fields {} and {} on day {}.",
            colonyId(), formatBlockPos(key.first()), formatBlockPos(key.second()), colonyDay));
    }

    /**
     * Check whether a field reverted to its natural biome on a colony day.
     *
     * @param fieldPosition position of the field to inspect
     * @param colonyDay current colony day
     * @return true when climate work should be blocked until a later colony day
     */
    public boolean wasFieldRevertedOnDay(final BlockPos fieldPosition, final long colonyDay)
    {
        return dayEquals(lastRevertedDays.get(fieldPosition), colonyDay);
    }

    /**
     * Record a successful maintenance visit.
     *
     * @param fieldPosition position of the farm field anchor
     * @param colonyDay current colony day
     */
    public void recordFieldMaintained(final BlockPos fieldPosition, final long colonyDay)
    {
        if (fieldPosition == null)
        {
            return;
        }

        final BlockPos immutablePosition = fieldPosition.immutable();
        lastFieldVisitDays.put(immutablePosition, colonyDay);
        lastMaintenanceVisitDays.put(immutablePosition, colonyDay);
        firstMissedMaintenanceDays.remove(immutablePosition);
        lastMaintenanceWarningDays.remove(immutablePosition);
        markDirty();
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule recorded maintenance for field {} on day {}.",
            colonyId(), formatBlockPos(immutablePosition), colonyDay));
    }

    /**
     * Record a maintenance visit that could not be paid for.
     *
     * @param fieldPosition position of the farm field anchor
     * @param colonyDay current colony day
     * @return the first colony day in the current missed-maintenance streak
     */
    public long recordFieldMaintenanceMissed(final BlockPos fieldPosition, final long colonyDay)
    {
        if (fieldPosition == null)
        {
            return colonyDay;
        }

        final BlockPos immutablePosition = fieldPosition.immutable();
        final Long lastMaintenanceDay = lastRecordedMaintenanceDay(immutablePosition);
        final long missedMaintenanceStartDay = lastMaintenanceDay != null && lastMaintenanceDay < colonyDay
            ? lastMaintenanceDay + 1
            : colonyDay;
        lastFieldVisitDays.put(immutablePosition, colonyDay);
        lastMaintenanceVisitDays.put(immutablePosition, colonyDay);
        final long firstMissedDay = firstMissedMaintenanceDays.computeIfAbsent(immutablePosition, ignored -> missedMaintenanceStartDay);
        markDirty();
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule recorded missed maintenance for field {} on day {}; first missed day {}, last successful day {}.",
            colonyId(), formatBlockPos(immutablePosition), colonyDay, firstMissedDay, lastMaintenanceDay));
        return firstMissedDay;
    }

    /**
     * Restore an owned field's overlay to the natural biome and clear its missed-maintenance streak.
     *
     * @param fieldPosition position of the farm field anchor
     */
    public void revertFieldToNaturalBiome(final BlockPos fieldPosition, final long colonyDay)
    {
        if (fieldPosition == null)
        {
            return;
        }

        final BlockPos immutablePosition = fieldPosition.immutable();
        final FarmField field = getField(fieldPosition);
        restoreFieldOverlay(immutablePosition);
        clearFieldOverlayTracking(immutablePosition);
        clearFieldSeed(field);
        assignments.remove(immutablePosition);
        updateHubVisualClimate(immutablePosition, getAssignment(immutablePosition));
        lastRevertedDays.put(immutablePosition, colonyDay);
        lastConvertedDays.remove(immutablePosition);
        lastFieldVisitDays.remove(immutablePosition);
        lastMaintenanceVisitDays.remove(immutablePosition);
        lastConversionBlockedDays.remove(immutablePosition);
        firstMissedMaintenanceDays.remove(immutablePosition);
        lastMaintenanceWarningDays.remove(immutablePosition);
        lastBiomeContentionWarningDays.keySet().removeIf(key -> key.contains(immutablePosition));
        markDirty();
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule reverted field {} to natural biome on day {}; cleared overlay tracking and seed assignment.",
            colonyId(), formatBlockPos(immutablePosition), colonyDay));

        MessageUtils.format(Component.translatable(
            FIELD_REVERTED_NOTICE,
            fieldDescription(field, fieldPosition)))
            .withPriority(MessageUtils.MessagePriority.IMPORTANT)
            .sendTo(building.getColony(), true)
            .forManagers();

    }

    /**
     * Determine whether a field's configured seed can grow in its current biome.
     *
     * @param level server level containing the field
     * @param field field to inspect
     * @return true when an exotic MineColonies crop seed cannot grow at the field anchor
     */
    @SuppressWarnings("null")
    public boolean needsSeedUnsetForActualBiome(final ServerLevel level, final FarmField field)
    {
        if (level == null || field == null || field.getPosition() == null)
        {
            return false;
        }

        return !canSeedGrowInBiome(field.getSeed(), level.getBiome(field.getPosition()));
    }

    /**
     * Clear a field's configured seed when it cannot grow in the current biome.
     *
     * @param level server level containing the field
     * @param field field to inspect
     * @return true when the seed was cleared
     */
    public boolean clearInvalidSeedForActualBiome(final ServerLevel level, final FarmField field)
    {
        if (!needsSeedUnsetForActualBiome(level, field))
        {
            return false;
        }

        clearFieldSeed(field);
        markDirty();
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule cleared invalid seed for field {} in actual biome.",
            colonyId(), field == null ? "null" : formatBlockPos(field.getPosition())));
        return true;
    }

    /**
     * Clear a field's configured seed when it cannot grow in a requested greenhouse climate.
     *
     * @param level server level containing biome registries
     * @param field field to inspect
     * @param climate target greenhouse climate
     * @return true when the seed was cleared
     */
    public boolean clearInvalidSeedForClimate(final ServerLevel level, final FarmField field, final GreenhouseClimate climate)
    {
        if (level == null || field == null || climate == null)
        {
            return false;
        }

        final Holder.Reference<Biome> targetBiome = biomeHolder(level, GreenhouseBiomeOverlayService.biomeFor(climate));
        if (targetBiome == null || canSeedGrowInBiome(field.getSeed(), targetBiome))
        {
            return false;
        }

        clearFieldSeed(field);
        markDirty();
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule cleared invalid seed for field {} for target climate {}.",
            colonyId(), formatBlockPos(field.getPosition()), climate));
        return true;
    }

    @Override
    public void onDestroyed()
    {
        final ServerLevel level = getServerLevel();
        if (level == null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule destroyed without server level; clearing {} owned fields and {} tracked cells.",
                colonyId(), ownedFields.size(), appliedBiomes.size()));
            ownedFields.clear();
            assignments.clear();
            lastConvertedDays.clear();
            lastFieldVisitDays.clear();
            lastMaintenanceVisitDays.clear();
            lastConversionBlockedDays.clear();
            lastRevertedDays.clear();
            firstMissedMaintenanceDays.clear();
            lastMaintenanceWarningDays.clear();
            lastBiomeContentionWarningDays.clear();
            return;
        }

        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule destroyed; restoring all overlays for {} owned fields and {} tracked cells.",
            colonyId(), ownedFields.size(), appliedBiomes.size()));
        GreenhouseBiomeOverlayService.restoreAllOverlays(level, naturalBiomes, appliedBiomes);
        clearInvalidOwnedFieldSeedsForActualBiomes(level);
        ownedFields.clear();
        assignments.clear();
        lastConvertedDays.clear();
        lastFieldVisitDays.clear();
        lastMaintenanceVisitDays.clear();
        lastConversionBlockedDays.clear();
        lastRevertedDays.clear();
        firstMissedMaintenanceDays.clear();
        lastMaintenanceWarningDays.clear();
        lastBiomeContentionWarningDays.clear();
        markDirty();
    }

    /**
     * Restore this greenhouse's owned field biome overlays before the hut block is picked up.
     */
    public void restoreOwnedFieldBiomesBeforePickup()
    {
        final ServerLevel level = getServerLevel();
        if (level == null || ownedFields.isEmpty() || appliedBiomes.isEmpty())
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule skipped pickup overlay restore; level present? {}, owned fields {}, tracked cells {}.",
                colonyId(), level != null, ownedFields.size(), appliedBiomes.size()));
            return;
        }

        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule restoring owned field biomes before pickup; owned fields {}, tracked cells {}.",
            colonyId(), ownedFields.size(), appliedBiomes.size()));
        GreenhouseBiomeOverlayService.restoreAllOverlays(level, naturalBiomes, appliedBiomes);
        clearInvalidOwnedFieldSeedsForActualBiomes(level);
        markDirty();
        if (building != null)
        {
            building.markDirty();
        }
    }

    /**
     * Get how many fields this greenhouse level can own.
     *
     * @return maximum owned field count for the current building level
     */
    public int getSupportedFieldCount()
    {
        final int level = building == null ? 0 : building.getBuildingLevel();
        if (level <= 0)
        {
            return 0;
        }
        if (level == 1)
        {
            return 1;
        }
        if (level == 2)
        {
            return 2;
        }
        if (level == 3)
        {
            return 3;
        }
        return MAX_FIELD_SLOTS;
    }

    /**
     * Get the fields this greenhouse currently owns and can actively maintain.
     *
     * @return sorted list of owned farm fields with valid climate control hubs
     */
    @SuppressWarnings("null")
    public List<FarmField> getManagedFields()
    {
        cleanupInvalidOwnedFields();
        return allFarmFields().stream()
            .filter(field -> field != null && ownedFields.contains(field.getPosition()) && hasClimateControlHub(field.getPosition()))
            .sorted(Comparator.comparingInt(field -> field.getPosition().distManhattan(building.getID())))
            .toList();
    }

    /**
     * Find a farm field by its anchor position.
     *
     * @param fieldPosition position of the farm field anchor
     * @return the matching field, or null when none is registered
     */
    public FarmField getField(final BlockPos fieldPosition)
    {
        if (fieldPosition == null)
        {
            return null;
        }

        return allFarmFields().stream()
            .filter(field -> fieldPosition.equals(field.getPosition()))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get fields that should be displayed in the module window.
     *
     * @return owned fields and eligible unowned fields, excluding fields owned by another greenhouse
     */
    @SuppressWarnings("null")
    private List<FarmField> getVisibleFields()
    {
        if (building == null)
        {
            return List.of();
        }

        return allFarmFields().stream()
            .filter(field -> field != null && isVisibleField(field.getPosition()))
            .sorted(Comparator
                .comparing((FarmField field) -> !ownedFields.contains(field.getPosition()))
                .thenComparingInt(field -> field.getPosition().distManhattan(building.getID())))
            .toList();
    }

    /**
     * Check whether a field can appear in this greenhouse's biome module.
     *
     * @param fieldPosition position of the farm field anchor
     * @return true when the field is owned here or eligible to be claimed here
     */
    private boolean isVisibleField(final BlockPos fieldPosition)
    {
        if (fieldPosition == null || !hasClimateControlHub(fieldPosition))
        {
            return false;
        }

        return ownedFields.contains(fieldPosition) || isEligibleUnownedField(fieldPosition);
    }

    /**
     * Check whether an unowned field can be claimed by this greenhouse.
     *
     * @param fieldPosition position of the farm field anchor
     * @return true when the field has a hub and no other greenhouse owns it
     */
    private boolean isEligibleUnownedField(final BlockPos fieldPosition)
    {
        return fieldPosition != null && hasClimateControlHub(fieldPosition) && !isOwnedByAnotherGreenhouse(fieldPosition);
    }

    /**
     * Check whether another greenhouse in the colony owns a field.
     *
     * @param fieldPosition position of the farm field anchor
     * @return true when a different greenhouse module owns the field
     */
    private boolean isOwnedByAnotherGreenhouse(final BlockPos fieldPosition)
    {
        if (building == null || fieldPosition == null)
        {
            return false;
        }

        for (final IBuilding candidate : building.getColony().getServerBuildingManager().getBuildings().values())
        {
            if (candidate == null || building.getID().equals(candidate.getID()))
            {
                continue;
            }

            final GreenhouseBiomeModule module = candidate.getModule(GreenhouseBiomeModule.class, ignored -> true);
            if (module != null && module.isOwned(fieldPosition))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Get all registered MineColonies farm field extensions in this colony.
     *
     * @return registered farm field extensions
     */
    private List<FarmField> allFarmFields()
    {
        if (building == null)
        {
            return List.of();
        }

        final List<FarmField> fields = new ArrayList<>();
        for (final IBuildingExtension extension : building.getColony()
            .getServerBuildingManager()
            .getBuildingExtensions(field -> field instanceof FarmField))
        {
            fields.add((FarmField) extension);
        }
        return fields;
    }

    /**
     * Remove ownership for any field whose climate hub has been removed.
     */
    public void cleanupInvalidOwnedFields()
    {
        if (building == null || ownedFields.isEmpty())
        {
            return;
        }

        final List<BlockPos> invalidFields = ownedFields.stream()
            .filter(pos -> !hasClimateControlHub(pos))
            .sorted(Comparator.comparing(BlockPos::asLong))
            .toList();
        if (invalidFields.isEmpty())
        {
            return;
        }

        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule releasing {} invalid owned fields with missing hubs: {}.",
            colonyId(), invalidFields.size(), invalidFields.stream().map(GreenhouseBiomeModule::formatBlockPos).toList()));
        invalidFields.forEach(pos -> {
            releaseOwnedField(pos);
        });
        markDirty();
        building.markDirty();
    }

    /**
     * Release an owned field, restore its natural biome, and clear seeds that no longer fit.
     *
     * @param fieldPosition position of the farm field anchor
     * @return true when ownership changed
     */
    private boolean releaseOwnedField(final BlockPos fieldPosition)
    {
        if (!ownedFields.remove(fieldPosition))
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule ignored release for unowned field {}.",
                colonyId(), formatBlockPos(fieldPosition)));
            return false;
        }

        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule releasing owned field {}; restoring overlay and clearing tracking.",
            colonyId(), formatBlockPos(fieldPosition)));
        restoreFieldOverlay(fieldPosition);
        clearFieldOverlayTracking(fieldPosition);
        clearInvalidSeedForActualBiome(getServerLevel(), getField(fieldPosition));
        setHubVisualClimate(fieldPosition, VisualClimate.INACTIVE);
        assignments.remove(fieldPosition);
        lastConvertedDays.remove(fieldPosition);
        lastFieldVisitDays.remove(fieldPosition);
        lastMaintenanceVisitDays.remove(fieldPosition);
        lastConversionBlockedDays.remove(fieldPosition);
        lastRevertedDays.remove(fieldPosition);
        firstMissedMaintenanceDays.remove(fieldPosition);
        lastMaintenanceWarningDays.remove(fieldPosition);
        lastBiomeContentionWarningDays.keySet().removeIf(key -> key.contains(fieldPosition));
        markDirty();
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule released field {}; owned fields remaining {}.",
            colonyId(), formatBlockPos(fieldPosition), ownedFields.size()));
        return true;
    }

    /**
     * Drop persisted overlay bookkeeping for one field after its overlay lifecycle ends.
     *
     * @param fieldPosition position of the farm field anchor
     */
    private void clearFieldOverlayTracking(final BlockPos fieldPosition)
    {
        final FarmField field = getField(fieldPosition);
        if (field == null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule could not clear overlay tracking for {}; field no longer exists.",
                colonyId(), formatBlockPos(fieldPosition)));
            return;
        }

        final BoundingBox region = biomeFootprint(field).paddedBiomeRegion();
        final List<BoundingBox> protectedRegions = protectedOverlayFootprints(fieldPosition).stream()
            .map(FieldBiomeFootprint::paddedBiomeRegion)
            .toList();
        final Predicate<BlockPos> belongsOnlyToField = pos -> pos != null && region.isInside(pos)
            && protectedRegions.stream().noneMatch(protectedRegion -> protectedRegion.isInside(pos));

        naturalBiomes.keySet().removeIf(belongsOnlyToField);
        appliedBiomes.keySet().removeIf(belongsOnlyToField);
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule cleared overlay tracking for field {}; protected overlap regions {}.",
            colonyId(), formatBlockPos(fieldPosition), protectedRegions.size()));
    }

    /**
     * Update the visual wool color of the climate hub under a managed field.
     *
     * @param fieldPosition position of the farm field anchor
     * @param assignment climate assignment represented by the hub
     */
    private void updateHubVisualClimate(final BlockPos fieldPosition, final FieldBiomeAssignment assignment)
    {
        final ServerLevel level = getServerLevel();
        if (!isAssignmentModifiedFromNatural(level, fieldPosition, assignment))
        {
            setHubVisualClimate(fieldPosition, VisualClimate.INACTIVE);
            return;
        }

        setHubVisualClimate(fieldPosition, visualClimate(assignment));
    }

    /**
     * Apply a visual state to the climate hub under a field without changing waterlogging.
     *
     * @param fieldPosition position of the farm field anchor
     * @param visualClimate visual climate state to show
     */
    @SuppressWarnings("null")
    private void setHubVisualClimate(final BlockPos fieldPosition, final VisualClimate visualClimate)
    {
        final ServerLevel level = getServerLevel();
        if (level == null || fieldPosition == null || visualClimate == null)
        {
            return;
        }

        final BlockPos hubPosition = fieldPosition.below();

        if (hubPosition == null) return;

        final BlockState hubState = level.getBlockState(hubPosition);
        if (!hubState.is(ModBlocks.climateControlHub.get()) || hubState.getValue(BlockClimateControlHub.CLIMATE) == visualClimate)
        {
            return;
        }

        level.setBlock(hubPosition, hubState.setValue(BlockClimateControlHub.CLIMATE, visualClimate), 3);
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule set climate hub {} for field {} to {}.",
            colonyId(), formatBlockPos(hubPosition), formatBlockPos(fieldPosition), visualClimate));
    }

    /**
     * Check whether the field anchor has a climate control hub directly underneath it.
     *
     * @param fieldPosition position of the farm field anchor
     * @return true when the block below is the climate control hub
     */
    @SuppressWarnings("null")
    private boolean hasClimateControlHub(final BlockPos fieldPosition)
    {
        if (building == null || fieldPosition == null)
        {
            return false;
        }

        final Level level = building.getColony().getWorld();
        return level != null && level.getBlockState(fieldPosition.below()).is(ModBlocks.climateControlHub.get());
    }

    /**
     * Restore this module's biome overlay for a single field footprint.
     *
     * @param fieldPosition position of the farm field anchor
     */
    private void restoreFieldOverlay(final BlockPos fieldPosition)
    {
        if (building == null || fieldPosition == null)
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("BiomeModule skipped overlay restore for field {}; building present? {}.",
                formatBlockPos(fieldPosition), building != null));
            return;
        }

        final Level level = building.getColony().getWorld();
        final FarmField field = getField(fieldPosition);
        if (field == null || !(level instanceof ServerLevel serverLevel))
        {
            trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule skipped overlay restore for field {}; field present? {}, server level? {}.",
                colonyId(), formatBlockPos(fieldPosition), field != null, level instanceof ServerLevel));
            return;
        }

        final List<FieldBiomeFootprint> protectedFootprints = protectedOverlayFootprints(fieldPosition);
        trace(() -> GreenhouseGardenerMod.LOGGER.info("Colony {} - BiomeModule restoring overlay for field {}; tracked cells {}, protected fields {}.",
            colonyId(), formatBlockPos(fieldPosition), appliedBiomes.size(), protectedFootprints.size()));
        GreenhouseBiomeOverlayService.restoreOverlay(
            serverLevel,
            biomeFootprint(field),
            protectedFootprints,
            naturalBiomes,
            appliedBiomes);
    }

    /**
     * Get other owned field footprints whose biome cells should survive one field's reversion or release.
     *
     * @param restoredFieldPosition field currently being restored to its natural biome
     * @return footprints for still-managed fields other than the restored field
     */
    private List<FieldBiomeFootprint> protectedOverlayFootprints(final BlockPos restoredFieldPosition)
    {
        return ownedFields.stream()
            .filter(position -> !position.equals(restoredFieldPosition))
            .map(this::getField)
            .filter(field -> field != null && hasClimateControlHub(field.getPosition()))
            .map(GreenhouseBiomeModule::biomeFootprint)
            .toList();
    }

    /**
     * Clear the selected crop for one reverted field without touching other managed fields.
     *
     * @param field reverted field whose seed assignment should be removed
     */
    private boolean clearFieldSeed(final FarmField field)
    {
        if (field == null)
        {
            return false;
        }

        unassignFieldFromFarmer(field);

        final ItemStack seed = field.getSeed();
        if (seed == null || seed.isEmpty())
        {
            return false;
        }

        clearFieldSeedAndResetStage(field);

        return true;
    }

    /**
     * Clear the selected crop and restart the field's work cycle.
     *
     * @param field field whose selected crop should be removed
     */
    private void clearFieldSeedAndResetStage(final FarmField field)
    {
        field.setSeed(ItemStack.EMPTY);
        field.setFieldStage(FarmField.Stage.EMPTY);
    }

    /**
     * Release a now-unseeded field from its farmer assignment.
     *
     * @param field field whose farmer assignment should be cleared
     */
    private void unassignFieldFromFarmer(final FarmField field)
    {
        if (field.getBuildingId() == null || building == null || building.getColony() == null)
        {
            return;
        }

        final IBuilding assignedBuilding = building.getColony().getServerBuildingManager().getBuilding(field.getBuildingId());
        if (assignedBuilding == null)
        {
            return;
        }

        final FarmerFieldsModule farmerFieldsModule = assignedBuilding.getModule(com.minecolonies.core.colony.buildings.modules.BuildingModules.FARMER_FIELDS);
        if (farmerFieldsModule != null)
        {
            farmerFieldsModule.freeExtension(field);
        }
    }

    /**
     * Check whether this field still has biome cells tracked as greenhouse-applied.
     *
     * @param field field to inspect
     * @return true when at least one applied biome cell belongs to this field footprint
     */
    public boolean hasTrackedOverlay(final FarmField field)
    {
        if (field == null || appliedBiomes.isEmpty())
        {
            return false;
        }

        final BoundingBox region = biomeFootprint(field).paddedBiomeRegion();
        return appliedBiomes.keySet().stream().anyMatch(region::isInside);
    }

    /**
     * Resolve the latest successful climate work day for a field.
     *
     * @param fieldPosition field anchor position
     * @return last successful maintenance or conversion day, or null when never converted
     */
    private Long lastRecordedMaintenanceDay(final BlockPos fieldPosition)
    {
        final Long firstMissedDay = firstMissedMaintenanceDays.get(fieldPosition);
        if (firstMissedDay != null)
        {
            return firstMissedDay - 1;
        }

        return lastMaintenanceVisitDays.containsKey(fieldPosition)
            ? lastMaintenanceVisitDays.get(fieldPosition)
            : lastConvertedDays.get(fieldPosition);
    }

    /**
     * Resolve the single source of truth for maintenance decay math.
     *
     * @param fieldPosition field anchor position
     * @param colonyDay current colony day
     * @return decay status, or null when the field has never been converted or maintained
     */
    private MaintenanceDecayStatus maintenanceDecayStatus(final BlockPos fieldPosition, final long colonyDay)
    {
        final Long lastMaintenanceDay = lastRecordedMaintenanceDay(fieldPosition);
        if (lastMaintenanceDay == null)
        {
            return null;
        }

        final int daysSinceMaintenance = (int) Math.max(0, colonyDay - lastMaintenanceDay);
        return new MaintenanceDecayStatus(
            lastMaintenanceDay,
            colonyDay,
            daysSinceMaintenance,
            Math.max(0, maintenanceRevertDays() - daysSinceMaintenance));
    }

    /**
     * Maintenance decay status for a field on a colony day.
     *
     * @param lastMaintenanceDay last successful climate maintenance/conversion day
     * @param colonyDay current colony day
     * @param daysSinceMaintenance days since the last successful maintenance/conversion
     * @param daysUntilReversion days left before natural reversion
     */
    private record MaintenanceDecayStatus(long lastMaintenanceDay, long colonyDay, int daysSinceMaintenance, int daysUntilReversion)
    {
        private boolean shouldRevert()
        {
            return daysSinceMaintenance >= maintenanceRevertDays();
        }

        private boolean shouldWarn()
        {
            return daysSinceMaintenance >= 2;
        }

        private boolean isConditioningActive()
        {
            return daysSinceMaintenance <= 1;
        }
    }

    /**
     * Clear invalid seeds for every field currently owned by this module.
     *
     * @param level server level containing the owned fields
     */
    private void clearInvalidOwnedFieldSeedsForActualBiomes(final ServerLevel level)
    {
        boolean changed = false;
        for (final BlockPos fieldPosition : ownedFields)
        {
            changed |= clearInvalidSeedForActualBiome(level, getField(fieldPosition));
        }

        if (changed)
        {
            markDirty();
        }
    }

    /**
     * Clear a field's seed for its saved target climate.
     *
     * @param fieldPosition position of the farm field anchor
     */
    private void clearInvalidSeedForAssignedClimate(final BlockPos fieldPosition)
    {
        clearInvalidSeedForClimate(getServerLevel(), getField(fieldPosition), climate(getAssignment(fieldPosition)));
    }

    /**
     * Resolve this building's world as a server level.
     *
     * @return server level, or null when unavailable
     */
    private ServerLevel getServerLevel()
    {
        if (building == null)
        {
            return null;
        }

        final Level level = building.getColony().getWorld();
        return level instanceof ServerLevel serverLevel && !serverLevel.isClientSide() ? serverLevel : null;
    }

    /**
     * Check whether a configured field seed can grow in a biome.
     *
     * @param seed selected field seed
     * @param biome biome to test
     * @return true for vanilla/non-exotic seeds or compatible MineColonies crop seeds
     */
    private static boolean canSeedGrowInBiome(final ItemStack seed, final Holder<Biome> biome)
    {
        if (seed == null || seed.isEmpty() || !(seed.getItem() instanceof ItemCrop itemCrop))
        {
            return true;
        }

        return biome != null && itemCrop.canBePlantedIn(biome);
    }

    /**
     * Resolve a biome holder without throwing when configuration is invalid.
     *
     * @param level server level containing biome registries
     * @param biomeId biome id to resolve
     * @return biome holder, or null when unavailable
     */
    @SuppressWarnings("null")
    private static Holder.Reference<Biome> biomeHolder(final ServerLevel level, final ResourceLocation biomeId)
    {
        if (level == null || biomeId == null)
        {
            return null;
        }

        return level.registryAccess()
            .registryOrThrow(Registries.BIOME)
            .getHolder(ResourceKey.create(Registries.BIOME, biomeId))
            .orElse(null);
    }

    /**
     * Convert a field biome assignment into shared greenhouse climate settings.
     *
     * @param assignment field biome assignment
     * @return shared greenhouse climate settings
     */
    private static GreenhouseClimate climate(final FieldBiomeAssignment assignment)
    {
        return new GreenhouseClimate(assignment.temperature(), assignment.humidity());
    }

    /**
     * Convert a field biome assignment into the corresponding climate hub visual.
     *
     * @param assignment field biome assignment
     * @return wool color state for the climate hub
     */
    private static VisualClimate visualClimate(final FieldBiomeAssignment assignment)
    {
        return switch (assignment.temperature())
        {
            case COLD -> switch (assignment.humidity())
            {
                case DRY -> VisualClimate.COLD_DRY;
                case NORMAL -> VisualClimate.COLD_NORMAL;
                case HUMID -> VisualClimate.COLD_HUMID;
            };
            case TEMPERATE -> switch (assignment.humidity())
            {
                case DRY -> VisualClimate.TEMPERATE_DRY;
                case NORMAL -> VisualClimate.TEMPERATE_NORMAL;
                case HUMID -> VisualClimate.TEMPERATE_HUMID;
            };
            case HOT -> switch (assignment.humidity())
            {
                case DRY -> VisualClimate.HOT_DRY;
                case NORMAL -> VisualClimate.HOT_NORMAL;
                case HUMID -> VisualClimate.HOT_HUMID;
            };
        };
    }

    /**
     * Build a field assignment matching the field's natural climate.
     *
     * @param level server level containing biome data
     * @param fieldPosition position of the farm field anchor
     * @return an assignment that does not consume a modified-biome slot
     */
    private FieldBiomeAssignment assignmentForNaturalClimate(final ServerLevel level, final BlockPos fieldPosition)
    {
        final GreenhouseClimate naturalClimate = naturalClimate(level, fieldPosition);
        return new FieldBiomeAssignment(naturalClimate.temperature(), naturalClimate.humidity());
    }

    /**
     * Check whether an assignment differs from the field's natural biome climate.
     *
     * @param level server level containing biome data
     * @param fieldPosition position of the farm field anchor
     * @param assignment assignment to compare
     * @return true when the assignment is not the natural climate
     */
    private boolean isAssignmentModifiedFromNatural(final ServerLevel level, final BlockPos fieldPosition, final FieldBiomeAssignment assignment)
    {
        if (fieldPosition == null || assignment == null)
        {
            return false;
        }

        final GreenhouseClimate naturalClimate = naturalClimate(level, fieldPosition);
        return assignment.temperature() != naturalClimate.temperature() || assignment.humidity() != naturalClimate.humidity();
    }

    /**
     * Resolve the field's natural climate, preferring persisted biome captures and falling back to the current world biome.
     *
     * @param level server level containing biome data
     * @param pos block position to classify
     * @return natural temperature and humidity axes
     */
    @SuppressWarnings("null")
    private GreenhouseClimate naturalClimate(final ServerLevel level, final BlockPos pos)
    {
        if (level != null)
        {
            final ResourceLocation naturalBiomeId = naturalBiomes.get(quantizedBlockPos(pos));
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

        return climate(FieldBiomeAssignment.DEFAULT);
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
     * Build the exact field footprint used by biome overlay operations.
     *
     * @param field farm field to describe
     * @return directional biome footprint
     */
    private static FieldBiomeFootprint biomeFootprint(final FarmField field)
    {
        return FieldBiomeFootprint.directional(
            field.getPosition(),
            field.getRadius(Direction.WEST),
            field.getRadius(Direction.EAST),
            field.getRadius(Direction.NORTH),
            field.getRadius(Direction.SOUTH));
    }

    private static BlockPos quantizedBlockPos(final BlockPos pos)
    {
        return new BlockPos(quantize(pos.getX()), quantize(pos.getY()), quantize(pos.getZ()));
    }

    private static int quantize(final int value)
    {
        return QuartPos.toBlock(QuartPos.fromBlock(value));
    }

    private int colonyId()
    {
        return building == null || building.getColony() == null ? -1 : building.getColony().getID();
    }

    private static String formatBlockPos(final BlockPos pos)
    {
        return pos == null ? "null" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String formatAssignment(final FieldBiomeAssignment assignment)
    {
        return assignment == null ? "null" : formatAssignment(assignment.temperature(), assignment.humidity());
    }

    private static String formatAssignment(final TemperatureSetting temperature, final HumiditySetting humidity)
    {
        return String.valueOf(temperature) + "/" + String.valueOf(humidity);
    }

    /**
     * Read the tag information from the biome map.
     *
     * @param tags serialized biome tags
     * @param target map receiving deserialized biome ids
     */
    private static void readBiomeMap(final ListTag tags, final Map<BlockPos, ResourceLocation> target)
    {
        for (final Tag tag : tags)
        {
            final CompoundTag biomeTag = (CompoundTag) tag;

            if (biomeTag == null)
            {
                continue;
            }

            NbtUtils.readBlockPos(biomeTag, TAG_POS).ifPresent(pos -> {
                final ResourceLocation biome = ResourceLocation.tryParse(biomeTag.getString(TAG_BIOME) + "");
                if (biome != null)
                {
                    target.put(pos, biome);
                }
            });
        }
    }

    /**
     * Write the tag information for a biome map.
     *
     * @param source biome map to serialize
     * @return serialized biome tag list
     */
    @SuppressWarnings("null")
    private static ListTag writeBiomeMap(final Map<BlockPos, ResourceLocation> source)
    {
        final ListTag tags = new ListTag();
        source.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().asLong())).forEach(entry -> {
            final CompoundTag biomeTag = new CompoundTag();
            biomeTag.put(TAG_POS, NbtUtils.writeBlockPos(entry.getKey()));
            biomeTag.putString(TAG_BIOME, entry.getValue().toString());
            tags.add(biomeTag);
        });
        return tags;
    }

    /**
     * Read persisted colony-day values keyed by field position.
     *
     * @param tags serialized day tags
     * @param target map receiving deserialized days
     */
    private static void readDayMap(final ListTag tags, final Map<BlockPos, Long> target)
    {
        for (final Tag tag : tags)
        {
            final CompoundTag dayTag = (CompoundTag) tag;
            if (dayTag == null)
            {
                continue;
            }

            NbtUtils.readBlockPos(dayTag, TAG_POS).ifPresent(pos -> target.put(pos.immutable(), dayTag.getLong(TAG_DAY)));
        }
    }

    /**
     * Write persisted colony-day values keyed by field position.
     *
     * @param source day map to serialize
     * @return serialized day tag list
     */
    @SuppressWarnings("null")
    private static ListTag writeDayMap(final Map<BlockPos, Long> source)
    {
        final ListTag tags = new ListTag();
        source.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().asLong())).forEach(entry -> {
            final CompoundTag dayTag = new CompoundTag();
            dayTag.put(TAG_POS, NbtUtils.writeBlockPos(entry.getKey()));
            dayTag.putLong(TAG_DAY, entry.getValue());
            tags.add(dayTag);
        });
        return tags;
    }

    /**
     * Read persisted colony-day values keyed by ordered field-position pairs.
     *
     * @param tags serialized pair-day tags
     * @param target map receiving deserialized days
     */
    private static void readFieldPairDayMap(final ListTag tags, final Map<FieldPairKey, Long> target)
    {
        for (final Tag tag : tags)
        {
            final CompoundTag dayTag = (CompoundTag) tag;
            if (dayTag == null)
            {
                continue;
            }

            final Optional<BlockPos> first = NbtUtils.readBlockPos(dayTag, TAG_FIRST_FIELD_POS);
            final Optional<BlockPos> second = NbtUtils.readBlockPos(dayTag, TAG_SECOND_FIELD_POS);
            if (first.isPresent() && second.isPresent())
            {
                final FieldPairKey key = FieldPairKey.of(first.get(), second.get());
                if (key != null)
                {
                    target.put(key, dayTag.getLong(TAG_DAY));
                }
            }
        }
    }

    /**
     * Write persisted colony-day values keyed by ordered field-position pairs.
     *
     * @param source pair-day map to serialize
     * @return serialized pair-day tag list
     */
    @SuppressWarnings("null")
    private static ListTag writeFieldPairDayMap(final Map<FieldPairKey, Long> source)
    {
        final ListTag tags = new ListTag();
        source.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().first().asLong()))
            .forEach(entry -> {
                final CompoundTag dayTag = new CompoundTag();
                dayTag.put(TAG_FIRST_FIELD_POS, NbtUtils.writeBlockPos(entry.getKey().first()));
                dayTag.put(TAG_SECOND_FIELD_POS, NbtUtils.writeBlockPos(entry.getKey().second()));
                dayTag.putLong(TAG_DAY, entry.getValue());
                tags.add(dayTag);
            });
        return tags;
    }

    private static boolean dayEquals(final Long storedDay, final long colonyDay)
    {
        return storedDay != null && storedDay == colonyDay;
    }

    private record FieldPairKey(BlockPos first, BlockPos second)
    {
        private static FieldPairKey of(final BlockPos firstPosition, final BlockPos secondPosition)
        {
            if (firstPosition == null || secondPosition == null)
            {
                return null;
            }

            final BlockPos immutableFirst = firstPosition.immutable();
            final BlockPos immutableSecond = secondPosition.immutable();
            return immutableFirst.asLong() <= immutableSecond.asLong()
                ? new FieldPairKey(immutableFirst, immutableSecond)
                : new FieldPairKey(immutableSecond, immutableFirst);
        }

        private boolean contains(final BlockPos fieldPosition)
        {
            return fieldPosition != null && (first.equals(fieldPosition) || second.equals(fieldPosition));
        }
    }

    public record FieldBiomeAssignment(TemperatureSetting temperature, HumiditySetting humidity)
    {
        public static final FieldBiomeAssignment DEFAULT =
            new FieldBiomeAssignment(TemperatureSetting.TEMPERATE, HumiditySetting.NORMAL);
    }

    /**
     * Requested field state for batched biome settings saves.
     */
    public record FieldBiomeChange(BlockPos fieldPosition, FieldBiomeAssignment assignment, boolean owned)
    {
    }

    /**
     * Proposed final state for validating a batched save before any mutation occurs.
     */
    private record ProposedFieldState(Set<BlockPos> ownedFields, Map<BlockPos, FieldBiomeAssignment> assignments)
    {
    }

    public enum TemperatureSetting
    {
        COLD("cold"), TEMPERATE("temperate"), HOT("hot");

        private final String serializedName;

        TemperatureSetting(final String serializedName)
        {
            this.serializedName = serializedName;
        }

        public String getSerializedName()
        {
            return serializedName;
        }

        public static TemperatureSetting bySerializedName(final String serializedName)
        {
            for (final TemperatureSetting setting : values())
            {
                if (setting.serializedName.equals(serializedName))
                {
                    return setting;
                }
            }
            return TEMPERATE;
        }
    }

    public enum HumiditySetting
    {
        DRY("dry"), NORMAL("normal"), HUMID("humid");

        private final String serializedName;

        HumiditySetting(final String serializedName)
        {
            this.serializedName = serializedName;
        }

        public String getSerializedName()
        {
            return serializedName;
        }

        public static HumiditySetting bySerializedName(final String serializedName)
        {
            for (final HumiditySetting setting : values())
            {
                if (setting.serializedName.equals(serializedName))
                {
                    return setting;
                }
            }
            return NORMAL;
        }
    }

    @Override
    public void alterItemsToBeKept(final TriConsumer<Predicate<ItemStack>, Integer, Boolean> consumer)
    {
        consumer.accept(GreenhouseBiomeModule::isBiomeModifierItem, Integer.MAX_VALUE, false);
    }

    private static boolean isBiomeModifierItem(final ItemStack stack)
    {
        return GreenhouseClimateItemValueListener.INSTANCE.hasAnyValue(stack);
    }
}
