package com.deathfrog.greenhousegardener.core.advancements;

import java.util.Optional;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

public class FieldBiomeModifiedTrigger extends SimpleCriterionTrigger<FieldBiomeModifiedTrigger.TriggerInstance>
{
    public void trigger(final @Nonnull ServerPlayer player)
    {
        trigger(player, trigger -> true);
    }

    @Override
    public Codec<TriggerInstance> codec()
    {
        return TriggerInstance.CODEC;
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleCriterionTrigger.SimpleInstance
    {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(builder -> builder
            .group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player))
            .apply(builder, TriggerInstance::new));

        public static Criterion<TriggerInstance> fieldBiomeModified()
        {
            return AdvancementTriggers.FIELD_BIOME_MODIFIED.get().createCriterion(new TriggerInstance(Optional.empty()));
        }
    }
}
