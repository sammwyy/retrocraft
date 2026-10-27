package com.sammwy.retrocraft.gametest.forge;

import com.sammwy.retrocraft.gametest.generated.*;

import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("retrocraft_test")
public final class ForgeGameTests {
    public ForgeGameTests(FMLJavaModLoadingContext context) {
        com.sammwy.retrocraft.compat.ForgeEvents.mod(
                context,
                RegisterGameTestsEvent.class,
                (RegisterGameTestsEvent event) -> {
                    event.register(ConsoleInventoryTest.class);
                    event.register(ArcadeVariantsTest.class);
                    event.register(ControllerPacketsTest.class);
                    event.register(FoldableConsolesTest.class);
                    event.register(PspConsoleTest.class);
                    event.register(ConsoleIdsTest.class);
                    event.register(ConsoleComponentsTest.class);
                });
    }
}
