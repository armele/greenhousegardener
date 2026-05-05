package com.deathfrog.greenhousegardener.core.colony.buildings.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.core.blocks.BlockScarecrow;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class GreenhouseBiomeModule extends AbstractBuildingModule implements IPersistentModule
{
    private static final int MAX_FIELD_SLOTS = 4;

    private static final String TAG_ASSIGNMENTS = "assignments";
    private static final String TAG_FIELD = "field";
    private static final String TAG_TEMPERATURE = "temperature";
    private static final String TAG_HUMIDITY = "humidity";
    private static final String TAG_NATURAL_BIOMES = "naturalBiomes";
    private static final String TAG_APPLIED_BIOMES = "appliedBiomes";
    private static final String TAG_POS = "pos";
    private static final String TAG_BIOME = "biome";

    private final Map<Integer, FieldBiomeAssignment> assignments = new HashMap<>();
    private final Map<net.minecraft.core.BlockPos, ResourceLocation> naturalBiomes = new HashMap<>();
    private final Map<net.minecraft.core.BlockPos, ResourceLocation> appliedBiomes = new HashMap<>();

    public GreenhouseBiomeModule()
    {
        super();
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        assignments.clear();
        for (final Tag tag : compound.getList(TAG_ASSIGNMENTS, Tag.TAG_COMPOUND))
        {
            final CompoundTag assignmentTag = (CompoundTag) tag;
            final int fieldIndex = assignmentTag.getInt(TAG_FIELD);
            if (fieldIndex >= 0 && fieldIndex < MAX_FIELD_SLOTS)
            {
                assignments.put(fieldIndex,
                    new FieldBiomeAssignment(TemperatureSetting.bySerializedName(assignmentTag.getString(TAG_TEMPERATURE)),
                        HumiditySetting.bySerializedName(assignmentTag.getString(TAG_HUMIDITY))));
            }
        }

        naturalBiomes.clear();
        readBiomeMap(compound.getList(TAG_NATURAL_BIOMES, Tag.TAG_COMPOUND), naturalBiomes);

        appliedBiomes.clear();
        readBiomeMap(compound.getList(TAG_APPLIED_BIOMES, Tag.TAG_COMPOUND), appliedBiomes);
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        final ListTag assignmentTags = new ListTag();
        assignments.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            final CompoundTag assignmentTag = new CompoundTag();
            assignmentTag.putInt(TAG_FIELD, entry.getKey());
            assignmentTag.putString(TAG_TEMPERATURE, entry.getValue().temperature().getSerializedName() + "");
            assignmentTag.putString(TAG_HUMIDITY, entry.getValue().humidity().getSerializedName() + "");
            assignmentTags.add(assignmentTag);
        });
        compound.put(TAG_ASSIGNMENTS, assignmentTags);

        ListTag natural = writeBiomeMap(naturalBiomes);
        if (natural != null)
        {
            compound.put(TAG_NATURAL_BIOMES, natural);
        }

        ListTag altered = writeBiomeMap(appliedBiomes);
        if (altered != null)
        {
            compound.put(TAG_APPLIED_BIOMES, altered);
        }
    }

    @SuppressWarnings("null")
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        final int supportedFields = getSupportedFieldCount();
        ensureFieldExtensionsRegistered(supportedFields);
        final List<FarmField> fields = getManagedFields(supportedFields);

        buf.writeInt(supportedFields);
        buf.writeInt(fields.size());
        for (int i = 0; i < fields.size(); i++)
        {
            final FarmField field = fields.get(i);
            final FieldBiomeAssignment assignment = getAssignment(i);
            buf.writeInt(i);
            buf.writeBlockPos(field.getPosition());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, field.getSeed());
            buf.writeEnum(assignment.temperature());
            buf.writeEnum(assignment.humidity());
        }
    }

    public void setAssignment(final int fieldIndex, final TemperatureSetting temperature, final HumiditySetting humidity)
    {
        if (fieldIndex < 0 || fieldIndex >= getSupportedFieldCount())
        {
            return;
        }

        final FieldBiomeAssignment current = getAssignment(fieldIndex);
        if (current.temperature() == temperature && current.humidity() == humidity)
        {
            return;
        }

        assignments.put(fieldIndex, new FieldBiomeAssignment(temperature, humidity));
        markDirty();
    }

    public FieldBiomeAssignment getAssignment(final int fieldIndex)
    {
        return assignments.getOrDefault(fieldIndex, FieldBiomeAssignment.DEFAULT);
    }

    /**
     * Get the persisted natural biome cells captured before greenhouse overlays were applied.
     *
     * @return mutable map of quantized biome cell positions to natural biome ids
     */
    public Map<net.minecraft.core.BlockPos, ResourceLocation> getNaturalBiomes()
    {
        return naturalBiomes;
    }

    /**
     * Get the persisted biome cells last written by the greenhouse overlay service.
     *
     * @return mutable map of quantized biome cell positions to applied biome ids
     */
    public Map<net.minecraft.core.BlockPos, ResourceLocation> getAppliedBiomes()
    {
        return appliedBiomes;
    }

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
     * Get the fields this greenhouse can currently manage.
     *
     * @return sorted list of field extensions inside the greenhouse bounds
     */
    public List<FarmField> getManagedFields()
    {
        final int supportedFields = getSupportedFieldCount();
        ensureFieldExtensionsRegistered(supportedFields);
        return getManagedFields(supportedFields);
    }

    /**
     * Get the list of managed fields.
     * 
     * @param supportedFields
     * @return
     */
    @SuppressWarnings("null")
    private List<FarmField> getManagedFields(final int supportedFields)
    {
        if (building == null || supportedFields <= 0)
        {
            return List.of();
        }

        final List<FarmField> fields = new ArrayList<>();
        for (final IBuildingExtension extension : building.getColony()
            .getServerBuildingManager()
            .getBuildingExtensions(field -> field instanceof FarmField && building.isInBuilding(field.getPosition())))
        {
            fields.add((FarmField) extension);
        }

        return fields.stream()
            .sorted(Comparator.comparingInt(field -> field.getPosition().distManhattan(building.getID())))
            .limit(supportedFields)
            .toList();
    }

    /**
     * Re-register scarecrow fields inside the greenhouse when MineColonies has not
     * already tracked them. This is run when the module view is requested, not on a
     * colony tick.
     *
     * @param supportedFields maximum number of field slots this greenhouse can use
     */
    @SuppressWarnings("null")
    private void ensureFieldExtensionsRegistered(final int supportedFields)
    {
        if (building == null || supportedFields <= 0 || getManagedFields(supportedFields).size() >= supportedFields)
        {
            return;
        }

        final Level level = building.getColony().getWorld();
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        final BlockPos min = corners.getA().offset(-1, -1, -1);
        final BlockPos max = corners.getB().offset(1, 1, 1);

        for (final BlockPos pos : BlockPos.betweenClosed(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()))
        {
            if (pos == null || !building.isInBuilding(pos))
            {
                continue;
            }

            final BlockState state = level.getBlockState(pos);
            
            if (state.is(ModBlocks.blockScarecrow) && state.getValue(BlockScarecrow.HALF) == DoubleBlockHalf.LOWER)
            {
                final FarmField field = FarmField.create(pos.immutable(), level);
                if (field != null)
                {
                    building.getColony().getServerBuildingManager().addBuildingExtension(field);
                }
            }
        }
    }

    /*
     * Read the tag information from the biome map.
     */
    private static void readBiomeMap(final ListTag tags, final Map<net.minecraft.core.BlockPos, ResourceLocation> target)
    {
        for (final Tag tag : tags)
        {
            final CompoundTag biomeTag = (CompoundTag) tag;

            if (biomeTag == null) continue;

            NbtUtils.readBlockPos(biomeTag, TAG_POS).ifPresent(pos -> {
                final ResourceLocation biome = ResourceLocation.tryParse(biomeTag.getString(TAG_BIOME) + "");
                if (biome != null)
                {
                    target.put(pos, biome);
                }
            });
        }
    }

    @SuppressWarnings("null")
    private static ListTag writeBiomeMap(final Map<net.minecraft.core.BlockPos, ResourceLocation> source)
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
}
