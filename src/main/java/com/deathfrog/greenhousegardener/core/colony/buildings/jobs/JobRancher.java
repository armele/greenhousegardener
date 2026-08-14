package com.deathfrog.greenhousegardener.core.colony.buildings.jobs;

import com.deathfrog.greenhousegardener.core.entity.EntityAIWorkRancher;
import com.minecolonies.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.colony.jobs.AbstractJob;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.api.util.constant.StatisticsConstants.ITEM_OBTAINED;

public class JobRancher extends AbstractJob<EntityAIWorkRancher, JobRancher>
{
    private boolean herdTypeOverload;
    public JobRancher(final ICitizenData entity)
    {
        super(entity);
    }

    @Override
    public EntityAIWorkRancher generateAI()
    {
        return new EntityAIWorkRancher(this);
    }

    public boolean hasHerdTypeOverload()
    {
        return herdTypeOverload;
    }

    public void setHerdTypeOverload(final boolean herdTypeOverload)
    {
        this.herdTypeOverload = herdTypeOverload;
    }

    /**
     * Reuses MineColonies' Cowboy citizen model and skin variants until the
     * Rancher receives dedicated artwork.
     *
     * @return Cowboy model-type identifier
     */
    @NotNull
    @Override
    public ResourceLocation getModel()
    {
        return ModModelTypes.COW_FARMER_ID;
    }

    /**
     * Account for products picked up inside the Ranch, including butcher drops
     * and passive animal products. The base herder walks to these items but does
     * not record their acquisition.
     */
    @Override
    public boolean onStackPickUp(@NotNull final ItemStack pickedUpStack)
    {
        if (getCitizen().getWorkBuilding() != null
            && getCitizen().getEntity().isPresent()
            && getCitizen().getWorkBuilding().isInBuilding(getCitizen().getEntity().get().blockPosition()))
        {
            StatsUtil.trackStatByName(
                getCitizen().getWorkBuilding(),
                ITEM_OBTAINED,
                pickedUpStack.getHoverName(),
                pickedUpStack.getCount());
        }
        return super.onStackPickUp(pickedUpStack);
    }
}
