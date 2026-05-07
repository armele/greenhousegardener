package com.deathfrog.greenhousegardener.core.blocks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.greenhousegardener.core.colony.buildings.modules.GreenhouseBiomeModule;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class BlockClimateControlHub extends Block implements SimpleWaterloggedBlock
{
    public static final String BLOCK_NAME = "climatecontrolhub";
    @SuppressWarnings("null")
    public static final @Nonnull BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    @SuppressWarnings("null")
    public static final @Nonnull EnumProperty<VisualClimate> CLIMATE = EnumProperty.create("climate", VisualClimate.class);

    @SuppressWarnings("null")
    public BlockClimateControlHub()
    {
        super(BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).lightLevel(state -> 6));
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(WATERLOGGED, Boolean.FALSE)
            .setValue(CLIMATE, VisualClimate.INACTIVE));
    }

    @SuppressWarnings("null")
    @Nullable
    @Override
    public BlockState getStateForPlacement(final @Nonnull BlockPlaceContext context)
    {
        final FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        return super.getStateForPlacement(context)
            .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER)
            .setValue(CLIMATE, VisualClimate.INACTIVE);
    }

    @Override
    protected BlockState updateShape(
        final @Nonnull BlockState state,
        final @Nonnull Direction facing,
        final @Nonnull BlockState facingState,
        final @Nonnull LevelAccessor level,
        final @Nonnull BlockPos currentPos,
        final @Nonnull BlockPos facingPos)
    {
        if (state.getValue(WATERLOGGED))
        {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    protected FluidState getFluidState(final @Nonnull BlockState state)
    {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected void createBlockStateDefinition(final @Nonnull StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(WATERLOGGED, CLIMATE);
    }

    public enum VisualClimate implements StringRepresentable
    {
        INACTIVE("inactive"),
        COLD_DRY("cold_dry"),
        COLD_NORMAL("cold_normal"),
        COLD_HUMID("cold_humid"),
        TEMPERATE_DRY("temperate_dry"),
        TEMPERATE_NORMAL("temperate_normal"),
        TEMPERATE_HUMID("temperate_humid"),
        HOT_DRY("hot_dry"),
        HOT_NORMAL("hot_normal"),
        HOT_HUMID("hot_humid");

        private final String serializedName;

        VisualClimate(final String serializedName)
        {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName()
        {
            return serializedName;
        }
    }

    /**
     * Invalidate greenhouse field ownership when this hub block is removed.
     *
     * @param state previous block state
     * @param level world containing the removed hub
     * @param pos removed hub position
     * @param newState replacement block state
     * @param movedByPiston true when the block was moved by a piston
     */
    @Override
    protected void onRemove(final @Nonnull BlockState state, final @Nonnull Level level, final @Nonnull BlockPos pos, final @Nonnull BlockState newState, final boolean movedByPiston)
    {
        Block block = newState.getBlock();

        if (block == null) return;

        super.onRemove(state, level, pos, newState, movedByPiston);
        if (level == null || level.isClientSide() || state.is(block))
        {
            return;
        }

        cleanupGreenhouseOwnership(level, pos.above());
    }

    /**
     * Ask greenhouse modules in the colony to release fields that no longer have a hub.
     *
     * @param level world containing the affected field
     * @param fieldPosition field anchor position above the removed hub
     */
    private static void cleanupGreenhouseOwnership(final Level level, final BlockPos fieldPosition)
    {
        final IColony colony = IColonyManager.getInstance().getIColony(level, fieldPosition);
        if (colony == null)
        {
            return;
        }

        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            final GreenhouseBiomeModule module = building.getModule(GreenhouseBiomeModule.class, ignored -> true);
            if (module != null && module.isOwned(fieldPosition))
            {
                module.cleanupInvalidOwnedFields();
            }
        }
    }
}
