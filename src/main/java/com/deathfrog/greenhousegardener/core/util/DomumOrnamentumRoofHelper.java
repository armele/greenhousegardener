package com.deathfrog.greenhousegardener.core.util;

import java.util.Map;

import javax.annotation.Nonnull;

import com.deathfrog.greenhousegardener.core.ModTags;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.entity.block.IMateriallyTexturedBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class DomumOrnamentumRoofHelper
{
    private DomumOrnamentumRoofHelper()
    {
    }

    /**
     * Check whether a placed Domum Ornamentum materially textured block uses any greenhouse roof material.
     *
     * @param level server level containing the candidate block
     * @param roofPos block position to inspect
     * @param roofState block state to inspect
     * @return true when any Domum texture component material is tagged as greenhouse roof material
     */
    public static boolean hasTaggedDomumMaterial(
        final @Nonnull ServerLevel level,
        final @Nonnull BlockPos roofPos,
        final @Nonnull BlockState roofState)
    {
        if (!(roofState.getBlock() instanceof IMateriallyTexturedBlock texturedBlock))
        {
            return false;
        }

        final BlockEntity blockEntity = level.getBlockEntity(roofPos);
        if (!(blockEntity instanceof IMateriallyTexturedBlockEntity texturedBlockEntity))
        {
            return false;
        }

        final MaterialTextureData textureData = texturedBlockEntity.getTextureData();
        if (textureData == null || textureData.isEmpty())
        {
            return false;
        }

        final Map<ResourceLocation, Block> componentMaterials = textureData
            .retainComponentsFromBlock(texturedBlock)
            .getTexturedComponents();

        for (final Block material : componentMaterials.values())
        {
            if (material != null && material.defaultBlockState().is(ModTags.BLOCKS.GREENHOUSE_ROOF))
            {
                return true;
            }
        }

        return false;
    }
}
