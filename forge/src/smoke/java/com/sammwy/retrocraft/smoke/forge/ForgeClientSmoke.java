package com.sammwy.retrocraft.smoke.forge;

import com.sammwy.retrocraft.smoke.ClientSmoke;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("retrocraft_smoke")
public final class ForgeClientSmoke {
    public ForgeClientSmoke(FMLJavaModLoadingContext context) {
        com.sammwy.retrocraft.compat.ForgeEvents.mod(
                context,
                FMLClientSetupEvent.class,
                (FMLClientSetupEvent event) -> event.enqueueWork(ClientSmoke::install));
    }
}
