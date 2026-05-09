package com.deathfrog.greenhousegardener.core.world.biomeservice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

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
    public static final int DEFAULT_VERTICAL_RANGE = 8;
    private static final int BIOME_LOOKUP_PADDING = 4;
    private static final ResourceLocation BIOME_COLD_DRY = ResourceLocation.withDefaultNamespace("snowy_slopes");
    private static final ResourceLocation BIOME_COLD_NORMAL = ResourceLocation.withDefaultNamespace("snowy_plains");
    private static final ResourceLocation BIOME_COLD_HUMID = ResourceLocation.withDefaultNamespace("old_growth_pine_taiga");
    private static final ResourceLocation BIOME_TEMPERATE_DRY = ResourceLocation.withDefaultNamespace("savanna");
    private static final ResourceLocation BIOME_TEMPERATE_NORMAL = ResourceLocation.withDefaultNamespace("plains");
    private static final ResourceLocation BIOME_TEMPERATE_HUMID = ResourceLocation.withDefaultNamespace("swamp");
    private static final ResourceLocation BIOME_HOT_DRY = ResourceLocation.withDefaultNamespace("desert");
    private static final ResourceLocation BIOME_HOT_NORMAL = ResourceLocation.withDefaultNamespace("sparse_jungle");
    private static final ResourceLocation BIOME_HOT_HUMID = ResourceLocation.withDefaultNamespace("jungle");

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
    public static OverlayResult applyOverlay(
        final ServerLevel level,
        final BlockPos center,
        final int horizontalRange,
        final int verticalRange,
        final GreenhouseClimate climate,
        final Map<BlockPos, ResourceLocation> naturalBiomes,
        final Map<BlockPos, ResourceLocation> appliedBiomes)
    {
        if (center == null)
        {
            return OverlayResult.EMPTY;
        }

        return applyOverlay(level, FieldBiomeFootprint.centered(center, horizontalRange, verticalRange), climate, naturalBiomes, appliedBiomes);
    }

    /**
     * Apply a climate overlay across a field footprint expanded to cover vanilla's smoothed biome lookup.
     *
     * @param level the server level containing the target chunks
     * @param footprint the exact field footprint before biome lookup padding
     * @param climate the desired greenhouse climate
     * @param naturalBiomes persisted biome cells captured before this service first changed them
     * @param appliedBiomes persisted biome cells last written by this service
     * @return a summary of the overlay work performed
     */
    @SuppressWarnings("null")
    public static OverlayResult applyOverlay(
        final ServerLevel level,
        final FieldBiomeFootprint footprint,
        final GreenhouseClimate climate,
        final Map<BlockPos, ResourceLocation> naturalBiomes,
        final Map<BlockPos, ResourceLocation> appliedBiomes)
    {
        if (level == null || footprint == null || climate == null || naturalBiomes == null || appliedBiomes == null)
        {
            return OverlayResult.EMPTY;
        }

        final ResourceLocation targetBiomeId = biomeFor(climate);
        if (targetBiomeId == null)
        {
            return OverlayResult.EMPTY;
        }

        final Holder.Reference<Biome> targetBiome = biomeHolder(level, targetBiomeId);
        final BoundingBox targetRegion = footprint.paddedBiomeRegion();
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
        if (center == null)
        {
            return OverlayResult.EMPTY;
        }

        return restoreOverlay(level, FieldBiomeFootprint.centered(center, horizontalRange, verticalRange), naturalBiomes, appliedBiomes);
    }

    /**
     * Restore natural biomes across a field footprint expanded to cover vanilla's smoothed biome lookup.
     *
     * @param level the server level containing the target chunks
     * @param footprint the exact field footprint before biome lookup padding
     * @param naturalBiomes persisted biome cells captured before this service first changed them
     * @param appliedBiomes persisted biome cells last written by this service
     * @return a summary of the restoration work performed
     */
    public static OverlayResult restoreOverlay(
        final ServerLevel level,
        final FieldBiomeFootprint footprint,
        final Map<BlockPos, ResourceLocation> naturalBiomes,
        final Map<BlockPos, ResourceLocation> appliedBiomes)
    {
        if (level == null || footprint == null || naturalBiomes == null || appliedBiomes == null)
        {
            return OverlayResult.EMPTY;
        }

        return restoreOverlay(level, footprint.paddedBiomeRegion(), naturalBiomes, appliedBiomes);
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
    public static ResourceLocation biomeFor(final GreenhouseClimate climate)
    {
        return switch (climate.temperature())
        {
            case COLD -> switch (climate.humidity())
            {
                case DRY -> BIOME_COLD_DRY;
                case NORMAL -> BIOME_COLD_NORMAL;
                case HUMID -> BIOME_COLD_HUMID;
            };
            case TEMPERATE -> switch (climate.humidity())
            {
                case DRY -> BIOME_TEMPERATE_DRY;
                case NORMAL -> BIOME_TEMPERATE_NORMAL;
                case HUMID -> BIOME_TEMPERATE_HUMID;
            };
            case HOT -> switch (climate.humidity())
            {
                case DRY -> BIOME_HOT_DRY;
                case NORMAL -> BIOME_HOT_NORMAL;
                case HUMID -> BIOME_HOT_HUMID;
            };
        };
    }

    /**
     * Resolve a reference biome back to its exact greenhouse climate.
     *
     * @param biomeId biome id to classify
     * @return the greenhouse climate, or empty when the biome is not a reference biome
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
        if (center == null)
        {
            return false;
        }

        return needsOverlay(level, FieldBiomeFootprint.centered(center, horizontalRange, verticalRange), climate);
    }

    /**
     * Check whether a field footprint already has the requested greenhouse biome.
     *
     * @param level the server level containing the field
     * @param footprint the exact field footprint before biome lookup padding
     * @param climate the desired greenhouse climate
     * @return true when at least one loaded biome cell in the padded footprint differs from the target biome
     */
    public static boolean needsOverlay(
        final ServerLevel level,
        final FieldBiomeFootprint footprint,
        final GreenhouseClimate climate)
    {
        return checkOverlay(level, footprint, climate).needsOverlay();
    }

    /**
     * Check whether a field footprint already has the requested greenhouse biome, including chunk availability.
     *
     * @param level the server level containing the field
     * @param footprint the exact field footprint before biome lookup padding
     * @param climate the desired greenhouse climate
     * @return overlay inspection result
     */
    public static OverlayCheckResult checkOverlay(
        final ServerLevel level,
        final FieldBiomeFootprint footprint,
        final GreenhouseClimate climate)
    {
        if (level == null || footprint == null || climate == null)
        {
            return OverlayCheckResult.COMPLETE;
        }

        final ResourceLocation targetBiomeId = biomeFor(climate);
        if (targetBiomeId == null)
        {
            return OverlayCheckResult.COMPLETE;
        }

        final BoundingBox targetRegion = footprint.paddedBiomeRegion();
        final ChunkSelection chunkSelection = loadedChunks(level, targetRegion);
        if (chunkSelection.chunks().isEmpty())
        {
            return new OverlayCheckResult(false, chunkSelection.hadUnloadedChunks());
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
                            return new OverlayCheckResult(true, chunkSelection.hadUnloadedChunks());
                        }
                    }
                }
            }
        }

        return new OverlayCheckResult(false, chunkSelection.hadUnloadedChunks());
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

    private static BlockPos quantizedBlockPos(final BlockPos pos)
    {
        return new BlockPos(quantize(pos.getX()), quantize(pos.getY()), quantize(pos.getZ()));
    }

    /**
     * Quantize an exact field footprint into the biome-cell region that should be modified.
     *
     * @param exactRegion exact field blocks, without biome lookup padding
     * @return padded and quantized biome-cell region
     */
    @SuppressWarnings("null")
    public static BoundingBox paddedBiomeRegion(final BoundingBox exactRegion)
    {
        if (exactRegion == null)
        {
            return null;
        }

        return BoundingBox.fromCorners(
            quantizedBlockPos(new BlockPos(
                exactRegion.minX() - BIOME_LOOKUP_PADDING,
                exactRegion.minY(),
                exactRegion.minZ() - BIOME_LOOKUP_PADDING)),
            quantizedBlockPos(new BlockPos(
                exactRegion.maxX() + BIOME_LOOKUP_PADDING,
                exactRegion.maxY(),
                exactRegion.maxZ() + BIOME_LOOKUP_PADDING)));
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

    private record ChunkSelection(List<ChunkAccess> chunks, boolean hadUnloadedChunks)
    {
    }

}
