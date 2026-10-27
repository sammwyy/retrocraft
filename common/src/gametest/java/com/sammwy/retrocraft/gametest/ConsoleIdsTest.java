package com.sammwy.retrocraft.gametest;

import java.util.Map;

import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.console.ConsoleType;
import com.sammwy.retrocraft.server.item.CartridgeItem;
import com.sammwy.retrocraft.server.screen.*;

import net.minecraft.core.registries.BuiltInRegistries;

public class ConsoleIdsTest implements GameTestPlatform {
    @Scenario
    public void consolesAreNamedAfterTheirSystemAndMediaAfterItsKind(ScenarioContext context) {
        var media =
                Map.of(
                        "nes", "nes_cartridge",
                        "gba", "gba_cartridge",
                        "nds", "nds_card",
                        "3ds", "3ds_card",
                        "psp", "psp_umd",
                        "ps2", "ps2_disc",
                        "gamecube", "gamecube_disc",
                        "arcade", "arcade_board");
        media.forEach(
                (system, item) -> {
                    var cartridge = ConsoleRegistry.media(Ids.of("retrocraft", system));
                    context.assertTrue(cartridge != null, system + " is a registered system");
                    context.assertTrue(
                            BuiltInRegistries.ITEM.getKey(cartridge).getPath().equals(item),
                            system + " media is registered as " + item);
                });
        var consoles =
                Map.of(
                        "nes", ConsoleType.DOCKED,
                        "nes_handheld", ConsoleType.HANDHELD,
                        "gba", ConsoleType.HANDHELD,
                        "gba_sp", ConsoleType.HANDHELD,
                        "ps2", ConsoleType.DOCKED,
                        "wii", ConsoleType.DOCKED,
                        "arcade", ConsoleType.ARCADE,
                        "arcade_cabinet", ConsoleType.ARCADE);
        consoles.forEach(
                (id, type) -> {
                    var console = ConsoleRegistry.get(Ids.of("retrocraft", id));
                    context.assertTrue(
                            console != null && console.type() == type,
                            id + " is a " + type + " console");
                });
        for (var console : ConsoleRegistry.consoles()) {
            String path = console.id().getPath();
            context.assertTrue(
                    !path.endsWith("_console")
                            && !(path.endsWith("_handheld") && !path.equals("nes_handheld")),
                    path + " must not carry a redundant suffix");
            context.assertTrue(
                    ContentProperties.get(BuiltInRegistries.ITEM, console.id()) != null
                            && !(ContentProperties.get(BuiltInRegistries.ITEM, console.id())
                                    instanceof CartridgeItem),
                    path + " has its own hardware item");
        }
        context.succeed();
    }
}
