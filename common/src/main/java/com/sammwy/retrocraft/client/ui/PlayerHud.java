package com.sammwy.retrocraft.client.ui;

import java.util.*;

import com.sammwy.retrocraft.client.ClientPreferences;
import com.sammwy.retrocraft.client.InputControls;
import com.sammwy.retrocraft.client.config.ControllerProfile;
import com.sammwy.retrocraft.client.config.ControllerProfile.Action;
import com.sammwy.retrocraft.compat.ClientGraphics;
import com.sammwy.retrocraft.compat.UiPose;
import com.sammwy.retrocraft.cores.worker.PadInput;
import com.sammwy.retrocraft.server.net.Packets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Bottom-of-screen controller "cards": one per occupied pad slot (P1 blue, P2 red, P3 green, P4
 * yellow), with the player's head and name on a saturated header and a translucent pad strip whose
 * chips flash and pop on every remote/local press. State is fed by {@link Packets.Players} (roster
 * + host) and {@link Packets.Pad} (virtual controller states).
 */
public final class PlayerHud {
    private static final int[] COLORS = {0xFF55A8FF, 0xFFFF5D5D, 0xFF63D97E, 0xFFFFD24A};
    private static final int HEADER_H = 11,
            CHIP_H = 9,
            PAD = 4,
            GAP = 8,
            CELL = 5,
            DPAD = 3 * CELL,
            MIDDLE_GAP = 28;
    private static final int UP = 1 << 4, DOWN = 1 << 5, LEFT = 1 << 6, RIGHT = 1 << 7;
    private static final long POP_MS = 110, JOIN_MS = 200;
    private static final Map<UUID, State> SESSIONS = new HashMap<>();

    private static final class Slot {
        UUID player;
        boolean host;
        int mask;
        PadInput input = PadInput.IDLE;
        final long[] pressAt = new long[16];
        long joinedAt, lastPress;
    }

    private static final class State {
        final Slot[] slots = {new Slot(), new Slot(), new Slot(), new Slot()};
        float rumble;
        long rumbleAt;
    }

    /** Keyboard controls represent motor feedback by shaking the local controller card. */
    public static void rumble(Packets.Rumble packet) {
        State state = SESSIONS.get(packet.instance());
        if (state == null) return;
        state.rumble = Math.max(packet.strong(), packet.weak()) / 65535f;
        state.rumbleAt = System.nanoTime();
    }

    private record HudButton(Action action, String label) {
        int mask() {
            return action.mask();
        }
    }

    private record Cell(HudButton button, int col, int row) {}

    /** Stacked centre column (Start/Select style) plus a face grid laid out like the real pad. */
    private static final Map<String, PadLayout> LAYOUTS = new HashMap<>();

    private record PadLayout(List<HudButton> middle, List<Cell> face, int cols, int rows) {}

    public static void roster(Packets.Players packet) {
        State state = SESSIONS.computeIfAbsent(packet.instance(), ignored -> new State());
        boolean[] seen = new boolean[state.slots.length];
        for (var entry : packet.entries()) {
            if (entry.slot() < 0 || entry.slot() >= state.slots.length) continue;
            Slot slot = state.slots[entry.slot()];
            seen[entry.slot()] = true;
            if (!entry.player().equals(slot.player)) {
                slot.player = entry.player();
                slot.mask = 0;
                slot.input = PadInput.IDLE;
                Arrays.fill(slot.pressAt, 0);
                slot.joinedAt = System.nanoTime();
                slot.lastPress = 0;
            }
            slot.host = entry.host();
        }
        for (int i = 0; i < state.slots.length; i++)
            if (!seen[i] && state.slots[i].player != null) {
                state.slots[i].player = null;
                state.slots[i].mask = 0;
                state.slots[i].input = PadInput.IDLE;
                state.slots[i].host = false;
            }
    }

    public static void pad(Packets.Pad packet) {
        if (packet.slot() < 0 || packet.slot() >= 4) return;
        State state = SESSIONS.get(packet.instance());
        if (state == null) return;
        update(state.slots[packet.slot()], packet.input());
    }

