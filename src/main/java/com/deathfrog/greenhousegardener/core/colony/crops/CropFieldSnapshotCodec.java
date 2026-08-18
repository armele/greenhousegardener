package com.deathfrog.greenhousegardener.core.colony.crops;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/** Network codec for distribution-neutral crop-field snapshots. */
public final class CropFieldSnapshotCodec
{
    private CropFieldSnapshotCodec()
    {
    }

    public static List<CropFieldSnapshot> readList(final RegistryFriendlyByteBuf buf)
    {
        final List<CropFieldSnapshot> decoded = new ArrayList<>();
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
            decoded.add(new CropFieldSnapshot(position, seed, product, productCount, assigned, farmPosition,
                List.copyOf(workers), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readResourceLocation(), buf.readResourceLocation()));
        }
        return List.copyOf(decoded);
    }

    @SuppressWarnings("null")
    public static void writeList(final RegistryFriendlyByteBuf buf, final List<CropFieldSnapshot> fields)
    {
        buf.writeInt(fields.size());
        for (final CropFieldSnapshot field : fields)
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
}
