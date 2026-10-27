package com.sammwy.retrocraft.gametest;

import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.SystemCores;
import com.sammwy.retrocraft.server.item.HandheldConsoleItem;
import com.sammwy.retrocraft.server.screen.*;

import net.minecraft.core.registries.BuiltInRegistries;

public class PspConsoleTest implements GameTestPlatform {
    @Scenario
    public void pspUsesItsOwnUmdAndPortableCore(ScenarioContext context) {
        var id = Ids.of("retrocraft", "psp");
        var console = ConsoleRegistry.get(id);
        context.assertTrue(
                console != null && console.type() == ConsoleType.HANDHELD, "PSP is a handheld");
        context.assertTrue(
                ContentProperties.get(BuiltInRegistries.ITEM, id) instanceof HandheldConsoleItem,
                "PSP has a usable item");
        context.assertTrue(
                console.cartridge() == ConsoleRegistry.media(Ids.of("retrocraft", "psp")),
                "PSP accepts UMD games");
        context.assertTrue(!console.usesTelevision(), "PSP uses its own screen");
        context.assertTrue(
                SystemCores.defaultForCartridge("retrocraft:psp").equals("ppsspp"),
                "PSP resolves to PPSSPP");
        context.succeed();
    }
}