    private static void update(Slot slot, PadInput input) {
        int rising = input.buttons() & ~slot.mask;
        long now = System.nanoTime();
        if (!slot.input.equals(input) && !input.equals(PadInput.IDLE)) slot.lastPress = now;
        for (int bit = 0; bit < 16; bit++) if ((rising & (1 << bit)) != 0) slot.pressAt[bit] = now;
        slot.mask = input.buttons();
        slot.input = input;
    }

    public static void drop(UUID instance) {
        SESSIONS.remove(instance);
    }

    public static void clear() {
        SESSIONS.clear();
    }

    /** Slot occupied by {@code player} on {@code instance}, or -1 (spectator). */
    public static int slotOf(UUID instance, UUID player) {
        State state = SESSIONS.get(instance);
        if (state == null) return -1;
        for (int i = 0; i < state.slots.length; i++)
            if (player.equals(state.slots[i].player)) return i;
        return -1;
    }

    /** Last broadcast controller state, used for the ports a hybrid owner does not hold locally. */
    public static PadInput input(UUID instance, int slot) {
        State state = SESSIONS.get(instance);
        return state != null && slot >= 0 && slot < 4 ? state.slots[slot].input : PadInput.IDLE;
    }

    /** Face-button geometry per console: middle column first, then the face grid. */
    private static PadLayout layout(ConsoleTheme theme) {
        var profile = theme.profile();
        Map<Action, HudButton> by = new EnumMap<>(Action.class);
        for (var button : profile.buttons())
            by.put(button.action(), new HudButton(button.action(), button.label()));
        List<HudButton> mid = new ArrayList<>();
        if (by.get(Action.START) != null) mid.add(by.get(Action.START));
        if (by.get(Action.SELECT) != null) mid.add(by.get(Action.SELECT));
        List<Cell> face = new ArrayList<>();
        int cols = 2, rows = 1;
        switch (profile.layout()) {
            case TRIGGERS -> {
                diamond(face, by);
                face.add(new Cell(by.get(Action.L), 3, 0));
                face.add(new Cell(by.get(Action.R), 4, 0));
                face.add(new Cell(by.get(Action.L2), 3, 1));
                face.add(new Cell(by.get(Action.R2), 4, 1));
                cols = 5;
                rows = 3;
            }
            case DIAMOND, DIAMOND_WIDE -> {
                diamond(face, by);
                face.add(new Cell(by.get(Action.L), 3, 0));
                face.add(new Cell(by.get(Action.R), 3, 2));
                face.add(new Cell(by.get(Action.L2), 4, 0));
                face.add(new Cell(by.get(Action.R2), 4, 2));
                face.add(new Cell(by.get(Action.L3), 4, 1));
                cols = profile.layout() == ControllerProfile.FaceLayout.DIAMOND_WIDE ? 5 : 4;
                rows = 3;
            }
            case SEGA -> {
                face.add(new Cell(by.get(Action.L), 0, 0));
                face.add(new Cell(by.get(Action.X), 1, 0));
                face.add(new Cell(by.get(Action.R), 2, 0));
                face.add(new Cell(by.get(Action.Y), 0, 1));
                face.add(new Cell(by.get(Action.B), 1, 1));
                face.add(new Cell(by.get(Action.A), 2, 1));
                cols = 3;
                rows = 2;
            }
            case N64 -> {
                face.add(new Cell(by.get(Action.Y), 0, 0));
                face.add(new Cell(by.get(Action.B), 0, 1));
                face.add(new Cell(by.get(Action.L), 1, 0));
                face.add(new Cell(by.get(Action.R), 1, 1));
                face.add(new Cell(by.get(Action.L2), 0, 2));
                cols = 2;
                rows = 3;
            }
            case GBA -> {
                face.add(new Cell(by.get(Action.L), 0, 0));
                face.add(new Cell(by.get(Action.R), 1, 0));
                face.add(new Cell(by.get(Action.B), 0, 1));
                face.add(new Cell(by.get(Action.A), 1, 1));
                rows = 2;
            }
            case ARCADE -> {
                face.add(new Cell(by.get(Action.B), 0, 0));
                face.add(new Cell(by.get(Action.Y), 1, 0));
                face.add(new Cell(by.get(Action.A), 2, 0));
                face.add(new Cell(by.get(Action.X), 0, 1));
                face.add(new Cell(by.get(Action.L), 1, 1));
                face.add(new Cell(by.get(Action.R), 2, 1));
                cols = 3;
                rows = 2;
            }
            case ROW -> {
                int col = 0;
                for (var button : profile.buttons())
                    if (button.action() != Action.START && button.action() != Action.SELECT)
                        face.add(new Cell(by.get(button.action()), col++, 0));
                cols = Math.max(1, col);
            }
        }
        face.removeIf(cell -> cell.button() == null);
        return new PadLayout(mid, face, cols, rows);
    }

