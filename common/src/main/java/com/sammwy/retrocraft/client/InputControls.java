package com.sammwy.retrocraft.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.sammwy.retrocraft.client.config.ControllerProfile;
import com.sammwy.retrocraft.client.ui.ConsoleScreen;
import com.sammwy.retrocraft.cores.worker.PadInput;

import net.minecraft.client.Minecraft;

public final class InputControls {
    public static PadInput sample(Minecraft client) {
        if (!client.isWindowActive()
                || !(client.screen instanceof ConsoleScreen screen)
                || !screen.acceptingInput()) return PadInput.IDLE;
        var profile = ControllerProfile.of(screen.consoleId());
        long window = com.sammwy.retrocraft.compat.ClientInput.window(client.getWindow());
        return screen.withTouch(
                profile.sample(
                        ClientPreferences.get().controls(profile.id()),
                        key ->
                                key >= 0
                                        ? com.sammwy.retrocraft.compat.ClientInput.keyDown(key)
                                        : GLFW.glfwGetMouseButton(window, -key - 2)
                                                == GLFW.GLFW_PRESS));
    }

    public static String keyName(int key) {
        if (key == -1) return "—";
        if (key <= -2)
            return InputConstants.Type.MOUSE.getOrCreate(-key - 2).getDisplayName().getString();
        return switch (key) {
            case GLFW.GLFW_KEY_UP -> "↑";
            case GLFW.GLFW_KEY_DOWN -> "↓";
            case GLFW.GLFW_KEY_LEFT -> "←";
            case GLFW.GLFW_KEY_RIGHT -> "→";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L Shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L Ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R Ctrl";
            default -> InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
        };
    }

    public static String keyName(String console, ControllerProfile.Action action) {
        var profile = ControllerProfile.of(console);
        return keyName(profile.key(ClientPreferences.get().controls(console), action));
    }

    private InputControls() {}
}
