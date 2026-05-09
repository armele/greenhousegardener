package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.core.blocks.ModBlocks;
import com.deathfrog.greenhousegardener.ModResearch;
import com.deathfrog.greenhousegardener.core.ModTags;
import com.deathfrog.greenhousegardener.core.blocks.BlockClimateControlHub;
import com.deathfrog.greenhousegardener.core.blocks.BlockClimateControlHub.VisualClimate;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseClimateItemModule.ClimateItemList;
import com.deathfrog.greenhousegardener.core.world.biomeservice.FieldBiomeFootprint;
import com.deathfrog.greenhousegardener.core.world.biomeservice.GreenhouseBiomeOverlayService;
import com.deathfrog.greenhousegardener.core.world.biomeservice.GreenhouseClimate;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IAltersRequiredItems;
import com.minecolonies.api.colony.buildings.modules.IBuildingEventsModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.core.colony.buildingextensions.FarmField;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;

public class GreenhouseBiomeModule extends AbstractBuildingModule implements IPersistentModule, IBuildingEventsModule, IAltersRequiredItems
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
    private static final String TAG_FIRST_MISSED_MAINTENANCE_DAYS = "firstMissedMaintenanceDays";
    private static final String TAG_DAY = "day";
    private static final String TAG_NATURAL_BIOMES = "naturalBiomes";
    private static final String TAG_APPLIED_BIOMES = "appliedBiomes";
    private static final String TAG_POS = "pos";
    private static final String TAG_BIOME = "biome";

    private final Map<BlockPos, FieldBiomeAssignment> assignments = new HashMap<>();
    private final Set<BlockPos> ownedFields = new HashSet<>();
    private final Map<BlockPos, Long> lastConvertedDays = new HashMap<>();
    private final Map<BlockPos, Long> lastFieldVisitDays = new HashMap<>();
    private final Map<BlockPos, Long> lastMaintenanceVisitDays = new HashMap<>();
    private final Map<BlockPos, Long> lastConversionBlockedDays = new HashMap<>();
    private final Map<BlockPos, Long> firstMissedMaintenanceDays = new HashMap<>();
    private final Map<BlockPos, ResourceLocation> naturalBiomes = new HashMap<>();
    private final Map<BlockPos, ResourceLocation> appliedBiomes = new HashMap<>();

    public GreenhouseBiomeModule()
    {
        super();
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

        firstMissedMaintenanceDays.clear();
        readDayMap(compound.getList(TAG_FIRST_MISSED_MAINTENANCE_DAYS, Tag.TAG_COMPOUND), firstMissedMaintenanceDays);
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
        compound.put(TAG_FIRST_MISSED_MAINTENANCE_DAYS, writeDayMap(firstMissedMaintenanceDays));
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
            buf.writeInt(i);
            buf.writeBlockPos(fieldPosition);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, field.getSeed());
            buf.writeEnum(assignment.temperature());
            buf.writeEnum(assignment.humidity());
            buf.writeEnum(naturalClimate.temperature());
            buf.writeEnum(naturalClimate.humidity());
            buf.writeBoolean(isOwned(fieldPosition));
        }
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
            return;
        }

        final BlockPos immutablePosition = fieldPosition.immutable();
        final FieldBiomeAssignment current = getAssignment(immutablePosition);
        if (current.temperature() == temperature && current.humidity() == humidity)
        {
            clearInvalidSeedForAssignedClimate(immutablePosition);
            updateHubVisualClimate(immutablePosition, current);
            return;
        }

        final FieldBiomeAssignment assignment = new FieldBiomeAssignment(temperature, humidity);
        if (!canSetAssignment(immutablePosition, assignment))
        {
            return;
        }

        assignments.put(immutablePosition, assignment);
        clearInvalidSeedForClimate(getServerLevel(), getField(immutablePosition), climate(assignment));
        updateHubVisualClimate(immutablePosition, assignment);
        markDirty();
    }

    /**
     * Get the requested climate assignment for a field.
     *
     * @param fieldPosition position of the farm field anchor
     * @return the saved assignment, or the default climate assignment
     */
    public FieldBiomeAssignment getAssignment(final BlockPos fieldPosition)
    {
        return assignments.getOrDefault(fieldPosition, FieldBiomeAssignment.DEFAULT);
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
            return false;
        }

        final BlockPos immutablePosition = fieldPosition.immutable();
        if (owned)
        {
            if (ownedFields.contains(immutablePosition))
            {
                return false;
            }
            if (ownedFields.size() >= getSupportedFieldCount() || !isEligibleUnownedField(immutablePosition))
            {
                return false;
            }

            ownedFields.add(immutablePosition);
            final ServerLevel level = getServerLevel();
            final FieldBiomeAssignment assignment = assignments.computeIfAbsent(immutablePosition, ignored -> assignmentForNaturalClimate(level, immutablePosition));
            clearInvalidSeedForClimate(level, getField(immutablePosition), climate(assignment));
            updateHubVisualClimate(immutablePosition, assignment);
            markDirty();
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
        int count = 0;
        for (final BlockPos fieldPosition : ownedFields)
        {
            if (isAssignmentModifiedFromNatural(level, fieldPosition, getAssignment(fieldPosition)))
            {
                count++;
            }
        }
        return count;
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
        final boolean currentlyModified = isAssignmentModifiedFromNatural(level, fieldPosition, getAssignment(fieldPosition));
        final boolean requestedModified = isAssignmentModifiedFromNatural(level, fieldPosition, assignment);
        return currentlyModified || !requestedModified || getModifiedBiomeCount(level) < getModifiedBiomeLimit();
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
        firstMissedMaintenanceDays.remove(immutablePosition);
        markDirty();
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
        markDirty();
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
        lastFieldVisitDays.put(immutablePosition, colonyDay);
        lastMaintenanceVisitDays.put(immutablePosition, colonyDay);
        final long firstMissedDay = firstMissedMaintenanceDays.computeIfAbsent(immutablePosition, ignored -> colonyDay);
        markDirty();
        return firstMissedDay;
    }

    /**
     * Restore an owned field's overlay to the natural biome and clear its missed-maintenance streak.
     *
     * @param fieldPosition position of the farm field anchor
     */
    public void revertFieldToNaturalBiome(final BlockPos fieldPosition)
    {
        if (fieldPosition == null)
        {
            return;
        }

        restoreFieldOverlay(fieldPosition);
        firstMissedMaintenanceDays.remove(fieldPosition);
        markDirty();
    }

    /**
     * Determine whether a field's configured seed can grow in its current biome.
     *
     * @param level server level containing the field
     * @param field field to inspect
     * @return true when an exotic MineColonies crop seed cannot grow at the field anchor
     */
    public boolean needsSeedUnsetForActualBiome(final ServerLevel level, final FarmField field)
    {
        BlockPos pos =  field.getPosition();

        if (level == null || field == null || pos == null)
        {
            return false;
        }

        return !canSeedGrowInBiome(field.getSeed(), level.getBiome(pos));
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

        field.setSeed(ItemStack.EMPTY);
        markDirty();
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

        field.setSeed(ItemStack.EMPTY);
        markDirty();
        return true;
    }

    @Override
    public void onDestroyed()
    {
        final ServerLevel level = getServerLevel();
        if (level == null)
        {
            ownedFields.clear();
            assignments.clear();
            return;
        }

        GreenhouseBiomeOverlayService.restoreAllOverlays(level, naturalBiomes, appliedBiomes);
        clearInvalidOwnedFieldSeedsForActualBiomes(level);
        ownedFields.clear();
        assignments.clear();
        lastConvertedDays.clear();
        lastFieldVisitDays.clear();
        lastMaintenanceVisitDays.clear();
        lastConversionBlockedDays.clear();
        firstMissedMaintenanceDays.clear();
        markDirty();
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
            return false;
        }

        restoreFieldOverlay(fieldPosition);
        clearInvalidSeedForActualBiome(getServerLevel(), getField(fieldPosition));
        setHubVisualClimate(fieldPosition, VisualClimate.INACTIVE);
        assignments.remove(fieldPosition);
        lastConvertedDays.remove(fieldPosition);
        lastFieldVisitDays.remove(fieldPosition);
        lastMaintenanceVisitDays.remove(fieldPosition);
        lastConversionBlockedDays.remove(fieldPosition);
        firstMissedMaintenanceDays.remove(fieldPosition);
        markDirty();
        return true;
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
            return;
        }

        final Level level = building.getColony().getWorld();
        final FarmField field = getField(fieldPosition);
        if (field == null || !(level instanceof ServerLevel serverLevel))
        {
            return;
        }

        GreenhouseBiomeOverlayService.restoreOverlay(serverLevel, biomeFootprint(field), naturalBiomes, appliedBiomes);
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

    private static boolean dayEquals(final Long storedDay, final long colonyDay)
    {
        return storedDay != null && storedDay == colonyDay;
    }

    public record FieldBiomeAssignment(TemperatureSetting temperature, HumiditySetting humidity)
    {
        public static final FieldBiomeAssignment DEFAULT =
            new FieldBiomeAssignment(TemperatureSetting.TEMPERATE, HumiditySetting.NORMAL);
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
        return !stack.isEmpty()
            && (stack.is(ModTags.ITEMS.GREENHOUSE_TEMP_INCREASE)
                || stack.is(ModTags.ITEMS.GREENHOUSE_TEMP_DECREASE)
                || stack.is(ModTags.ITEMS.GREENHOUSE_HUMIDITY_INCREASE)
                || stack.is(ModTags.ITEMS.GREENHOUSE_HUMIDITY_DECREASE));
    }
}
