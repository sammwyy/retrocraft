package com.sammwy.retrocraft.client.ui;

import java.util.*;

import org.lwjgl.glfw.GLFW;

import com.sammwy.retrocraft.compat.ClientGraphics;
import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.compat.UiPose;
import com.sammwy.retrocraft.server.console.component.ComponentStorage;
import com.sammwy.retrocraft.server.screen.ConsoleScreenHandler;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class ConsoleInventoryScreen
        extends com.sammwy.retrocraft.compat.InputContainerScreen<ConsoleScreenHandler> {
    private SettingsWidgets.Flat power, eject;
    private final UiMotion motion = new UiMotion();
    private final ConsoleTheme theme;
    private final ItemStack consoleStack;

    public ConsoleInventoryScreen(
            ConsoleScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 176, 176);
        inventoryLabelY = 82;
        theme = ConsoleTheme.of(handler.definition().id().getPath());
        consoleStack =
                new ItemStack(
                        ContentProperties.get(BuiltInRegistries.ITEM, handler.definition().id()));
    }

    @Override
    protected void init() {
        super.init();
        power =
                addRenderableWidget(
                        new SettingsWidgets.Flat(
                                leftPos + 8,
                                topPos + 60,
                                78,
                                18,
                                Component.empty(),
                                () -> button(ConsoleScreenHandler.POWER)));
        eject =
                addRenderableWidget(
                        new SettingsWidgets.Flat(
                                leftPos + 90,
                                topPos + 60,
                                78,
                                18,
                                Component.translatable("screen.retrocraft.eject"),
                                () -> button(ConsoleScreenHandler.EJECT)));
        updateButtons();
    }

    private void button(int action) {
        if (minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }

    private void updateButtons() {
        power.setMessage(
                Component.translatable(
                        menu.powered()
                                ? "screen.retrocraft.power_off"
                                : "screen.retrocraft.power_on"));
        power.active = menu.powered() || menu.getSlot(0).hasItem();
        eject.visible = !menu.definition().internalRom();
        eject.active = eject.visible && menu.getSlot(0).hasItem();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    @Override
    protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
        int shell = theme.shell();
        context.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff14171e);
        context.fill(
                leftPos + 1,
                topPos + 1,
                leftPos + imageWidth - 1,
                topPos + imageHeight - 1,
                0xff2b303a);
        context.fill(
                leftPos + 1,
                topPos + 1,
                leftPos + imageWidth - 1,
                topPos + 80,
                ConsoleTheme.shade(shell, .28f));
        context.fill(
                leftPos + imageWidth - 15,
                topPos + 8,
                leftPos + imageWidth - 10,
                topPos + 13,
                menu.powered() ? 0xffd9453a : 0xff5a2a28);
        // Cartridge bay: recessed slot with gold contacts under it.
        var slot = menu.slots.getFirst();
        context.fill(
                leftPos + slot.x - 5,
                topPos + slot.y - 5,
                leftPos + slot.x + 21,
                topPos + slot.y + 22,
                0xff0f1116);
        context.fill(
                leftPos + slot.x - 4,
                topPos + slot.y - 4,
                leftPos + slot.x + 20,
                topPos + slot.y + 21,
                ConsoleTheme.shade(shell, .5f));
        for (int i = 0; i < 6; i++)
            context.fill(
                    leftPos + slot.x - 2 + i * 4,
                    topPos + slot.y + 18,
                    leftPos + slot.x + i * 4,
                    topPos + slot.y + 20,
                    0xffd7b15a);
        // Component ports: a smaller dark socket with a pin strip along its top edge.
        for (int i = 1; i < menu.machineSlots(); i++) {
            var port = menu.slots.get(i);
            context.fill(
                    leftPos + port.x - 3,
                    topPos + port.y - 3,
                    leftPos + port.x + 19,
                    topPos + port.y + 19,
                    0xff0f1116);
            context.fill(
                    leftPos + port.x - 2,
                    topPos + port.y - 2,
                    leftPos + port.x + 18,
                    topPos + port.y + 18,
                    ConsoleTheme.shade(shell, .4f));
            for (int k = 0; k < 4; k++)
                context.fill(
                        leftPos + port.x + 2 + k * 4,
                        topPos + port.y - 2,
                        leftPos + port.x + 4 + k * 4,
                        topPos + port.y - 1,
                        0xffb8bcc6);
        }
        // Player items that cannot go into this console (other cartridges, anything else) get a red
        // backing.
        for (var s : menu.slots) {
            boolean wrong = !s.getItem().isEmpty() && !accepted(s.getItem());
            context.fill(
                    leftPos + s.x - 1,
                    topPos + s.y - 1,
                    leftPos + s.x + 17,
                    topPos + s.y + 17,
                    wrong ? 0xff8a3030 : 0xff727986);
            context.fill(
                    leftPos + s.x,
                    topPos + s.y,
                    leftPos + s.x + 16,
                    topPos + s.y + 16,
                    wrong ? 0xff5a1f1f : 0xff181c24);
        }
    }

    private boolean accepted(ItemStack stack) {
        if (stack.is(menu.definition().cartridge())) return true;
        var component = ComponentStorage.component(stack);
        return menu.definition().componentSlots().stream().anyMatch(s -> s.accepts(component));
    }

    /** Empty component ports name themselves and what they take. */
    private void drawPortTooltip(GuiGraphics context, int mouseX, int mouseY) {
        if (hoveredSlot == null || hoveredSlot.hasItem()) return;
        int index = menu.slots.indexOf(hoveredSlot);
        if (index < 1 || index >= menu.machineSlots()) return;
        var port = menu.definition().componentSlots().get(index - 1);
        List<Component> lines = new ArrayList<>();
        lines.add(port.displayName());
        for (var component : port.accepts())
            lines.add(
                    Component.literal("  ")
                            .append(component.displayName())
                            .withStyle(ChatFormatting.GRAY));
        com.sammwy.retrocraft.compat.UiTooltips.show(context, font, lines, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics context, int mouseX, int mouseY) {
        context.renderItem(consoleStack, 6, 3);
        context.drawString(font, title, 26, 8, 0xffffff, false);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float visibility = motion.value(now);
        if (!motion.shown() && motion.settled(now)) {
            super.onClose();
            return;
        }
        context.fill(0, 0, width, height, (int) (160 * visibility) << 24);
        UiPose.of(context).pushPose();
        try {
            UiDrawing.pop(
                    context, leftPos + imageWidth / 2f, topPos + imageHeight / 2f, visibility);
            int mx = acceptingInput() ? mouseX : -1, my = acceptingInput() ? mouseY : -1;
            super.render(context, mx, my, delta);
            if (acceptingInput()) {
                renderTooltip(context, mx, my);
                drawPortTooltip(context, mx, my);
            }
        } finally {
            ClientGraphics.color(context, 1, 1, 1, 1);
            UiPose.of(context).popPose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBg(context, delta, mouseX, mouseY);
    }

    private boolean acceptingInput() {
        return motion.shown() && motion.settled(System.nanoTime());
    }

    @Override
    public void onClose() {
        motion.show(false, System.nanoTime());
        setFocused(null);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return !acceptingInput() || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return !acceptingInput() || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return !acceptingInput() || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        return !acceptingInput() || super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!motion.shown()) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE
                || com.sammwy.retrocraft.compat.ClientInput.matches(
                        minecraft.options.keyInventory, key, scanCode)) {
            onClose();
            return true;
        }
        return !acceptingInput() || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
