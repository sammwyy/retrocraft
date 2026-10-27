package com.sammwy.retrocraft.client.ui;

import static com.sammwy.retrocraft.client.ui.SettingsWidgets.*;

import java.io.IOException;
import java.util.*;

import org.lwjgl.glfw.GLFW;

import com.sammwy.retrocraft.client.ClientPreferences;
import com.sammwy.retrocraft.client.InputControls;
import com.sammwy.retrocraft.client.RetrocraftClient;
import com.sammwy.retrocraft.client.config.ControllerProfile;
import com.sammwy.retrocraft.client.config.ControllerProfile.Action;
import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.server.console.ConsoleDefinition;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Local bindings editor. Drafts are committed through the settings footer's Apply button. */
final class ControlsPane {
    private record Row(Action action, Component label, int height) {}

    private Map<String, Map<String, Integer>> draft = copyBindings();
    private List<ConsoleDefinition> consoles = List.of();
    private ControllerProfile selected;
    private List<Row> rows = List.of();
    private Action recording;
    private boolean active, dragging;
    private Component captureHint;
    private int x, y, w, h;
    private float scroll;

    void init(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        consoles = List.copyOf(ConsoleRegistry.consoles());
        scroll = Math.min(scroll, maxScroll());
    }

    void setActive(boolean active) {
        this.active = active;
        if (!active) {
            recording = null;
            dragging = false;
            captureHint = null;
        }
    }

    boolean recording() {
        return active && recording != null;
    }

    private static Map<String, Map<String, Integer>> copyBindings() {
        var result = new LinkedHashMap<String, Map<String, Integer>>();
        ClientPreferences.get()
                .controls
                .forEach((console, values) -> result.put(console, new LinkedHashMap<>(values)));
        return result;
    }

    int dirtyCount() {
        int count = 0;
        for (var console : consoles) {
            var profile = ControllerProfile.of(console.id().getPath());
            var before = ClientPreferences.get().controls(profile.id());
            var after = draft.getOrDefault(profile.id(), Map.of());
            for (Action action : profile.actions())
                if (profile.key(before, action) != profile.key(after, action)) count++;
        }
        return count;
    }

    void apply() throws IOException {
        ClientPreferences.applyControls(draft);
        draft = copyBindings();
    }

    void resetDraft() {
        draft = copyBindings();
        recording = null;
        captureHint = null;
    }

    private Map<String, Integer> bindings() {
        return draft.getOrDefault(selected.id(), Map.of());
    }

    private void open(ConsoleDefinition console) {
        selected = ControllerProfile.of(console.id().getPath());
        var list = new ArrayList<Row>();
        list.add(section("dpad"));
        for (Action action : List.of(Action.UP, Action.DOWN, Action.LEFT, Action.RIGHT))
            list.add(row(action));
        list.add(section("buttons"));
        for (var button : selected.buttons()) list.add(row(button.action()));
        if (selected.sticks() > 0) {
            list.add(section("left_stick"));
            for (Action action :
                    List.of(Action.LEFT_UP, Action.LEFT_DOWN, Action.LEFT_LEFT, Action.LEFT_RIGHT))
                list.add(row(action));
        }
        if (selected.sticks() > 1) {
            list.add(section(selected.rightStick().equals("C") ? "c_buttons" : "right_stick"));
            for (Action action :
                    List.of(
                            Action.RIGHT_UP,
                            Action.RIGHT_DOWN,
                            Action.RIGHT_LEFT,
                            Action.RIGHT_RIGHT)) list.add(row(action));
        }
        rows = List.copyOf(list);
        scroll = 0;
        recording = null;
        captureHint = null;
    }

    private Row section(String key) {
        return new Row(null, Component.translatable("controls.retrocraft." + key), 18);
    }