    private static void diamond(List<Cell> face, Map<Action, HudButton> by) {
        face.add(new Cell(by.get(Action.X), 1, 0));
        face.add(new Cell(by.get(Action.Y), 0, 1));
        face.add(new Cell(by.get(Action.A), 2, 1));
        face.add(new Cell(by.get(Action.B), 1, 2));
    }

    static int reservedHeight(ConsoleTheme theme) {
        if (theme.profile().sticks() > 0) return 76;
        return LAYOUTS.computeIfAbsent(theme.id(), ignored -> layout(theme)).rows() > 2 ? 64 : 52;
    }

    static void render(
            GuiGraphics context,
            Font font,
            Minecraft client,
            ConsoleTheme theme,
            int width,
            int height,
            UUID instance) {
        if (!ClientPreferences.get().showPlayerHud) return;
        State state = SESSIONS.get(instance);
        if (state == null || client.getConnection() == null) return;
        if (client.player != null) {
            int mine = slotOf(instance, client.player.getUUID());
            if (mine >= 0) update(state.slots[mine], InputControls.sample(client));
        }
        List<Integer> order = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) if (state.slots[i].player != null) order.add(i);
        if (order.isEmpty()) return;

        PadLayout layout = LAYOUTS.computeIfAbsent(theme.id(), ignored -> layout(theme));
        Map<HudButton, Integer> widths = new HashMap<>();
        for (var cell : layout.face())
            widths.computeIfAbsent(
                    cell.button(),
                    b -> Math.max(11, Math.round(font.width(hudLabel(b.label())) * .72f) + 5));
        for (var b : layout.middle())
            widths.computeIfAbsent(
                    b, button -> Math.max(9, Math.round(font.width(button.label()) * .72f) + 5));
        int midW = layout.middle().stream().mapToInt(widths::get).max().orElse(0);
        int cellW =
                layout.face().stream().mapToInt(cell -> widths.get(cell.button())).max().orElse(0);
        int midH = layout.middle().isEmpty() ? 0 : layout.middle().size() * (CHIP_H + 2) - 2;
        int faceH = layout.rows() * (CHIP_H + 6) - 6;
        int faceW = layout.face().isEmpty() ? 0 : layout.cols() * cellW + (layout.cols() - 1) * 2;
        int directionW = theme.profile().sticks() > 0 ? 39 : DPAD;
        int directionH = theme.profile().sticks() > 0 ? 49 : DPAD;
        int rowH = Math.max(directionH, Math.max(midH, faceH));
        int rowW = directionW + (midW > 0 ? 6 + midW : 0) + (faceW > 0 ? 6 + faceW : 0);

        int bodyH = 3 + rowH + 3, cardH = HEADER_H + bodyH, y = height - cardH - 4;
        var handler = client.getConnection();
        String[] names = new String[4];
        ResourceLocation[] skins = new ResourceLocation[4];
        int[] cardW = new int[4];
        for (int i : order) {
            PlayerInfo entry = handler.getPlayerInfo(state.slots[i].player);
            String name =
                    entry != null
                            ? com.sammwy.retrocraft.compat.Profiles.name(entry.getProfile())
                            : "?";
            names[i] = font.plainSubstrByWidth(name, 62);
            skins[i] =
                    entry != null
                            ? com.sammwy.retrocraft.compat.SkinTextures.texture(entry)
                            : DefaultPlayerSkin.getDefaultTexture();
            int tagW = Math.round(font.width("P" + (i + 1)) * .72f);
            int headerW = PAD + 8 + 3 + Math.round(font.width(names[i]) * .72f) + 5 + tagW + PAD;
            cardW[i] = Math.max(headerW, rowW + 2 * PAD);
        }
        // Two corner clusters: P1/P2 on the left, P3/P4 on the right; the centre stays clear.
        int leftW = -GAP, rightW = -GAP;
        for (int i : order) {
            if (i < 2) leftW += cardW[i] + GAP;
            else rightW += cardW[i] + GAP;
        }
        if (leftW < 0) leftW = 0;
        if (rightW < 0) rightW = 0;
        int span = leftW + rightW + (leftW > 0 && rightW > 0 ? MIDDLE_GAP : 0);
        int layoutWidth = Math.max(width, span + 8);
        int x = 4, rx = layoutWidth - 4 - rightW;
        long now = System.nanoTime();

