package com.sammwy.retrocraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.sammwy.retrocraft.compat.CloneableConsoleBlock;
import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.compat.ModelData;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.roms.RomCatalog;
import com.sammwy.retrocraft.server.block.ArcadeSkin;
import com.sammwy.retrocraft.server.block.DockedConsoleBlock;
import com.sammwy.retrocraft.server.block.DockedConsoleBlockEntity;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.console.ConsoleType;
import com.sammwy.retrocraft.server.console.Consoles;
import com.sammwy.retrocraft.server.item.ArcadeItem;
import com.sammwy.retrocraft.server.item.CartridgeItem;
import com.sammwy.retrocraft.server.item.RetrocraftItemGroups;
import com.sammwy.retrocraft.server.screen.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ArcadeVariantsTest implements GameTestPlatform {
    @Scenario
    public void internalRomSurvivesPlacementSavingBreakingAndReplacement(ScenarioContext context) {
        var world = context.getLevel();
        var pos = context.absolutePos(new BlockPos(1, 2, 1));
        world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(pos.above(2), Blocks.AIR.defaultBlockState());
        var item =
                (ArcadeItem)
                        ContentProperties.get(
                                BuiltInRegistries.ITEM, Ids.of("retrocraft", "arcade_cabinet"));
        String hash = "a".repeat(64);
        var stack = item.game(hash, "pacman");
        var player = context.makeMockServerPlayerInLevel();
        player.setPos(pos.getCenter().add(3, 0, 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var placement =
                new BlockPlaceContext(
                        new UseOnContext(
                                player,
                                InteractionHand.MAIN_HAND,
                                new BlockHitResult(
                                        Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        context.assertTrue(
                item.place(placement).consumesAction(), "A catalog variant must be placeable");
        var console = (DockedConsoleBlockEntity) world.getBlockEntity(pos);
        context.assertTrue(
                console != null && console.hasInternalRom(), "Placed arcade must be permanent");
        context.assertTrue(
                hash.equals(CartridgeItem.gameId(console.cartridge())),
                "Placement must install its own game");
        var inv = new ConsoleInventory(console);
        context.assertTrue(
                inv.removeItem(0, 1).isEmpty() && inv.removeItemNoUpdate(0).isEmpty(),
                "Both removal paths must reject internal ROMs");
        inv.clearContent();
        inv.setItem(0, console.definition().cartridge().game("b".repeat(64), "tetris"));
        context.assertTrue(
                hash.equals(CartridgeItem.gameId(console.cartridge())),
                "Clear and replacement must leave the internal game intact");
        var menu = new ConsoleScreenHandler(1, player.getInventory(), console, -1);
        context.assertTrue(
                !menu.getSlot(0).mayPickup(player)
                        && !menu.getSlot(0).mayPlace(console.cartridge()),
                "Internal slot must be locked even while powered off");
        menu.clicked(0, 0, ClickType.CLONE, player);
        context.assertTrue(
                menu.getCarried().isEmpty(), "Creative clone cannot extract an internal ROM");
        context.assertTrue(
                menu.quickMoveStack(player, 0).isEmpty(),
                "Shift-click cannot extract an internal ROM");
        context.assertTrue(
                !menu.clickMenuButton(player, ConsoleScreenHandler.EJECT),
                "Eject button must reject internal ROMs");
        var saved = console.saveWithFullMetadata(world.registryAccess());
        var restored =
                (DockedConsoleBlockEntity)
                        BlockEntity.loadStatic(
                                pos, console.getBlockState(), saved, world.registryAccess());
        context.assertTrue(
                restored != null && hash.equals(CartridgeItem.gameId(restored.cartridge())),
                "Saving and loading must preserve the internal game");
        var picked =
                CloneableConsoleBlock.pick(
                        console.getBlockState().getBlock(), world, pos, console.getBlockState());
        context.assertTrue(
                hash.equals(ArcadeItem.gameId(picked)), "Pick-block must preserve the variant");
        console.instance(UUID.randomUUID());
        console.instance(null);
        context.assertTrue(
                hash.equals(CartridgeItem.gameId(console.cartridge())),
                "Power cycling must retain the board");
        List<ItemStack> drops = Block.getDrops(console.getBlockState(), world, pos, console);
        context.assertTrue(
                drops.size() == 1
                        && drops.getFirst().is(item)
                        && hash.equals(ArcadeItem.gameId(drops.getFirst())),
                "Breaking must drop only the same arcade variant");
        world.destroyBlock(pos, true);
        context.assertTrue(
                world.getEntities(
                                EntityType.ITEM,
                                new net.minecraft.world.phys.AABB(pos).inflate(1),
                                entity -> entity.getItem().is(console.definition().cartridge()))
                        .isEmpty(),
                "Breaking must never scatter the internal ROM");
        player.setItemInHand(InteractionHand.MAIN_HAND, drops.getFirst());
        context.assertTrue(
                item.place(
                                new BlockPlaceContext(
                                        new UseOnContext(
                                                player,
                                                InteractionHand.MAIN_HAND,
                                                new BlockHitResult(
                                                        Vec3.atCenterOf(pos),
                                                        Direction.UP,
                                                        pos,
                                                        false))))
                        .consumesAction(),
                "Dropped arcade must be placeable again");
        var replaced = (DockedConsoleBlockEntity) world.getBlockEntity(pos);
        context.assertTrue(
                hash.equals(CartridgeItem.gameId(replaced.cartridge())),
                "Replaced arcade must keep the original game");
        world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        context.succeed();
    }

    @Scenario
    public void creativeCatalogSeparatesHardwareGamesAndPermanentArcades(ScenarioContext context) {
        var old = RomCatalog.clientEntries();
        try {
            RomCatalog.clientEntries(
                    List.of(
                            new RomCatalog.Entry("retrocraft:arcade", "a".repeat(64), "pacman"),
                            new RomCatalog.Entry("retrocraft:arcade", "b".repeat(64), "puckman"),
                            new RomCatalog.Entry("retrocraft:nes", "c".repeat(64), "NES Game")));
            var display =
                    new CreativeModeTab.ItemDisplayParameters(
                            context.getLevel().enabledFeatures(),
                            true,
                            context.getLevel().registryAccess());
            CreativeModeTabs.tryRebuildTabContents(
                    display.enabledFeatures(), true, display.holders());
            RetrocraftItemGroups.CONSOLES.get().buildContents(display);
            RetrocraftItemGroups.GAMES.get().buildContents(display);
            RetrocraftItemGroups.ARCADES.get().buildContents(display);
            var generic =
                    ContentProperties.get(BuiltInRegistries.ITEM, Ids.of("retrocraft", "arcade"));
            context.assertTrue(
                    RetrocraftItemGroups.CONSOLES.get().getDisplayItems().stream()
                            .anyMatch(stack -> stack.is(generic)),
                    "Consoles tab must include the generic arcade");
            context.assertTrue(
                    RetrocraftItemGroups.CONSOLES.get().getDisplayItems().stream()
                            .noneMatch(
                                    stack ->
                                            stack.getItem() instanceof CartridgeItem
                                                    || stack.getItem() instanceof ArcadeItem),
                    "Consoles tab must contain hardware only");
            context.assertTrue(
                    RetrocraftItemGroups.GAMES.get().getDisplayItems().size() == 3,
                    "Games tab must list every ROM");
            context.assertTrue(
                    RetrocraftItemGroups.ARCADES.get().getDisplayItems().size() == 2,
                    "Arcades tab must create one variant per arcade ROM, even when art is shared");
            context.assertTrue(
                    RetrocraftItemGroups.ARCADES.get().getDisplayItems().stream()
                            .allMatch(
                                    stack ->
                                            ModelData.skin(stack)
                                                    == ArcadeSkin.of(ArcadeItem.gameName(stack))
                                                            .ordinal()),
                    "Every variant must bake its skin into custom model data for the item model");
            var board = ConsoleRegistry.media(Ids.of("retrocraft", "arcade"));
            List<Component> tooltip = new ArrayList<>();
            board.tooltip(board.game("a".repeat(64), "pacman"), tooltip);
            context.assertTrue(
                    tooltip.size() == 2
                            && tooltip.getFirst().getString().equals("Compatible with:")
                            && tooltip.get(1).getString().strip().equals("Arcade Machine"),
                    "Compatibility must have a heading and a separate console line, without internal variants");
        } finally {
            RomCatalog.clientEntries(old);
        }
        context.succeed();
    }

    @Scenario
    public void allSkinsUseTheSameRomBoardAndKeepTheirOwnBlocks(ScenarioContext context) {
        var board = ConsoleRegistry.media(Ids.of("retrocraft", "arcade"));
        String[] ids = {"arcade", "arcade_tetris", "arcade_space_invaders"};
        for (int i = 0; i < ids.length; i++) {
            var definition = ConsoleRegistry.get(Ids.of("retrocraft", ids[i]));
            context.assertTrue(definition != null, "Every arcade skin must be registered");
            context.assertTrue(
                    definition.type() == ConsoleType.ARCADE,
                    "Arcades must have their own physical type");
            context.assertTrue(
                    definition.type().isPlaced(),
                    "Arcades must use block video and multiplayer paths");
            context.assertTrue(
                    !definition.usesTelevision(), "Every arcade skin must use its integrated CRT");
            context.assertTrue(
                    definition.cartridge() == board,
                    "Every skin must accept the same arcade ROM board");
            var block =
                    (DockedConsoleBlock)
                            ContentProperties.get(BuiltInRegistries.BLOCK, definition.id());
            var pos = context.absolutePos(new BlockPos(i * 4, 2, 0));
            var world = context.getLevel();
            world.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
            world.setBlockAndUpdate(pos.above(2), Blocks.AIR.defaultBlockState());
            world.setBlockAndUpdate(pos, block.defaultBlockState());
            var console = (DockedConsoleBlockEntity) world.getBlockEntity(pos);
            context.assertTrue(
                    console != null && console.definition() == definition,
                    "A placed skin must keep its console identity");
            var inventory = new ConsoleInventory(console);
            inventory.setItem(0, new ItemStack(board));
            context.assertTrue(
                    inventory.getItem(0).is(board),
                    "Each skin must accept a ROM board through the shared inventory");
            console.instance(UUID.randomUUID());
            context.assertTrue(
                    world.getBlockState(pos).getValue(DockedConsoleBlock.POWERED),
                    "Each skin must expose its powered blockstate");
            console.instance(null);
            context.assertTrue(
                    !world.getBlockState(pos).getValue(DockedConsoleBlock.POWERED),
                    "Powering off must restore the unpowered skin");
            context.assertTrue(
                    inventory.removeItemNoUpdate(0).is(board),
                    "Powering off must preserve the inserted ROM board");
            context.assertTrue(
                    inventory.isEmpty(), "Ejecting must clear the shared cartridge slot");
            world.setBlockAndUpdate(pos.above(2), Blocks.STONE.defaultBlockState());
            context.assertTrue(
                    !block.defaultBlockState().canSurvive(world, pos),
                    "All three-block cabinets must reject a blocked third block");
            world.setBlockAndUpdate(pos.above(2), Blocks.AIR.defaultBlockState());
            context.assertTrue(
                    block.defaultBlockState().canSurvive(world, pos),
                    "Cabinets must allow placement with free space above");
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var state = block.defaultBlockState().setValue(DockedConsoleBlock.FACING, facing);
                var bounds = state.getShape(world, pos).bounds();
                context.assertTrue(
                        Math.abs(bounds.getYsize() - 3) < .001,
                        "Every orientation must select the full three-block height");
                context.assertTrue(
                        bounds.getXsize() > 1 && bounds.getZsize() > 1,
                        "Every skin must keep its enlarged footprint after rotation");
            }
            world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        context.assertTrue(
                Consoles.NES.type() == ConsoleType.DOCKED && Consoles.NES.usesTelevision(),
                "Home consoles must still use external televisions");
        context.assertTrue(
                !Consoles.NES_HANDHELD.type().isPlaced() && !Consoles.NES_HANDHELD.usesTelevision(),
                "Handhelds must keep their personal screen path");
        context.succeed();
    }
}
