package com.deathfrog.greenhousegardener.core.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

import com.deathfrog.greenhousegardener.Config;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.HumiditySetting;
import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule.TemperatureSetting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Applies local biome overlays without modifying global biome definitions.
 */
public final class GreenhouseBiomeOverlayService
{
    private static final int DEFAULT_VERTICAL_RANGE = 8;

    private GreenhouseBiomeOverlayService()
    {
    }

    /**
     * Apply a climate overlay around a center point using the default vertical range.
     *
     * @param level the server level containing the target chunks
     * @param center the center of the target field area
     * @param horizontalRange the horizontal block radius to overlay
     * @param climate the desired greenhouse climate
     * @param naturalBiomes persisted biome cells captured before this service first changed them
     * @param appliedBiomes persisted biome cells last written by this service
     * @return a summary of the overlay work performed
     */
    public static OverlayResult applyOverlay(
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRange,
        final GreenhouseClimate climate,
        final Map<BlockPos, ResourceLocation> naturalBiomes,
        final Map<BlockPos, ResourceLocation> appliedBiomes)
    {
        return applyOverlay(level, center, horizontalRange, DEFAULT_VERTICAL_RANGE, climate, naturalBiomes, appliedBiomes);
    }

    /**
     * Apply a climate overlay around a center point.
     *
     * @param level the server level containing the target chunks
     * @param center the center of the target field area
     * @param horizontalRange the horizontal block radius to overlay
     * @param verticalRange the vertical block radius to overlay
     * @param climate the desired greenhouse climate
     * @param naturalBiomes persisted biome cells captured before this service first changed them
     * @param appliedBiomes persisted biome cells last written by this service
     * @return a summary of the overlay work performed
     */
    @SuppressWarnings("null")
    public static OverlayResult applyOverlay(
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRange,
        final int verticalRange,
        final GreenhouseClimate climate,
        final Map<BlockPos, ResourceLocation> naturalBiomes,
        final Map<BlockPos, ResourceLocation> appliedBiomes)
    {
        if (level == null || center == null || climate == null || naturalBiomes == null || appliedBiomes == null)
        {
            return OverlayResult.EMPTY;
        }

        final ResourceLocation targetBiomeId = biomeFor(climate);
        if (targetBiomeId == null)
        {
            return OverlayResult.EMPTY;
        }

        final Holder.Reference<Biome> targetBiome = biomeHolder(level, targetBiomeId);
        final BoundingBox targetRegion = quantizedBox(center, horizontalRange, verticalRange);
        final ChunkSelection chunkSelection = loadedChunks(level, targetRegion);
        if (chunkSelection.chunks().isEmpty())
        {
            return OverlayResult.EMPTY;
        }

        final Set<BlockPos> changedCells = new HashSet<>();
        for (final ChunkAccess chunk : chunkSelection.chunks())
        {
            chunk.fillBiomesFromNoise((quartX, quartY, quartZ, sampler) -> {
                final BlockPos cellPos = quantizedCell(quartX, quartY, quartZ);
                final Holder<Biome> currentBiome = chunk.getNoiseBiome(quartX, quartY, quartZ);
                
                if (cellPos == null) return currentBiome;

                if (!targetRegion.isInside(cellPos))
                {
                    return currentBiome;
                }

                holderId(currentBiome).ifPresent(id -> naturalBiomes.putIfAbsent(cellPos, id));
                appliedBiomes.put(cellPos, targetBiomeId);
                changedCells.add(cellPos);
                return targetBiome;
            }, level.getChunkSource().randomState().sampler());
            chunk.setUnsaved(true);
        }

        resendBiomes(level, chunkSelection.chunks());
        return new OverlayResult(changedCells.size(), chunkSelection.chunks().size(), chunkSelection.hadUnloadedChunks());
    }

    /**
     * Restore natural biomes around a center point using the default vertical range.
     *
     * @param level the server level containing the target chunks
     * @param center the center of the target field area
     * @param horizontalRange the horizontal block radius to restore
     * @param naturalBiomes persisted biome cells captured before this service first changed them
     * @param appliedBiomes persisted biome cells last written by this service
     * @return a summary of the restoration work performed
     */
    public static OverlayResult restoreOverlay(
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRange,
        final Map<BlockPos, ResourceLocation> naturalBiomes,
        final Map<BlockPos, ResourceLocation> appliedBiomes)
    {
        return restoreOverlay(level, center, horizontalRange, DEFAULT_VERTICAL_RANGE, naturalBiomes, appliedBiomes);
    }

