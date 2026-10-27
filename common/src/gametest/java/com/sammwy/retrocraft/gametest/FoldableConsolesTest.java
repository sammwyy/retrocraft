package com.sammwy.retrocraft.gametest;

import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.console.ConsoleType;
import com.sammwy.retrocraft.server.console.SystemCores;
import com.sammwy.retrocraft.server.item.HandheldConsoleItem;
import com.sammwy.retrocraft.server.screen.*;

import net.minecraft.core.registries.BuiltInRegistries;

public class FoldableConsolesTest implements GameTestPlatform {
    @Scenario
    public void foldablesHaveIndependentItemsAndTheCorrectCartridgeAndCore(
            ScenarioContext context) {
        String[][] systems = {
            {"nds", "nds", "desmume"}, {"3ds", "3ds", "citra"}, {"gba_sp", "gba", "mgba"}
        };
        for (String[] system : systems) {
            var definition = ConsoleRegistry.get(Ids.of("retrocraft", system[0]));
            context.assertTrue(
                    definition != null && definition.type() == ConsoleType.HANDHELD,
                    system[0] + " is registered as a handheld");
            context.assertTrue(
                    ContentProperties.get(BuiltInRegistries.ITEM, definition.id())
                            instanceof HandheldConsoleItem,
                    system[0] + " has a usable console item");
            context.assertTrue(
                    definition.cartridge()
                            == ConsoleRegistry.media(Ids.of("retrocraft", system[1])),
                    system[0] + " accepts its system's game cards");
            context.assertTrue(
                    SystemCores.compatible("retrocraft:" + system[1]).contains(system[2]),
                    system[0] + " resolves to a trusted compatible core");
        }
        context.assertTrue(
                ConsoleRegistry.get(Ids.of("retrocraft", "gba")).cartridge()
                        == ConsoleRegistry.get(Ids.of("retrocraft", "gba_sp")).cartridge(),
                "GBA and GBA SP share existing games and saves");
        context.succeed();
    }
}
