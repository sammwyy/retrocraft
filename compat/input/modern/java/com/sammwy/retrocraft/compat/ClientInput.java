package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.*;

public final class ClientInput {
    public static long window(Window window) {
        return window.handle();
    }

    public static boolean keyDown(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
    }

    public static boolean matches(KeyMapping binding, int key, int scan) {
        return binding.matches(new KeyEvent(key, scan, 0));
    }

    public static boolean key(EditBox field, int key, int scan, int modifiers) {
        return field.keyPressed(new KeyEvent(key, scan, modifiers));
    }

    public static boolean character(EditBox field, char chr, int modifiers) {
        return field.charTyped(NativeInput.character(chr, modifiers));
    }

    public static boolean click(AbstractWidget widget, double x, double y, int button) {
        return widget.mouseClicked(NativeInput.mouse(x, y, button), false);
    }

    public static void activate(AbstractWidget widget) {
        widget.onClick(NativeInput.mouse(widget.getX(), widget.getY(), 0), false);
    }

    public static KeyMapping key(String name, InputConstants.Type type, int key, String category) {
        return new KeyMapping(
                name,
                type,
                key,
                KeyMapping.Category.register(
                        com.sammwy.retrocraft.platform.Ids.of("retrocraft", "main")));
    }

    public static boolean screenKey(
            net.minecraft.client.gui.screens.Screen screen, int key, int scan, int modifiers) {
        return screen.keyPressed(new KeyEvent(key, scan, modifiers));
    }

    private ClientInput() {}
}
