package com.deathfrog.greenhousegardener.core.advancements;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AdvancementTriggers
{
    @SuppressWarnings("null")
    public static final DeferredRegister<CriterionTrigger<?>> DEFERRED_REGISTER =
        DeferredRegister.create(Registries.TRIGGER_TYPE, GreenhouseGardenerMod.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, FieldBiomeModifiedTrigger> FIELD_BIOME_MODIFIED =
        DEFERRED_REGISTER.register("field_biome_modified", FieldBiomeModifiedTrigger::new);

    private AdvancementTriggers()
    {
        throw new IllegalStateException("Tried to initialize: AdvancementTriggers but this is a Utility class.");
    }
}
