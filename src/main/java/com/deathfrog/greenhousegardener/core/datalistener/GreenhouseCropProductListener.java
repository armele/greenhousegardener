package com.deathfrog.greenhousegardener.core.datalistener;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

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

/** Loads datapack mappings from field planting items to their normal harvested products. */
public class GreenhouseCropProductListener extends SimpleJsonResourceReloadListener
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String DIRECTORY = "greenhouse_crop_products";
    private static final String TAG_SEED = "seed";
    private static final String TAG_PRODUCT = "product";

    public static final GreenhouseCropProductListener INSTANCE = new GreenhouseCropProductListener();

    private Map<Item, Item> products = Map.of();

    private GreenhouseCropProductListener()
    {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(
        @NotNull final @Nonnull Map<ResourceLocation, JsonElement> objects,
        @NotNull final @Nonnull ResourceManager resourceManager,
        @NotNull final @Nonnull ProfilerFiller profiler)
    {
        final Map<Item, Item> resolvedProducts = new HashMap<>();
        parseDefinitions(objects).forEach((seedId, productId) ->
        {
            final Item seed = BuiltInRegistries.ITEM.getOptional(seedId).orElse(null);
            final Item product = BuiltInRegistries.ITEM.getOptional(productId).orElse(null);
            if (seed == null || product == null)
            {
                GreenhouseGardenerMod.LOGGER.error(
                    "Unknown item in greenhouse crop product mapping: seed {}, product {}",
                    seedId,
                    productId);
            }
            else
            {
                resolvedProducts.put(seed, product);
            }
        });
        products = Map.copyOf(resolvedProducts);
        GreenhouseGardenerMod.LOGGER.info("Loaded {} greenhouse crop product mappings", products.size());
    }

    /**
     * Resolve the warehouse item representing a field's selected planting item.
     *
     * @param seed selected field planting item
     * @return mapped harvested product, or the planting item itself when no mapping exists
     */
    public Item productFor(final ItemStack seed)
    {
        return products.getOrDefault(seed.getItem(), seed.getItem());
    }

    /**
     * Parse a complete reload snapshot in deterministic resource order.
     *
     * @param objects crop-product JSON resources
     * @return immutable planting-item to product mappings
     */
    @SuppressWarnings("null")
    static Map<ResourceLocation, ResourceLocation> parseDefinitions(final Map<ResourceLocation, JsonElement> objects)
    {
        final Map<ResourceLocation, ResourceLocation> loadedProducts = new HashMap<>();
        objects.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
            .forEach(entry -> loadMapping(entry.getKey(), entry.getValue(), loadedProducts));
        return Map.copyOf(loadedProducts);
    }

    /**
     * Resolve a product against an explicit mapping snapshot.
     *
     * @param seed selected planting item
     * @param mappings planting-item to product mappings
     * @return mapped product or the planting item itself
     */
    static ResourceLocation productIdFor(
        final ResourceLocation seed,
        final Map<ResourceLocation, ResourceLocation> mappings)
    {
        return mappings.getOrDefault(seed, seed);
    }

    /**
     * Parse and add one crop-product resource, logging invalid or conflicting definitions.
     *
     * @param resource resource identifier used for diagnostics
     * @param element JSON mapping definition
     * @param target mappings accumulated during this reload
     */
    private static void loadMapping(
        final ResourceLocation resource,
        final @Nonnull JsonElement element,
        final Map<ResourceLocation, ResourceLocation> target)
    {
        try
        {
            final JsonObject json = GsonHelper.convertToJsonObject(element, DIRECTORY + "/" + resource);

            if (json == null) 
            {
                GreenhouseGardenerMod.LOGGER.error("No JSON element found while parsing greenhouse crop product mapping {}", resource);
                return;
            }

            final ResourceLocation seed = parseItemId(json, TAG_SEED);
            final ResourceLocation product = parseItemId(json, TAG_PRODUCT);
            final ResourceLocation previous = target.put(seed, product);
            if (previous != null && !previous.equals(product))
            {
                GreenhouseGardenerMod.LOGGER.warn(
                    "Crop product mapping {} replaces {} with {} for seed {}",
                    resource,
                    previous,
                    product,
                    seed);
            }
        }
        catch (final JsonParseException | IllegalArgumentException e)
        {
            GreenhouseGardenerMod.LOGGER.error("Error parsing greenhouse crop product mapping {}", resource, e);
        }
    }

    /**
     * Resolve a required item identifier from a mapping property.
     *
     * @param json mapping object
     * @param key property containing the item identifier
     * @return parsed item identifier
     * @throws JsonParseException when the identifier is malformed
     */
    private static ResourceLocation parseItemId(final @Nonnull JsonObject json, final @Nonnull String key)
    {
        final String value = GsonHelper.getAsString(json, key);

        if (value == null)
        {
            throw new JsonParseException("Null value for '" + key + "'");  
        }

        final ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null)
        {
            throw new JsonParseException("Invalid item identifier for '" + key + "': " + value);
        }
        return id;
    }
}
