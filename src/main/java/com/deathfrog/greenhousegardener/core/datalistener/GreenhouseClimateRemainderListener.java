package com.deathfrog.greenhousegardener.core.datalistener;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Loads item remainders for greenhouse climate materials whose item metadata does not expose one.
 */
public class GreenhouseClimateRemainderListener extends SimpleJsonResourceReloadListener
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String DIRECTORY = "greenhouse_climate_remainders";
    private static final String TAG_CONSUMED = "consumed";
    private static final String TAG_REMAINDER = "remainder";

    public static final GreenhouseClimateRemainderListener INSTANCE = new GreenhouseClimateRemainderListener();

    private final Map<Item, Item> remainders = new HashMap<>();

    private GreenhouseClimateRemainderListener()
    {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(
        @NotNull final @Nonnull Map<ResourceLocation, JsonElement> object,
        @NotNull final @Nonnull ResourceManager resourceManager,
        @NotNull final @Nonnull ProfilerFiller profiler)
    {
        remainders.clear();

        for (final Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet())
        {
            try
            {
                JsonElement elem = entry.getValue();

                if (elem == null) continue;

                final JsonObject json = GsonHelper.convertToJsonObject(elem, DIRECTORY + "/" + entry.getKey());

                if (json == null) continue;

                final Item consumed = parseItem(json, TAG_CONSUMED)
                    .orElseThrow(() -> new JsonParseException("Unknown consumed item: " + GsonHelper.getAsString(json, TAG_CONSUMED)));
                final Item remainder = parseItem(json, TAG_REMAINDER)
                    .orElseThrow(() -> new JsonParseException("Unknown remainder item: " + GsonHelper.getAsString(json, TAG_REMAINDER)));

                remainders.put(consumed, remainder);
            }
            catch (final JsonParseException | IllegalArgumentException e)
            {
                GreenhouseGardenerMod.LOGGER.error("Error parsing greenhouse climate remainder {}", entry.getKey(), e);
            }
        }

        GreenhouseGardenerMod.LOGGER.info("Loaded {} greenhouse climate remainder mappings", remainders.size());
    }

    /**
     * Resolve the empty container produced when a climate material stack is consumed.
     *
     * @param consumed stack consumed by the greenhouse climate ledger
     * @return remainder stack, or empty when no remainder is known
     */
    public ItemStack getRemainder(final ItemStack consumed)
    {
        if (consumed.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        if (consumed.hasCraftingRemainingItem())
        {
            return consumed.getCraftingRemainingItem();
        }

        final Item remainder = remainders.get(consumed.getItem());
        return remainder == null ? ItemStack.EMPTY : new ItemStack(remainder);
    }

    private static Optional<Item> parseItem(final @Nonnull JsonObject json, final @Nonnull String key)
    {
        String gString = GsonHelper.getAsString(json, key);

        if (gString == null) return Optional.of(Items.AIR);

        final ResourceLocation location = ResourceLocation.parse(gString);
        return BuiltInRegistries.ITEM.getOptional(location);
    }
}
