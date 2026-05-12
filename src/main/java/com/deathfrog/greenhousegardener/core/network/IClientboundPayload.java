package com.deathfrog.greenhousegardener.core.network;

import javax.annotation.Nonnull;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Small helper for server-to-client addon payloads.
 */
public interface IClientboundPayload extends CustomPacketPayload
{
    default void sendToPlayer(final @Nonnull ServerPlayer player)
    {
        PacketDistributor.sendToPlayer(player, this);
    }
}
