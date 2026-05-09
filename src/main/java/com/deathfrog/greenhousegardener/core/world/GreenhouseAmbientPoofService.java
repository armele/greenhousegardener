package com.deathfrog.greenhousegardener.core.world;

import javax.annotation.Nonnull;

import com.deathfrog.greenhousegardener.Config;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.FieldBiomeAssignment;
import com.deathfrog.greenhousegardener.core.world.biomeservice.GreenhouseClimate;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildingextensions.FarmField;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

public final class GreenhouseAmbientPoofService
{
    private static final int PARTICLE_POSITIONS_PER_FIELD = 4;
    private static final double NEARBY_PLAYER_RANGE = 48.0D;
    private static final double NEARBY_PLAYER_RANGE_SQUARED = NEARBY_PLAYER_RANGE * NEARBY_PLAYER_RANGE;

    private GreenhouseAmbientPoofService()
    {
    }

    /**
     * Emit light ambient poof particles over maintained greenhouse fields.
     *
     * @param level ticking level
     */
    public static void tick(final Level level)
    {
        if (!Config.ambientPoofsEnabled.get() || !(level instanceof ServerLevel serverLevel))
        {
            return;
        }

        final int interval = Math.max(1, Config.ambientPoofIntervalTicks.get());
        if (serverLevel.getGameTime() % interval != 0)
        {
            return;
        }

        for (final IColony colony : IColonyManager.getInstance().getColonies(serverLevel))
        {
            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
            {
                final GreenhouseBiomeModule module = building.getModule(GreenhouseBiomeModule.class, ignored -> true);
                if (module != null)
                {
                    emitForModule(serverLevel, module);
                }
            }
        }
    }

    /**
     * Emit ambient conditioning particles for every modified field managed by a greenhouse module.
     *
     * @param level server level containing the fields
     * @param module greenhouse biome module to inspect
     */
    private static void emitForModule(final ServerLevel level, final GreenhouseBiomeModule module)
    {
        for (final FarmField field : module.getManagedFields())
        {
            final BlockPos fieldPosition = field == null ? null : field.getPosition();
            if (fieldPosition == null || !module.isFieldModifiedFromNatural(level, fieldPosition) || !hasNearbyPlayer(level, fieldPosition))
            {
                continue;
            }

            emitForField(level, module, field);
        }
    }


    /**
     * Choose random positions inside a field and emit the particles matching its active climate changes.
     *
     * @param level server level containing the field
     * @param module greenhouse biome module storing the field assignment
     * @param field farm field receiving ambient particles
     */
    private static void emitForField(final ServerLevel level, final GreenhouseBiomeModule module, final FarmField field)
    {
        final BlockPos center = field.getPosition();
        final FieldBiomeAssignment assignment = module.getAssignment(center);
        final GreenhouseClimate naturalClimate = module.getNaturalClimate(level, center);
        final RandomSource random = level.getRandom();
        final int minX = center.getX() - field.getRadius(Direction.WEST);
        final int maxX = center.getX() + field.getRadius(Direction.EAST);
        final int minZ = center.getZ() - field.getRadius(Direction.NORTH);
        final int maxZ = center.getZ() + field.getRadius(Direction.SOUTH);

        for (int i = 0; i < PARTICLE_POSITIONS_PER_FIELD; i++)
        {
            final int x = randomBetween(random, minX, maxX);
            final int z = randomBetween(random, minZ, maxZ);
            if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)))
            {
                continue;
            }

            emitConditioningParticles(level, assignment, naturalClimate, x + 0.5D, center.getY() + 1.0D, z + 0.5D);
        }
    }

    /**
     * Emit one particle for each direction of conditioning represented by the requested climate.
     *
     * <p>Temperature increases use small flames, temperature decreases use snowflakes, humidity increases use poofs,
     * and humidity decreases use white ash. A field changed on both axes emits both matching particles.</p>
     *
     * @param level server level receiving particles
     * @param assignment requested field climate
     * @param naturalClimate field climate before greenhouse conditioning
     * @param x particle x coordinate
     * @param y particle y coordinate
     * @param z particle z coordinate
     */
    @SuppressWarnings("null")
    private static void emitConditioningParticles(
        final ServerLevel level,
        final FieldBiomeAssignment assignment,
        final GreenhouseClimate naturalClimate,
        final double x,
        final double y,
        final double z)
    {
        final int temperatureChange = assignment.temperature().ordinal() - naturalClimate.temperature().ordinal();
        final int humidityChange = assignment.humidity().ordinal() - naturalClimate.humidity().ordinal();

        if (temperatureChange > 0)
        {
            emitParticle(level, ParticleTypes.SMALL_FLAME, x, y, z);
        }
        else if (temperatureChange < 0)
        {
            emitParticle(level, ParticleTypes.SNOWFLAKE, x, y, z);
        }

        if (humidityChange > 0)
        {
            emitParticle(level, ParticleTypes.POOF, x, y, z);
        }
        else if (humidityChange < 0)
        {
            emitParticle(level, ParticleTypes.WHITE_ASH, x, y, z);
        }
    }

    /**
     * Send a single low-density ambient particle at the sampled field position.
     *
     * @param level server level receiving particles
     * @param particle particle type to emit
     * @param x particle x coordinate
     * @param y particle y coordinate
     * @param z particle z coordinate
     */
    private static void emitParticle(final ServerLevel level, final @Nonnull ParticleOptions particle, final double x, final double y, final double z)
    {
        level.sendParticles(
            particle,
            x,
            y,
            z,
            1,
            0.15D,
            0.25D,
            0.15D,
            0.01D);
    }

    /**
     * Check whether the field is close enough to a player for ambient particles to be worth sending.
     *
     * @param level server level containing players
     * @param pos field anchor position
     * @return true when at least one player is within the configured ambient particle range
     */
    private static boolean hasNearbyPlayer(final ServerLevel level, final BlockPos pos)
    {
        return level.players().stream().anyMatch(player -> player.distanceToSqr(
            pos.getX() + 0.5D,
            pos.getY() + 0.5D,
            pos.getZ() + 0.5D) <= NEARBY_PLAYER_RANGE_SQUARED);
    }

    /**
     * Pick an inclusive random integer inside a field axis range.
     *
     * @param random random source from the server level
     * @param min inclusive lower bound
     * @param max inclusive upper bound
     * @return selected value between min and max
     */
    private static int randomBetween(final RandomSource random, final int min, final int max)
    {
        return min + random.nextInt(max - min + 1);
    }
}