    private Row row(Action action) {
        String label = selected.label(action);
        Component text =
                action.analog() || action.ordinal() >= 4 && action.ordinal() <= 7
                        ? Component.translatable("controls.retrocraft.action." + action.id())
                        : Set.of("cross", "circle", "square", "triangle").contains(label)
                                ? Component.translatable("controls.retrocraft." + label)
                                : Component.literal(label);
        return new Row(action, text, 24);
    }

    private int listY() {
        return y + (selected == null ? 22 : 40);
    }

    private int listH() {
        return Math.max(1, y + h - listY());
    }

    private float maxScroll() {
        int total =
                selected == null ? consoles.size() * 28 : rows.stream().mapToInt(Row::height).sum();
        return Math.max(0, total - listH());
    }

    void render(GuiGraphics context, int mx, int my) {
        if (!active) return;
        var font = Minecraft.getInstance().font;
        if (selected == null) {
            context.drawString(
                    font,
                    Component.translatable("controls.retrocraft.choose"),
                    x + 2,
                    y + 4,
                    MUTED,
                    false);
        } else {
            button(context, x, y, 24, "‹", mx, my, false);
            context.drawString(
                    font,
                    font.plainSubstrByWidth(
                            Component.translatable("console.retrocraft." + selected.id())
                                    .getString(),
                            Math.max(1, w - 112)),
                    x + 32,
                    y + 5,
                    TEXT,
                    false);
            button(
                    context,
                    x + w - 76,
                    y,
                    70,
                    Component.translatable("controls.retrocraft.defaults").getString(),
                    mx,
                    my,
                    false);
            Component hint =
                    recording != null
                            ? captureHint != null
                                    ? captureHint
                                    : Component.translatable("controls.retrocraft.recording")
                            : Component.translatable("controls.retrocraft.hint");
            context.drawString(
                    font,
                    font.plainSubstrByWidth(hint.getString(), w - 6),
                    x + 2,
                    y + 25,
                    recording != null ? ACCENT_HI : MUTED,
                    false);
        }
        UiDrawing.scissor(context, x, listY(), x + w, listY() + listH());
        int ry = listY() - (int) scroll;
        if (selected == null) {
            for (var console : consoles) {
                boolean hot = inside(mx, my, x, ry, w - 8, 26);
                rounded(context, x, ry, w - 8, 26, hot ? PANEL_HI : CONTROL, 3);
                context.renderItem(
                        new ItemStack(ContentProperties.get(BuiltInRegistries.ITEM, console.id())),
                        x + 5,
                        ry + 5);
                context.drawString(
                        font,
                        font.plainSubstrByWidth(console.displayName().getString(), w - 46),
                        x + 28,
                        ry + 9,
                        TEXT,
                        false);
                context.drawString(font, "›", x + w - 20, ry + 9, MUTED, false);
                ry += 28;
            }
        } else {
            for (Row row : rows) {
                if (row.action() == null) {
                    context.drawString(font, row.label(), x + 3, ry + 7, MUTED, false);
                } else {
                    int key = selected.key(bindings(), row.action());
                    boolean conflict =
                            key != -1
                                    && selected.actions().stream()
                                            .anyMatch(
                                                    other ->
                                                            other != row.action()
                                                                    && selected.key(
                                                                                    bindings(),
                                                                                    other)
                                                                            == key);
                    boolean capturing = recording == row.action();
                    boolean hot = inside(mx, my, x, ry, w - 8, 22);
                    if (hot || capturing)
                        rounded(context, x, ry, w - 8, 22, capturing ? PANEL_HI : CONTROL, 3);
                    boolean glyph =
                            PadGlyphs.draw(
                                    context, selected.label(row.action()), x + 5, ry + 7, false);
                    context.drawString(
                            font,
                            font.plainSubstrByWidth(row.label().getString(), Math.max(1, w - 124)),
                            x + (glyph ? 18 : 5),
                            ry + 7,
                            TEXT,
                            false);
                    button(
                            context,
                            x + w - 108,
                            ry + 2,
                            100,
                            capturing ? "…" : InputControls.keyName(key),
                            mx,
                            my,
                            conflict);
                }
                ry += row.height();
            }
        }
        context.disableScissor();
        if (maxScroll() > 0) {
            int thumb = Math.max(12, (int) (listH() * listH() / (maxScroll() + listH())));
            int ty = listY() + Math.round((listH() - thumb) * scroll / maxScroll());
            rounded(context, x + w - 3, listY(), 3, listH(), 0x20FFFFFF, 1);
            rounded(context, x + w - 3, ty, 3, thumb, 0x90FFFFFF, 1);
        }
    }

