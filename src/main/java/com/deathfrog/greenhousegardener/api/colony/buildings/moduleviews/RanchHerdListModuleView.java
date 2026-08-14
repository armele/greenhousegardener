package com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews;

import com.deathfrog.greenhousegardener.core.client.gui.modules.WindowRanchHerdListModule;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.deathfrog.greenhousegardener.core.colony.buildings.modules.RanchHerdListModule.BOWL_MILKABLE;
import static com.deathfrog.greenhousegardener.core.colony.buildings.modules.RanchHerdListModule.BUCKET_MILKABLE;
import static com.deathfrog.greenhousegardener.core.colony.buildings.modules.RanchHerdListModule.SHEARABLE;

/**
 * Client-side view of the Ranch herd snapshot.
 */
public class RanchHerdListModuleView extends AbstractBuildingModuleView
{
    private int herdCapacity;
    private int supportedTypeCapacity;
    private final List<HerdView> herds = new ArrayList<>();

    /**
     * Reads the Ranch capacity and managed-herd snapshot from the server.
     *
     * @param buf synchronized module-view data
     */
    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf)
    {
        herdCapacity = buf.readInt();
        supportedTypeCapacity = buf.readInt();
        herds.clear();
        final int size = buf.readInt();
        for (int i = 0; i < size; i++)
        {
            herds.add(new HerdView(
                buf.readResourceLocation(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));
        }
    }

    /**
     * Returns the translated label shown for the Ranch herd tab.
     *
     * @return herd-tab description
     */
    @Override
    public @Nullable Component getDesc()
    {
        return Component.translatable("com.greenhousegardener.core.gui.modules.ranch_herds");
    }

    /**
     * Creates the read-only herd-list window.
     *
     * @return Ranch herd-list window
     */
    @Override
    public BOWindow getWindow()
    {
        return new WindowRanchHerdListModule(this);
    }

    /**
     * Returns the MineColonies entity icon used by the herd tab.
     *
     * @return module-tab icon resource
     */
    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath("minecolonies", "textures/gui/modules/entity.png");
    }

    /**
     * Returns the uniform capacity applied to every exact Ranch species.
     *
     * @return maximum animals per species
     */
    public int getHerdCapacity()
    {
        return herdCapacity;
    }

    public int getSupportedTypeCapacity()
    {
        return supportedTypeCapacity;
    }

    /**
     * Returns the latest synchronized herd rows.
     *
     * @return immutable copy of the herd rows
     */
    public List<HerdView> getHerds()
    {
        return List.copyOf(herds);
    }

    /**
     * One exact entity type and its current count inside the Ranch boundaries.
     *
     * @param entityType registered entity-type identifier
     * @param count number of managed animals currently present
     * @param capabilities synchronized product-capability bitmask
     * @param supported whether this type occupies a supported first-seen slot
     * @param breedingFood preferred breeding food, or an empty stack
     */
    public record HerdView(ResourceLocation entityType, int count, int capabilities, boolean supported, ItemStack breedingFood)
    {
        /**
         * Tests whether the herd supports bowl-based milking.
         *
         * @return whether to display the bowl icon
         */
        public boolean isBowlMilkable()
        {
            return (capabilities & BOWL_MILKABLE) != 0;
        }

        /**
         * Tests whether the herd supports bucket-based milking.
         *
         * @return whether to display the bucket icon
         */
        public boolean isBucketMilkable()
        {
            return (capabilities & BUCKET_MILKABLE) != 0;
        }

        /**
         * Tests whether the herd supports shearing.
         *
         * @return whether to display the shears icon
         */
        public boolean isShearable()
        {
            return (capabilities & SHEARABLE) != 0;
        }
    }
}
