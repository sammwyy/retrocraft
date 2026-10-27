package com.sammwy.retrocraft.gametest.forge;

import com.sammwy.retrocraft.gametest.generated.Scenarios;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;

/** Register the shared scenarios in Minecraft's function registry. */
@Mod("retrocraft_test")
public final class ForgeGameTests {
    public ForgeGameTests(FMLJavaModLoadingContext context) {
        com.sammwy.retrocraft.compat.ForgeEvents.mod(
                context, RegisterEvent.class, (RegisterEvent event) -> Scenarios.register(event));
    }
}
