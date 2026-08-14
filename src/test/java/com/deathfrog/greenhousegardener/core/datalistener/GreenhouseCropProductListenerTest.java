package com.deathfrog.greenhousegardener.core.datalistener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

class GreenhouseCropProductListenerTest
{
    @Test
    void parsesExplicitSeedProductMapping()
    {
        final Map<ResourceLocation, ResourceLocation> mappings = GreenhouseCropProductListener.parseDefinitions(Map.of(
            ResourceLocation.fromNamespaceAndPath("test", "wheat"),
            JsonParser.parseString("{\"seed\":\"minecraft:wheat_seeds\",\"product\":\"minecraft:wheat\"}")));

        assertEquals(ResourceLocation.parse("minecraft:wheat"), GreenhouseCropProductListener.productIdFor(
            ResourceLocation.parse("minecraft:wheat_seeds"), mappings));
    }

    @Test
    void fallsBackToPlantingItemWithoutMapping()
    {
        final ResourceLocation carrot = ResourceLocation.parse("minecraft:carrot");
        assertEquals(carrot, GreenhouseCropProductListener.productIdFor(carrot, Map.of()));
    }

    @Test
    void laterResourceIdDeterministicallyReplacesDuplicateSeed()
    {
        final Map<ResourceLocation, ResourceLocation> mappings = GreenhouseCropProductListener.parseDefinitions(Map.of(
            ResourceLocation.fromNamespaceAndPath("test", "a_first"),
            JsonParser.parseString("{\"seed\":\"minecraft:wheat_seeds\",\"product\":\"minecraft:wheat\"}"),
            ResourceLocation.fromNamespaceAndPath("test", "z_last"),
            JsonParser.parseString("{\"seed\":\"minecraft:wheat_seeds\",\"product\":\"minecraft:bread\"}")));

        assertEquals(ResourceLocation.parse("minecraft:bread"), GreenhouseCropProductListener.productIdFor(
            ResourceLocation.parse("minecraft:wheat_seeds"), mappings));
    }
}