    private void button(
            GuiGraphics context,
            int bx,
            int by,
            int bw,
            String text,
            int mx,
            int my,
            boolean conflict) {
        var font = Minecraft.getInstance().font;
        rounded(
                context,
                bx,
                by,
                bw,
                18,
                inside(mx, my, bx, by, bw, 18) ? CONTROL_HOVER : CONTROL,
                3);
        String label = font.plainSubstrByWidth(text, bw - 8);
        context.drawString(
                font,
                label,
                bx + (bw - font.width(label)) / 2,
                by + 5,
                conflict ? BAD : TEXT,
                false);
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (!active) return false;
        if (recording != null) {
            assign(-button - 2);
            return true;
        }
        if (button != 0) return false;
        if (selected != null && inside(mx, my, x, y, 24, 18)) {
            selected = null;
            scroll = 0;
            rows = List.of();
            click(Minecraft.getInstance());
            return true;
        }
        if (selected != null && inside(mx, my, x + w - 76, y, 70, 18)) {
            draft.remove(selected.id());
            click(Minecraft.getInstance());
            return true;
        }
        if (!inside(mx, my, x, listY(), w, listH())) return false;
        if (maxScroll() > 0 && mx >= x + w - 6) {
            dragging = true;
            drag(my);
            return true;
        }
        int ry = listY() - (int) scroll;
        if (selected == null) {
            for (var console : consoles) {
                if (ry >= listY() && ry + 26 <= y + h && inside(mx, my, x, ry, w - 8, 26)) {
                    open(console);
                    click(Minecraft.getInstance());
                    return true;
                }
                ry += 28;
            }
        } else {
            for (var row : rows) {
                if (row.action() != null
                        && ry >= listY()
                        && ry + 22 <= y + h
                        && inside(mx, my, x, ry, w - 8, 22)) {
                    recording = row.action();
                    captureHint = null;
                    click(Minecraft.getInstance());
                    return true;
                }
                ry += row.height();
            }
        }
        return true;
    }

    boolean keyPressed(int key, int scanCode) {
        if (recording == null) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            recording = null;
            captureHint = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            assign(-1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_UNKNOWN) return true;
        if (key == GLFW.GLFW_KEY_F8
                || com.sammwy.retrocraft.compat.ClientInput.matches(
                        RetrocraftClient.SETTINGS_KEY, key, scanCode)) {
            captureHint = Component.translatable("controls.retrocraft.reserved");
            return true;
        }
        assign(key);
        return true;
    }

    private void assign(int key) {
        var values = draft.computeIfAbsent(selected.id(), ignored -> new LinkedHashMap<>());
        if (key == selected.defaultKey(recording)) values.remove(recording.id());
        else values.put(recording.id(), key);
        if (values.isEmpty()) draft.remove(selected.id());
        recording = null;
        captureHint = null;
    }

    boolean mouseScrolled(double mx, double my, double vertical) {
        if (!active || recording != null || !inside(mx, my, x, listY(), w, listH())) return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (float) vertical * 24));
        return true;
    }

    boolean mouseDragged(double my) {
        if (!dragging) return false;
        drag(my);
        return true;
    }

    void mouseReleased() {
        dragging = false;
    }

    private void drag(double my) {
        scroll = Math.max(0, Math.min(maxScroll(), (float) (my - listY()) / listH() * maxScroll()));
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
