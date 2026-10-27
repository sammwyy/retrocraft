package com.sammwy.retrocraft.gametest;

import java.util.UUID;

import com.sammwy.retrocraft.server.console.ConsoleDefinition;
import com.sammwy.retrocraft.server.console.ConsoleHost;
import com.sammwy.retrocraft.server.screen.*;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class ConsoleInventoryTest implements GameTestPlatform {
    @Scenario
    public void concurrentMenusShareTheCartridgeAndCannotWithdrawItTwice(ScenarioContext context) {
        var host = new Host();
        var first = new ConsoleInventory(host);
        var second = new ConsoleInventory(host);
        first.setItem(0, new ItemStack(Items.PAPER));
        context.assertTrue(
                second.getItem(0).getCount() == 1,
                "The second menu must observe a newly inserted cartridge");
        context.assertTrue(
                second.removeItemNoUpdate(0).getCount() == 1,
                "The second menu must withdraw the cartridge");
        context.assertTrue(
                first.isEmpty(), "The first menu must immediately observe the withdrawal");
        context.assertTrue(
                first.removeItemNoUpdate(0).isEmpty(),
                "A second withdrawal must not duplicate the cartridge");
        second.setItem(0, new ItemStack(Items.PAPER));
        context.assertTrue(
                first.removeItem(0, 1).getCount() == 1,
                "Partial removal must return the inserted cartridge");
        context.assertTrue(
                second.getItem(0).isEmpty(), "Partial removal must update every open menu");
        context.succeed();
    }

    private static final class Host implements ConsoleHost {
        private ItemStack cartridge = ItemStack.EMPTY;

        public ConsoleDefinition definition() {
            return null;
        }

        public ItemStack cartridge() {
            return cartridge;
        }

        public void cartridge(ItemStack value) {
            cartridge = value;
        }

        public UUID instance() {
            return null;
        }

        public void instance(UUID id) {}

        public ServerLevel world() {
            return null;
        }

        public Vec3 position() {
            return Vec3.ZERO;
        }

        public boolean valid() {
            return true;
        }

        public boolean canAccess(ServerPlayer player) {
            return false;
        }
    }
}
