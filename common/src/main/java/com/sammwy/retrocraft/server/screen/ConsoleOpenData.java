package com.sammwy.retrocraft.server.screen;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record ConsoleOpenData(ResourceLocation console, int lockedSlot) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ConsoleOpenData> CODEC =
            new StreamCodec<>() {
                public ConsoleOpenData decode(RegistryFriendlyByteBuf b) {
                    return new ConsoleOpenData(
                            ResourceLocation.STREAM_CODEC.decode(b), b.readInt());
                }

                public void encode(RegistryFriendlyByteBuf b, ConsoleOpenData data) {
                    ResourceLocation.STREAM_CODEC.encode(b, data.console);
                    b.writeInt(data.lockedSlot);
                }
            };
}
