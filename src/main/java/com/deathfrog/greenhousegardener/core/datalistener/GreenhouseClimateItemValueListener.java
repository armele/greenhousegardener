package com.deathfrog.greenhousegardener.core.datalistener;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseClimateItemModule.ClimateModificationType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Loads datapack-defined greenhouse climate item values.
 */
public class GreenhouseClimateItemValueListener extends SimpleJsonResourceReloadListener
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String DIRECTORY = "greenhouse_climate_items";
    private static final String TAG_REPLACE = "replace";

    public static final GreenhouseClimateItemValueListener INSTANCE = new GreenhouseClimateItemValueListener();

    private final Map<ClimateModificationType, Map<Item, Integer>> itemValues = new EnumMap<>(ClimateModificationType.class);
    private final Map<ClimateModificationType, Map<TagKey<Item>, Integer>> tagValues = new EnumMap<>(ClimateModificationType.class);

    private GreenhouseClimateItemValueListener()
    {
        super(GSON, DIRECTORY);
        for (final ClimateModificationType type : ClimateModificationType.values())
        {
            itemValues.put(type, new HashMap<>());
            tagValues.put(type, new HashMap<>());
        }
    }

    @Override
    protected void apply(
        @NotNull final @Nonnull Map<ResourceLocation, JsonElement> object,
        @NotNull final @Nonnull ResourceManager resourceManager,
        @NotNull final @Nonnull ProfilerFiller profiler)
    {
        clearValues();

        object.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> loadClimateItemFile(entry.getKey(), entry.getValue()));

        int itemCount = 0;
        int tagCount = 0;
        for (final ClimateModificationType type : ClimateModificationType.values())
        {
            itemCount += itemValues.get(type).size();
            tagCount += tagValues.get(type).size();
        }

        GreenhouseGardenerMod.LOGGER.info("Loaded {} item and {} tag greenhouse climate CCU mappings", itemCount, tagCount);
    }

    /**
     * Check whether a stack has a configured climate value for any modification type.
     *
     * @param stack item stack to check
     * @return true when the stack contributes CCU to at least one climate direction
     */
    public boolean hasAnyValue(final ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return false;
        }

        for (final ClimateModificationType type : ClimateModificationType.values())
        {
            if (getValue(type, stack) > 0)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Check whether a stack has a configured climate value for the requested modification type.
     *
     * @param type climate modification type
     * @param stack item stack to check
     * @return true when the stack contributes CCU to the requested climate direction
     */
    public boolean hasValue(final ClimateModificationType type, final ItemStack stack)
    {
        return getValue(type, stack) > 0;
    }

    /**
     * Resolve a stack's configured climate value for the requested modification type.
     *
     * @param type climate modification type
     * @param stack item stack to value
     * @return CCU value for one item, or zero when the stack is not configured
     */
    @SuppressWarnings("null")
    public int getValue(final ClimateModificationType type, final ItemStack stack)
    {
        if (type == null || stack.isEmpty())
        {
            return 0;
        }

        final Integer itemValue = itemValues.get(type).get(stack.getItem());
        if (itemValue != null)
        {
            return itemValue;
        }

        int value = 0;
        for (final Map.Entry<TagKey<Item>, Integer> entry : tagValues.get(type).entrySet())
        {
            if (stack.is(entry.getKey()))
            {
                value = Math.max(value, entry.getValue());
            }
        }

        return value;
    }

    /**
     * Resolve the largest configured climate value for this stack across all directions.
     *
     * @param stack item stack to value
     * @return highest configured CCU value, or zero when the stack is not configured
     */
    public int getBestValue(final ItemStack stack)
    {
        int value = 0;
        for (final ClimateModificationType type : ClimateModificationType.values())
        {
            value = Math.max(value, getValue(type, stack));
        }
        return value;
    }

    private void loadClimateItemFile(final ResourceLocation file, final JsonElement elem)
    {
        try
        {
            if (elem == null)
            {
                return;
            }

            final JsonObject json = GsonHelper.convertToJsonObject(elem, DIRECTORY + "/" + file);
            if (json.has(TAG_REPLACE) && GsonHelper.getAsBoolean(json, TAG_REPLACE))
            {
                clearValues();
            }

            for (final ClimateModificationType type : ClimateModificationType.values())
            {
                final String key = serializedName(type);

                if (key == null) continue;

                if (json.has(key))
                {
                    JsonElement val = json.get(key);

                    if (val == null) continue;

                    loadValueMap(file, type, GsonHelper.convertToJsonObject(val, key));
                }
            }
        }
        catch (final JsonParseException | IllegalArgumentException e)
        {
            GreenhouseGardenerMod.LOGGER.error("Error parsing greenhouse climate item values {}", file, e);
        }
    }

    @SuppressWarnings("null")
    private void loadValueMap(final ResourceLocation file, final ClimateModificationType type, final JsonObject values)
    {
        for (final Map.Entry<String, JsonElement> entry : values.entrySet())
        {
            final int value = entry.getValue().getAsInt();
            if (value <= 0)
            {
                throw new JsonParseException("Climate item value must be positive in " + file + ": " + entry.getKey());
            }

            final String key = entry.getKey();
            if (key.startsWith("#"))
            {
                tagValues.get(type).put(ItemTags.create(ResourceLocation.parse(key.substring(1))), value);
                continue;
            }

            final Item item = parseItem(key)
                .orElseThrow(() -> new JsonParseException("Unknown climate item in " + file + ": " + key));
            itemValues.get(type).put(item, value);
        }
    }

    private void clearValues()
    {
        itemValues.values().forEach(Map::clear);
        tagValues.values().forEach(Map::clear);
    }

    private static Optional<Item> parseItem(final @Nonnull String itemId)
    {
        final ResourceLocation location = ResourceLocation.parse(itemId);
        return BuiltInRegistries.ITEM.getOptional(location);
    }

    private static String serializedName(final ClimateModificationType type)
    {
        return switch (type)
        {
            case HOT -> "temp_up";
            case COLD -> "temp_down";
            case HUMID -> "humid_up";
            case DRY -> "humid_down";
        };
    }
}
