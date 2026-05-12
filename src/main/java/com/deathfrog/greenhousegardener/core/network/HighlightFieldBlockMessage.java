package com.deathfrog.greenhousegardener.core.network;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;
import com.deathfrog.greenhousegardener.core.client.GreenhouseFieldHighlighter;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Tells the client to highlight a greenhouse field block.
 */
public record HighlightFieldBlockMessage(BlockPos fieldPos) implements IClientboundPayload
{
    @SuppressWarnings("null")
    public static final Type<HighlightFieldBlockMessage> ID =
        new Type<>(ResourceLocation.fromNamespaceAndPath(GreenhouseGardenerMod.MODID, "highlight_field_block"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, HighlightFieldBlockMessage> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        HighlightFieldBlockMessage::fieldPos,
        HighlightFieldBlockMessage::new);

    @Override
    public Type<HighlightFieldBlockMessage> type()
    {
        return ID;
    }

    public void onExecute(@NotNull final IPayloadContext context)
    {
        context.enqueueWork(() -> GreenhouseFieldHighlighter.highlight(fieldPos));
    }
}