        var matrices = UiPose.of(context);
        matrices.pushPose();
        float hudScale = Math.min(1f, width / (float) layoutWidth);
        if (hudScale < 1f) {
            matrices.translate(width / 2f, height, 0);
            matrices.scale(hudScale, hudScale, 1);
            matrices.translate(-layoutWidth / 2f, -height, 0);
        }
        for (int i : order) {
            Slot slot = state.slots[i];
            int color = COLORS[i], w = cardW[i];
            int px;
            if (i < 2) {
                px = x;
                x += w + GAP;
            } else {
                px = rx;
                rx += w + GAP;
            }
            float join = Math.min(1f, (now - slot.joinedAt) / 1e6f / JOIN_MS);
            matrices.pushPose();
            if (client.player != null
                    && client.player.getUUID().equals(slot.player)
                    && now - state.rumbleAt < 300_000_000L
                    && state.rumble > 0)
                matrices.translate(Math.sin(now / 15_000_000d) * 2 * state.rumble, 0, 0);
            if (join < 1f) {
                float s = .55f + .45f * easeOut(join);
                matrices.translate(px + w / 2f, y + cardH / 2f, 0);
                matrices.scale(s, s, 1);
                matrices.translate(-(px + w / 2f), -(y + cardH / 2f), 0);
            }
            // Card shell: dark plate, translucent body tinted by the slot colour, solid top-rounded
            // header.
            rounded(context, px - 1, y - 1, w + 2, cardH + 2, 0x7A000000, 4);
            rounded(context, px, y, w, cardH, over(0xB0181B22, color, .22f), 4);
            roundedTop(context, px, y, w, HEADER_H, withAlpha(color, 0xFF), 4);
            // Header: head (with the host crown), name and a "P#" tag on the right.
            int hx = px + PAD, hy = y + 1;
            if (slot.host) crown(context, hx + 4, y - 3);
            ClientGraphics.blit(context, skins[i], hx, hy, 8, 8, 8f, 8f, 8, 8, 64, 64);
            ClientGraphics.blit(context, skins[i], hx, hy, 8, 8, 40f, 8f, 8, 8, 64, 64);
            scaledText(context, font, names[i], hx + 11, y + 2f, 0xFFFFFFFF, true);
            String tag = "P" + (i + 1);
            scaledText(
                    context,
                    font,
                    tag,
                    px + w - PAD - Math.round(font.width(tag) * .72f),
                    y + 2f,
                    withAlpha(0xFFFFFFFF, 0xE6),
                    true);
            // Pad row: reusable cross, stacked middle column, face grid.
            // Key hints show on join for ~5s, then only after 5s without presses; any press hides
            // them.
            boolean hints =
                    client.player != null
                            && slot.player.equals(client.player.getUUID())
                            && (slot.lastPress == 0
                                    ? now - slot.joinedAt < 5_000_000_000L
                                    : now - slot.lastPress > 5_000_000_000L);
            int rowX = px + (w - rowW) / 2, rowY = y + HEADER_H + 3;
            dpad(
                    context,
                    font,
                    rowX + (directionW - DPAD) / 2,
                    rowY + (rowH - directionH) / 2,
                    slot,
                    color,
                    now,
                    theme.diagonals(),
                    hints,
                    theme.id());
            if (theme.profile().sticks() > 0) {
                int sy = rowY + (rowH - directionH) / 2 + 29;
                stick(
                        context,
                        font,
                        rowX + (theme.profile().sticks() == 1 ? 12 : 0),
                        sy,
                        slot,
                        false,
                        color,
                        hints,
                        theme.id());
                if (theme.profile().sticks() > 1)
                    stick(context, font, rowX + 24, sy, slot, true, color, hints, theme.id());
            }
            int cx = rowX + directionW + 6;
            if (midW > 0) {
                int my = rowY + (rowH - midH) / 2;
                for (int m = 0; m < layout.middle().size(); m++) {
                    var button = layout.middle().get(m);
                    // Stacked chips sit together: the top one's key hint floats above, the bottom
                    // one's below.
                    drawChip(
                            context,
                            font,
                            cx + (midW - widths.get(button)) / 2,
                            my,
                            widths.get(button),
                            button,
                            slot,
                            color,
                            now,
                            layout.middle().size() > 1 && m == 0,
                            hints,
                            theme.id());
                    my += CHIP_H + 2;
                }
                cx += midW + 6;
            }
            if (faceW > 0) {
                int fy = rowY + (rowH - faceH) / 2;
                for (var cell : layout.face())
                    drawChip(
                            context,
                            font,
                            cx + cell.col() * (cellW + 2),
                            fy + cell.row() * (CHIP_H + 6),
                            cellW,
                            cell.button(),
                            slot,
                            color,
                            now,
                            false,
                            hints,
                            theme.id());
            }
            matrices.popPose();
        }
        matrices.popPose();
    }

    /**
     * RetroPad cross: a 3x3 grid whose arms light per direction. With {@code diagonals} the corner
     * cells exist too and light when both adjacent arms are held (8-way sticks).
     */
    private static void dpad(
            GuiGraphics context,
            Font font,
            int x,
            int y,
            Slot slot,
            int color,
            long now,
            boolean diagonals,
            boolean hints,
            String console) {
        int[][] arms = {{1, 0, UP}, {1, 2, DOWN}, {0, 1, LEFT}, {2, 1, RIGHT}};
        int[][] corners = {
            {0, 0, UP | LEFT}, {2, 0, UP | RIGHT}, {0, 2, DOWN | LEFT}, {2, 2, DOWN | RIGHT}
        };
        if (diagonals)
            for (int[] corner : corners)
                dpadCell(context, x, y, corner[0], corner[1], slot, corner[2], color, now, true);
        for (int[] arm : arms)
            dpadCell(context, x, y, arm[0], arm[1], slot, arm[2], color, now, false);
        // Pivot with a small centre nub.
        context.fill(x + CELL, y + CELL, x + 2 * CELL, y + 2 * CELL, 0xD0333A48);
        context.fill(x + CELL + 1, y + CELL + 1, x + 2 * CELL - 1, y + 2 * CELL - 1, 0x9014181E);
        if (hints)
            keyHint(
                    context,
                    font,
                    directionKeys(console, false, false),
                    x + DPAD / 2f,
                    y + DPAD + 1,
                    (slot.mask & 0xf0) != 0);
    }

    private static void dpadCell(
            GuiGraphics context,
            int x,
            int y,
            int col,
            int row,
            Slot slot,
            int mask,
            int color,
            long now,
            boolean both) {
        boolean held = (slot.mask & mask) == mask;
        int cx = x + col * CELL, cy = y + row * CELL;
        int bg = 0xD0333A48;
        if (held) {
            long pressed =
                    both
                            ? Math.max(
                                    slot.pressAt[Integer.numberOfTrailingZeros(mask & -mask)],
                                    slot.pressAt[31 - Integer.numberOfLeadingZeros(mask)])
                            : slot.pressAt[Integer.numberOfTrailingZeros(mask)];
            float since = (now - pressed) / 1e6f;
            bg = lerp(0xF2FFFFFF, withAlpha(color, 0xE6), Math.min(1f, since / 90f));
            if (since < POP_MS) {
                float pop = 1 + .3f * (1 - since / POP_MS);
                var matrices = UiPose.of(context);
                matrices.pushPose();
                matrices.translate(cx + CELL / 2f, cy + CELL / 2f, 0);
                matrices.scale(pop, pop, 1);
                matrices.translate(-(cx + CELL / 2f), -(cy + CELL / 2f), 0);
                context.fill(cx, cy, cx + CELL, cy + CELL, bg);
                matrices.popPose();
                return;
            }
        }
        context.fill(cx, cy, cx + CELL, cy + CELL, bg);
    }

    /**
     * Chip plus its key cap floating just off the bottom edge (or off the top when {@code
     * hintAbove}).
     */
    private static void drawChip(
            GuiGraphics context,
            Font font,
            int x,
            int y,
            int w,
            HudButton button,
            Slot slot,
            int color,
            long now,
            boolean hintAbove,
            boolean hints,
            String console) {
        boolean held = (slot.mask & button.mask()) != 0;
        float since = (now - slot.pressAt[Integer.numberOfTrailingZeros(button.mask())]) / 1e6f;
        float pop = held && since < POP_MS ? 1 + .3f * (1 - since / POP_MS) : 1;
        int bg =
                held
                        ? lerp(0xF2FFFFFF, withAlpha(color, 0xE6), Math.min(1f, since / 90f))
                        : 0xD0333A48;
        int fg = held ? lerp(0xFF14161C, 0xFFFFFFFF, Math.min(1f, since / 90f)) : 0xBAFFFFFF;
        var matrices = UiPose.of(context);
        matrices.pushPose();
        if (pop > 1.001f) {
            matrices.translate(x + w / 2f, y + CHIP_H / 2f, 0);
            matrices.scale(pop, pop, 1);
            matrices.translate(-(x + w / 2f), -(y + CHIP_H / 2f), 0);
        }
        rounded(context, x, y, w, CHIP_H, bg, 2);
        context.fill(x + 1, y, x + w - 1, y + 1, withAlpha(fg, held ? 0x30 : 0x14));
        if (!PadGlyphs.draw(context, button.label(), x + (w - 7) / 2, y + 1, held)) {
            String label = hudLabel(button.label());
            scaledText(
                    context,
                    font,
                    label,
                    x + (w - Math.round(font.width(label) * .72f)) / 2f,
                    y + 2f,
                    fg,
                    false);
        }
        matrices.popPose();
        if (hints)
            keyHint(
                    context,
                    font,
                    InputControls.keyName(console, button.action()),
                    x + w / 2f,
                    hintAbove ? y - 5 : y + CHIP_H + 1,
                    held);
    }

    private static String hudLabel(String label) {
        return switch (label) {
            case "cross", "circle", "square", "triangle" -> "  ";
            case "C left" -> "C←";
            case "C right" -> "C→";
            case "C up" -> "C↑";
            case "C down" -> "C↓";
            default -> label;
        };
    }

    private static String directionKeys(String console, boolean analog, boolean right) {
        Action[] actions =
                !analog
                        ? new Action[] {Action.UP, Action.LEFT, Action.DOWN, Action.RIGHT}
                        : right
                                ? new Action[] {
                                    Action.RIGHT_UP,
                                    Action.RIGHT_LEFT,
                                    Action.RIGHT_DOWN,
                                    Action.RIGHT_RIGHT
                                }
                                : new Action[] {
                                    Action.LEFT_UP,
                                    Action.LEFT_LEFT,
                                    Action.LEFT_DOWN,
                                    Action.LEFT_RIGHT
                                };
        StringJoiner names = new StringJoiner("");
        for (Action action : actions) names.add(InputControls.keyName(console, action));
        return names.toString();
    }

    private static void stick(
            GuiGraphics context,
            Font font,
            int x,
            int y,
            Slot slot,
            boolean right,
            int color,
            boolean hints,
            String console) {
        short ax = right ? slot.input.rightX() : slot.input.leftX();
        short ay = right ? slot.input.rightY() : slot.input.leftY();
        boolean pressed = (slot.mask & (right ? 1 << 15 : 1 << 14)) != 0;
        boolean moving = ax != 0 || ay != 0;
        rounded(context, x, y, 15, 15, pressed ? withAlpha(color, 0xE6) : 0xD0333A48, 5);
        rounded(context, x + 2, y + 2, 11, 11, 0xFF161A22, 3);
        int dx = Math.round(ax / 32767f * 3), dy = Math.round(ay / 32767f * 3);
        rounded(context, x + 5 + dx, y + 5 + dy, 5, 5, moving || pressed ? color : 0xFF818B9D, 2);
        scaledText(
                context,
                font,
                right
                        ? ControllerProfile.of(console).rightStick()
                        : ControllerProfile.of(console).leftStick(),
                x + 5,
                y - 6,
                0xBFCCD2DD,
                false);
        if (hints)
            keyHint(
                    context,
                    font,
                    font.plainSubstrByWidth(directionKeys(console, true, right), 38),
                    x + 7.5f,
                    y + 16,
                    moving);
    }

    /** Text drawn at .72 scale so the shrunken card stays proportional. */
    private static void scaledText(
            GuiGraphics context,
            Font font,
            String text,
            float x,
            float y,
            int color,
            boolean shadow) {
        var matrices = UiPose.of(context);
        matrices.pushPose();
        matrices.translate(x, y, 0);
        matrices.scale(.72f, .72f, 1);
        context.drawString(font, text, 0, 0, color, shadow);
        matrices.popPose();
    }

    /**
     * Bare tiny text hung just off an element's edge at {@code top}: floats, never eats layout
     * space.
     */
    private static void keyHint(
            GuiGraphics context, Font font, String key, float cx, float top, boolean held) {
        var matrices = UiPose.of(context);
        matrices.pushPose();
        matrices.translate(cx, top, 0);
        matrices.scale(.55f, .55f, 1);
        context.drawString(
                font, key, -font.width(key) / 2, 0, held ? 0xFFFFFFFF : 0x8AFFFFFF, true);
        matrices.popPose();
    }

    /** Tiny 7x4 crown floated above the head of whoever emulates the session. */
    private static void crown(GuiGraphics context, int cx, int y) {
        int gold = 0xFFFFD24A, shade = 0xFFB8860B;
        for (int i = -3; i <= 3; i += 3) context.fill(cx + i, y, cx + i + 1, y + 1, gold);
        context.fill(cx - 3, y + 1, cx + 4, y + 3, gold);
        context.fill(cx - 3, y + 3, cx + 4, y + 4, shade);
    }

    private static void rounded(GuiGraphics context, int x, int y, int w, int h, int color, int r) {
        context.fill(x + r, y, x + w - r, y + h, color);
        context.fill(x, y + r, x + r, y + h - r, color);
        context.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int k = 1; k < r; k++) {
            context.fill(x + r - k, y + k, x + r, y + k + 1, color);
            context.fill(x + w - r, y + k, x + w - r + k, y + k + 1, color);
            context.fill(x + r - k, y + h - k - 1, x + r, y + h - k, color);
            context.fill(x + w - r, y + h - k - 1, x + w - r + k, y + h - k, color);
        }
    }

    /** Rounded rect with only the two top corners rounded. */
    private static void roundedTop(
            GuiGraphics context, int x, int y, int w, int h, int color, int r) {
        context.fill(x + r, y, x + w - r, y + h, color);
        context.fill(x, y + r, x + w, y + h, color);
        for (int k = 1; k < r; k++) {
            context.fill(x + r - k, y + k, x + r, y + k + 1, color);
            context.fill(x + w - r, y + k, x + w - r + k, y + k + 1, color);
        }
    }

    private static float easeOut(float t) {
        float u = 1 - t;
        return 1 - u * u * u;
    }

    private static int withAlpha(int argb, int alpha) {
        return alpha << 24 | (argb & 0xFFFFFF);
    }

    private static int shade(int argb, float factor) {
        int r = Math.min(255, Math.round(((argb >> 16) & 255) * factor)),
                g = Math.min(255, Math.round(((argb >> 8) & 255) * factor)),
                b = Math.min(255, Math.round((argb & 255) * factor));
        return (argb & 0xff000000) | r << 16 | g << 8 | b;
    }

    /** Tints {@code base} towards {@code rgb} by {@code alpha}, keeping the base alpha. */
    private static int over(int base, int rgb, float alpha) {
        int r = Math.round((base >> 16 & 255) * (1 - alpha) + (rgb >> 16 & 255) * alpha);
        int g = Math.round((base >> 8 & 255) * (1 - alpha) + (rgb >> 8 & 255) * alpha);
        int b = Math.round((base & 255) * (1 - alpha) + (rgb & 255) * alpha);
        return (base & 0xff000000) | r << 16 | g << 8 | b;
    }

    private static int lerp(int a, int b, float t) {
        int result = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = a >>> shift & 255, cb = b >>> shift & 255;
            result |= (ca + Math.round((cb - ca) * t)) << shift;
        }
        return result;
    }

    private PlayerHud() {}
}
