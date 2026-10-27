package com.sammwy.retrocraft.gametest;

import java.util.UUID;

import com.sammwy.retrocraft.cores.worker.PadInput;
import com.sammwy.retrocraft.server.net.Packets;
import com.sammwy.retrocraft.server.screen.*;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import io.netty.buffer.Unpooled;

public class ControllerPacketsTest implements GameTestPlatform {
    @Scenario
    public void virtualInputsRoundTripWithoutKeyboardAssignments(ScenarioContext context) {
        var input =
                new PadInput(0xf001, (short) -32767, (short) 1234, (short) 32767, (short) -4321)
                        .withTouch((short) -12345, (short) 23456, true);
        var request = new Packets.Request(Packets.INPUT, UUID.randomUUID(), input, 42);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            Packets.Request.CODEC.encode(buffer, request);
            context.assertTrue(
                    buffer.readableBytes() == 33,
                    "Input sends only action, instance, virtual buttons/axes/stylus and video acknowledgement");
            context.assertTrue(
                    request.equals(Packets.Request.CODEC.decode(buffer)),
                    "All buttons and both sticks must survive the client/server codec");
            context.assertTrue(buffer.readableBytes() == 0, "No extra input data remains");
            for (int slot = 0; slot < 4; slot++) {
                var broadcast = new Packets.Pad(request.instance(), slot, input);
                Packets.Pad.CODEC.encode(buffer, broadcast);
                context.assertTrue(
                        broadcast.equals(Packets.Pad.CODEC.decode(buffer)),
                        "Remote players and hybrid emulation receive the same virtual input for port "
                                + slot);
            }
        } finally {
            buffer.release();
        }
        context.succeed();
    }
}