    /**
     * Restore natural biomes around a center point.
     *
     * @param level the server level containing the target chunks
     * @param center the center of the target field area
     * @param horizontalRange the horizontal block radius to restore
     * @param verticalRange the vertical block radius to restore
     * @param naturalBiomes persisted biome cells captured before this service first changed them
     * @param appliedBiomes persisted biome cells last written by this service
     * @return a summary of the restoration work performed
     */
    public static OverlayResult restoreOverlay(
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRange,
        final int verticalRange,
        final Map<BlockPos, ResourceLocation> naturalBiomes,
        final Map<BlockPos, ResourceLocation> appliedBiomes)
    {
        if (level == null || center == null || naturalBiomes == null || appliedBiomes == null)
        {
            return OverlayResult.EMPTY;
        }

        final BoundingBox targetRegion = quantizedBox(center, horizontalRange, verticalRange);
        return restoreOverlay(level, targetRegion, naturalBiomes, appliedBiomes);
    }

    /**
     * Restore all biome cells currently tracked as applied by this service.
     *
     * @param level the server level containing the target chunks
     * @param naturalBiomes persisted biome cells captured before this service first changed them
     * @param appliedBiomes persisted biome cells last written by this service
     * @return a summary of the restoration work performed
     */
    public static OverlayResult restoreAllOverlays(
        final ServerLevel level,
        final Map<BlockPos, ResourceLocation> naturalBiomes,
        final Map<BlockPos, ResourceLocation> appliedBiomes)
    {
        if (level == null || naturalBiomes == null || appliedBiomes == null || appliedBiomes.isEmpty())
        {
            return OverlayResult.EMPTY;
        }

        final List<BlockPos> positions = appliedBiomes.keySet().stream().sorted(Comparator.comparing(BlockPos::asLong)).toList();
        final BlockPos first = positions.getFirst();
        int minX = first.getX();
        int minY = first.getY();
        int minZ = first.getZ();
        int maxX = first.getX();
        int maxY = first.getY();
        int maxZ = first.getZ();

        for (final BlockPos pos : positions)
        {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        return restoreOverlay(level, new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ), naturalBiomes, appliedBiomes);
    }

    /**
     * Resolve the vanilla biome used to represent a requested greenhouse climate.
     *
     * @param climate the desired greenhouse climate
     * @return the resource location of the overlay biome
     */
    @SuppressWarnings("null")
    public static ResourceLocation biomeFor(final GreenhouseClimate climate)
    {
        final String biomeId = switch (climate.temperature())
        {
            case COLD -> switch (climate.humidity())
            {
                case DRY -> Config.coldDry.get();
                case NORMAL -> Config.coldNormal.get();
                case HUMID -> Config.coldHumid.get();
            };
            case TEMPERATE -> switch (climate.humidity())
            {
                case DRY -> Config.temperateDry.get();
                case NORMAL -> Config.temperateNormal.get();
                case HUMID -> Config.temperateHumid.get();
            };
            case HOT -> switch (climate.humidity())
            {
                case DRY -> Config.hotDry.get();
                case NORMAL -> Config.hotNormal.get();
                case HUMID -> Config.hotHumid.get();
            };
        };
        return ResourceLocation.tryParse(biomeId);
    }

