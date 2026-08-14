package com.deathfrog.greenhousegardener.api.colony.buildings.moduleviews;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.greenhousegardener.core.client.gui.modules.WindowColonyCropsModule;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Client-side data for the colony-wide crop-field overview. */
public class ColonyCropsModuleView extends AbstractBuildingModuleView
{
    private final List<CropFieldView> fields = new ArrayList<>();

    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf)
    {
        fields.clear();
        fields.addAll(readFields(buf));
    }

    /**
     * Decode a crop-field snapshot shared by building views and portable journals.
     *
     * @param buf network buffer containing the snapshot
     * @return immutable decoded field list
     */
    public static List<CropFieldView> readFields(final RegistryFriendlyByteBuf buf)
    {
        final List<CropFieldView> decoded = new ArrayList<>();
        final int count = buf.readInt();
        for (int i = 0; i < count; i++)
        {
            final BlockPos position = buf.readBlockPos();
            final ItemStack seed = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            final ItemStack product = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            final int productCount = buf.readInt();
            final boolean assigned = buf.readBoolean();
            final BlockPos farmPosition = buf.readBoolean() ? buf.readBlockPos() : null;
            final int workerCount = buf.readInt();
            final List<String> workers = new ArrayList<>();
            for (int worker = 0; worker < workerCount; worker++)
            {
                workers.add(buf.readUtf());
            }
            decoded.add(new CropFieldView(position, seed, product, productCount, assigned, farmPosition, List.copyOf(workers),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readResourceLocation(), buf.readResourceLocation()));
        }
        return List.copyOf(decoded);
    }

    /**
     * Encode a crop-field snapshot shared by building views and portable journals.
     *
     * @param buf destination network buffer
     * @param fields fields to encode
     */
    @SuppressWarnings("null")
    public static void writeFields(final RegistryFriendlyByteBuf buf, final List<CropFieldView> fields)
    {
        buf.writeInt(fields.size());
        for (final CropFieldView field : fields)
        {
            buf.writeBlockPos(field.position());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, field.seed());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, field.product());
            buf.writeInt(field.productCount());
            buf.writeBoolean(field.assigned());
            buf.writeBoolean(field.farmPosition() != null);
            if (field.farmPosition() != null)
            {
                buf.writeBlockPos(field.farmPosition());
            }
            buf.writeInt(field.workers().size());
            field.workers().forEach(buf::writeUtf);
            buf.writeBoolean(field.hasClimateControlHub());
            buf.writeBoolean(field.ownedByThisGreenhouse());
            buf.writeBoolean(field.ownedByAnotherGreenhouse());
            buf.writeBoolean(field.thisGreenhouseHasCapacity());
            buf.writeResourceLocation(field.effectiveBiome());
            buf.writeResourceLocation(field.naturalBiome());
        }
    }

    @Override
    public @Nullable Component getDesc()
    {
        return Component.translatable("com.greenhousegardener.core.gui.modules.colony_crops");
    }

    @Override
    public BOWindow getWindow()
    {
        return new WindowColonyCropsModule(this);
    }

    @Override
    public String getIcon()
    {
        return "field";
    }

    public List<CropFieldView> getFields()
    {
        return List.copyOf(fields);
    }

    public record CropFieldView(
        BlockPos position,
        ItemStack seed,
        ItemStack product,
        int productCount,
        boolean assigned,
        @Nullable BlockPos farmPosition,
        List<String> workers,
        boolean hasClimateControlHub,
        boolean ownedByThisGreenhouse,
        boolean ownedByAnotherGreenhouse,
        boolean thisGreenhouseHasCapacity,
        ResourceLocation effectiveBiome,
        ResourceLocation naturalBiome)
    {
    }
}
