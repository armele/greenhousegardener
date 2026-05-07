package com.deathfrog.greenhousegardener.core.world;

import com.deathfrog.greenhousegardener.Config;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildingextensions.FarmField;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

public final class GreenhouseAmbientPoofService
{
    private static final int POOF_POSITIONS_PER_FIELD = 4;
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

    private static void emitForModule(final ServerLevel level, final GreenhouseBiomeModule module)
    {
        for (final FarmField field : module.getManagedFields())
        {
            final BlockPos fieldPosition = field == null ? null : field.getPosition();
            if (fieldPosition == null || !module.isFieldModifiedFromNatural(level, fieldPosition) || !hasNearbyPlayer(level, fieldPosition))
            {
                continue;
            }

            emitForField(level, field);
        }
    }

    @SuppressWarnings("null")
    private static void emitForField(final ServerLevel level, final FarmField field)
    {
        final BlockPos center = field.getPosition();
        final RandomSource random = level.getRandom();
        final int minX = center.getX() - field.getRadius(Direction.WEST);
        final int maxX = center.getX() + field.getRadius(Direction.EAST);
        final int minZ = center.getZ() - field.getRadius(Direction.NORTH);
        final int maxZ = center.getZ() + field.getRadius(Direction.SOUTH);

        for (int i = 0; i < POOF_POSITIONS_PER_FIELD; i++)
        {
            final int x = randomBetween(random, minX, maxX);
            final int z = randomBetween(random, minZ, maxZ);
            if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)))
            {
                continue;
            }

            level.sendParticles(
                ParticleTypes.POOF,
                x + 0.5D,
                center.getY() + 1.0D,
                z + 0.5D,
                1,
                0.15D,
                0.25D,
                0.15D,
                0.01D);
        }
    }

    private static boolean hasNearbyPlayer(final ServerLevel level, final BlockPos pos)
    {
        return level.players().stream().anyMatch(player -> player.distanceToSqr(
            pos.getX() + 0.5D,
            pos.getY() + 0.5D,
            pos.getZ() + 0.5D) <= NEARBY_PLAYER_RANGE_SQUARED);
    }

    private static int randomBetween(final RandomSource random, final int min, final int max)
    {
        return min + random.nextInt(max - min + 1);
    }
}