    /**
     * Resolve a configured reference biome back to its exact greenhouse climate.
     *
     * @param biomeId biome id to classify
     * @return the configured greenhouse climate, or empty when the biome is not a reference biome
     */
    public static Optional<GreenhouseClimate> climateFor(final ResourceLocation biomeId)
    {
        if (biomeId == null)
        {
            return Optional.empty();
        }

        for (final TemperatureSetting temperature : TemperatureSetting.values())
        {
            for (final HumiditySetting humidity : HumiditySetting.values())
            {
                final GreenhouseClimate climate = new GreenhouseClimate(temperature, humidity);
                if (biomeId.equals(biomeFor(climate)))
                {
                    return Optional.of(climate);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Check whether a field region already has the requested greenhouse biome.
     *
     * @param level the server level containing the field
     * @param center the center of the target field area
     * @param horizontalRange the horizontal block radius to inspect
     * @param climate the desired greenhouse climate
     * @return true when at least one loaded biome cell in the region differs from the target biome
     */
    public static boolean needsOverlay(
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRange,
        final GreenhouseClimate climate)
    {
        return needsOverlay(level, center, horizontalRange, DEFAULT_VERTICAL_RANGE, climate);
    }

    /**
     * Check whether a field region already has the requested greenhouse biome.
     *
     * @param level the server level containing the field
     * @param center the center of the target field area
     * @param horizontalRange the horizontal block radius to inspect
     * @param verticalRange the vertical block radius to inspect
     * @param climate the desired greenhouse climate
     * @return true when at least one loaded biome cell in the region differs from the target biome
     */
    public static boolean needsOverlay(
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRange,
        final int verticalRange,
        final GreenhouseClimate climate)
    {
        if (level == null || center == null || climate == null)
        {
            return false;
        }

        final ResourceLocation targetBiomeId = biomeFor(climate);
        if (targetBiomeId == null)
        {
            return false;
        }

        final BoundingBox targetRegion = quantizedBox(center, horizontalRange, verticalRange);
        final ChunkSelection chunkSelection = loadedChunks(level, targetRegion);
        if (chunkSelection.chunks().isEmpty())
        {
            return false;
        }

        for (final ChunkAccess chunk : chunkSelection.chunks())
        {
            final int minQuartX = Math.max(QuartPos.fromBlock(targetRegion.minX()), QuartPos.fromBlock(chunk.getPos().getMinBlockX()));
            final int maxQuartX = Math.min(QuartPos.fromBlock(targetRegion.maxX()), QuartPos.fromBlock(chunk.getPos().getMaxBlockX()));
            final int minQuartZ = Math.max(QuartPos.fromBlock(targetRegion.minZ()), QuartPos.fromBlock(chunk.getPos().getMinBlockZ()));
            final int maxQuartZ = Math.min(QuartPos.fromBlock(targetRegion.maxZ()), QuartPos.fromBlock(chunk.getPos().getMaxBlockZ()));

            for (int quartX = minQuartX; quartX <= maxQuartX; quartX++)
            {
                for (int quartY = QuartPos.fromBlock(targetRegion.minY()); quartY <= QuartPos.fromBlock(targetRegion.maxY()); quartY++)
                {
                    for (int quartZ = minQuartZ; quartZ <= maxQuartZ; quartZ++)
                    {
                        final BlockPos cellPos = quantizedCell(quartX, quartY, quartZ);
                        if (cellPos == null || !targetRegion.isInside(cellPos))
                        {
                            continue;
                        }

                        final Optional<ResourceLocation> currentBiomeId = holderId(chunk.getNoiseBiome(quartX, quartY, quartZ));
                        if (currentBiomeId.isEmpty() || !targetBiomeId.equals(currentBiomeId.get()))
                        {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    @SuppressWarnings("null")
    private static OverlayResult restoreOverlay(
        final ServerLevel level,
        final BoundingBox targetRegion,
        final Map<BlockPos, ResourceLocation> naturalBiomes,
        final Map<BlockPos, ResourceLocation> appliedBiomes)
    {
        final ChunkSelection chunkSelection = loadedChunks(level, targetRegion);
        if (chunkSelection.chunks().isEmpty())
        {
            return OverlayResult.EMPTY;
        }

        final Set<BlockPos> restoredCells = new HashSet<>();
        for (final ChunkAccess chunk : chunkSelection.chunks())
        {
            chunk.fillBiomesFromNoise((quartX, quartY, quartZ, sampler) -> {
                final BlockPos cellPos = quantizedCell(quartX, quartY, quartZ);
                final Holder<Biome> currentBiome = chunk.getNoiseBiome(quartX, quartY, quartZ);

                if (cellPos == null) return currentBiome;

                if (!targetRegion.isInside(cellPos))
                {
                    return currentBiome;
                }

                final ResourceLocation expectedApplied = appliedBiomes.get(cellPos);
                final ResourceLocation naturalBiomeId = naturalBiomes.get(cellPos);
                if (expectedApplied == null || naturalBiomeId == null)
                {
                    return currentBiome;
                }

                final Optional<ResourceLocation> currentBiomeId = holderId(currentBiome);
                if (currentBiomeId.isEmpty() || !expectedApplied.equals(currentBiomeId.get()))
                {
                    return currentBiome;
                }

                restoredCells.add(cellPos);
                return biomeHolder(level, naturalBiomeId);
            }, level.getChunkSource().randomState().sampler());
            chunk.setUnsaved(true);
        }

        restoredCells.forEach(pos -> {
            naturalBiomes.remove(pos);
            appliedBiomes.remove(pos);
        });
        resendBiomes(level, chunkSelection.chunks());
        return new OverlayResult(restoredCells.size(), chunkSelection.chunks().size(), chunkSelection.hadUnloadedChunks());
    }

    private static ChunkSelection loadedChunks(final ServerLevel level, final BoundingBox targetRegion)
    {
        final List<ChunkAccess> chunks = new ArrayList<>();
        boolean hadUnloadedChunks = false;
        for (int chunkZ = SectionPos.blockToSectionCoord(targetRegion.minZ()); chunkZ <= SectionPos.blockToSectionCoord(targetRegion.maxZ()); chunkZ++)
        {
            for (int chunkX = SectionPos.blockToSectionCoord(targetRegion.minX()); chunkX <= SectionPos.blockToSectionCoord(targetRegion.maxX()); chunkX++)
            {
                @SuppressWarnings("null")
                final ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk != null)
                {
                    chunks.add(chunk);
                }
                else
                {
                    hadUnloadedChunks = true;
                }
            }
        }
        return new ChunkSelection(chunks, hadUnloadedChunks);
    }

    @SuppressWarnings("null")
    private static BoundingBox quantizedBox(final BlockPos center, final int horizontalRange, final int verticalRange)
    {
        final int xzRange = Math.max(0, horizontalRange);
        final int yRange = Math.max(0, verticalRange);
        return BoundingBox.fromCorners(
            quantizedBlockPos(center.offset(-xzRange, -yRange, -xzRange)),
            quantizedBlockPos(center.offset(xzRange, yRange, xzRange)));
    }

    private static BlockPos quantizedBlockPos(final BlockPos pos)
    {
        return new BlockPos(quantize(pos.getX()), quantize(pos.getY()), quantize(pos.getZ()));
    }

    private static BlockPos quantizedCell(final int quartX, final int quartY, final int quartZ)
    {
        return new BlockPos(QuartPos.toBlock(quartX), QuartPos.toBlock(quartY), QuartPos.toBlock(quartZ));
    }

    private static int quantize(final int value)
    {
        return QuartPos.toBlock(QuartPos.fromBlock(value));
    }

    @SuppressWarnings("null")
    private static Holder.Reference<Biome> biomeHolder(final ServerLevel level, final ResourceLocation biomeId)
    {
        return level.registryAccess()
            .registryOrThrow(Registries.BIOME)
            .getHolderOrThrow(ResourceKey.create(Registries.BIOME, biomeId));
    }

    private static Optional<ResourceLocation> holderId(final Holder<Biome> biome)
    {
        return biome.unwrapKey().map(ResourceKey::location);
    }

    private static void resendBiomes(final ServerLevel level, final @Nonnull List<ChunkAccess> chunks)
    {
        level.getChunkSource().chunkMap.resendBiomesForChunks(chunks);
    }

    /**
     * Shared greenhouse climate axes used for field assignment, overlay biomes, and cost calculation.
     *
     * @param temperature requested or inferred temperature axis
     * @param humidity requested or inferred humidity axis
     */
    public record GreenhouseClimate(TemperatureSetting temperature, HumiditySetting humidity)
    {
        /**
         * Create climate settings from serialized module names.
         *
         * @param temperature serialized temperature name
         * @param humidity serialized humidity name
         * @return parsed climate settings, defaulting invalid values to temperate/normal
         */
        public static GreenhouseClimate bySerializedNames(final String temperature, final String humidity)
        {
            return new GreenhouseClimate(TemperatureSetting.bySerializedName(temperature), HumiditySetting.bySerializedName(humidity));
        }
    }

    /**
     * Summary of biome overlay or restoration work.
     *
     * @param changedCells number of quart biome cells changed
     * @param touchedChunks number of loaded chunks touched
     * @param hadUnloadedChunks true when at least one chunk in the target range was skipped because it was not loaded
     */
    public record OverlayResult(int changedCells, int touchedChunks, boolean hadUnloadedChunks)
    {
        public static final OverlayResult EMPTY = new OverlayResult(0, 0, false);
    }

    private record ChunkSelection(List<ChunkAccess> chunks, boolean hadUnloadedChunks)
    {
    }

}
