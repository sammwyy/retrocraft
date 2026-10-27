package com.sammwy.retrocraft.client.ui;

import static com.sammwy.retrocraft.client.ui.SettingsWidgets.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import org.lwjgl.glfw.GLFW;

import com.sammwy.retrocraft.client.ClientPreferences;
import com.sammwy.retrocraft.client.RetrocraftClient;
import com.sammwy.retrocraft.client.net.SettingsBackend;
import com.sammwy.retrocraft.client.net.SettingsClient;
import com.sammwy.retrocraft.client.ui.SettingsWidgets.Cycle;
import com.sammwy.retrocraft.client.ui.SettingsWidgets.Flat;
import com.sammwy.retrocraft.client.ui.SettingsWidgets.Glyph;
import com.sammwy.retrocraft.client.ui.SettingsWidgets.Toggle;
import com.sammwy.retrocraft.compat.ClientGraphics;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.platform.Platform;
import com.sammwy.retrocraft.server.config.ConfigKey;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.net.SettingsPackets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Personal and operator settings: a sidebar with one category per config group and a ROM library
 * browser. Values are edited into a draft and committed with Apply.
 */
public final class SettingsScreen extends AnimatedScreen {
    private static final int SIDEBAR = 126,
            ROW_H = 32,
            SECTION_H = 18,
            CONTENT_TOP = 34,
            FOOTER = 26;
    private static final ResourceLocation LOGO_TEX =
            Ids.of("retrocraft", "textures/gui/logotype.png");
    private static final int LOGO_W = 1852, LOGO_H = 391;
    private static final String[] GLYPH_CHIP = {
        " X X X ", "XXXXXXX", "X XXX X", "X XXX X", "X XXX X", "XXXXXXX", " X X X "
    };
    private static final String[] GLYPH_WAVES = {
        "  X    ", " XX  X ", "X X X X", "X X X X", "X X X X", " XX  X ", "  X    "
    };
    private static final String[] GLYPH_CLOCK = {
        " XXXXX ", "X  X  X", "X  X  X", "X  XX X", "X     X", "X     X", " XXXXX "
    };
    private static final String[] GLYPH_CORE = {
        " X X X ", "X     X", "X XXX X", "X XXX X", "X XXX X", "X     X", " X X X "
    };
    private static final String[] GLYPH_FOLDER = {
        "XXX     ", "X  XXXXX", "X      X", "X      X", "X      X", "X      X", "XXXXXXX "
    };
    private static final String[] GLYPH_MINUS = {
        "       ", "       ", "       ", " XXXXX ", "       ", "       ", "       "
    };
    private static final String[] GLYPH_PLUS = {
        "       ", "   X   ", "   X   ", " XXXXX ", "   X   ", "   X   ", "       "
    };

    private enum State {
        CONNECTING,
        DENIED,
        READY
    }

    private final Screen parent;
    private State state;
    private String denyReason = "timeout";
    private SettingsBackend backend;
    private List<SettingsPackets.GuiOpen.Entry> schema = List.of();
    private Map<String, SettingsPackets.GuiOpen.Entry> byKey = Map.of();
    private final Map<String, String> draft = new LinkedHashMap<>();
    private static final int CLIENT = -1, CONTROLS = -2;
    private int category = CLIENT;
    private final Map<String, String> clientDraft = new LinkedHashMap<>();
    private float scroll;
    private boolean syncing;
    private final List<Row> rows = new ArrayList<>();
    private Component status;
    private int statusColor;
    private long statusUntil;
    private final RomBrowser browser = new RomBrowser(new BrowserHost());
    private final ControlsPane controlsPane = new ControlsPane();
    private final CorePane corePane = new CorePane(new CoreHost());
    private long requestedAt;
    private boolean scrollDrag;
    private int px, py, pw, ph;
    private int contentX, contentY, contentW, contentH;
    private SettingsWidgets.Flat apply;

    private SettingsScreen(Screen parent, SettingsBackend backend, State state) {
        super(Component.translatable("settings.retrocraft.title"));
        this.parent = parent;
        this.backend = backend;
        this.state = state;
        schema(backend != null ? backend.schema() : List.of());
    }

    public static SettingsScreen remote(Screen parent, SettingsPackets.GuiOpen grant) {
        if (!grant.granted()) return denied(parent, grant.reason());
        return new SettingsScreen(parent, new SettingsBackend.Remote(grant), State.READY);
    }

    /** ModMenu's config button: local files when hosting/offline, a server request otherwise. */
    public static SettingsScreen modMenu(Screen parent) {
        var client = Minecraft.getInstance();
        if (client.player == null) return local(parent);
        if (!com.sammwy.retrocraft.compat.Access.operator(client.player))
            return denied(parent, "operator");
        if (!SettingsClient.supported()) return denied(parent, "not_installed");
        return new SettingsScreen(parent, null, State.CONNECTING);
    }

    static SettingsScreen bios(String coreId) {
        var screen = modMenu(null);
        screen.category = ConfigKey.Category.CORES.ordinal();
        screen.corePane.focus(coreId);
        return screen;
    }

    private static SettingsScreen local(Screen parent) {
        try {
            return new SettingsScreen(parent, new SettingsBackend.Local(), State.READY);
        } catch (Exception ex) {
            return denied(parent, "local_error");
        }
    }

    private static SettingsScreen denied(Screen parent, String reason) {
        var screen = new SettingsScreen(parent, null, State.DENIED);
        screen.denyReason = reason;
        return screen;
    }

    private void schema(List<SettingsPackets.GuiOpen.Entry> entries) {
        var combined = new ArrayList<>(ClientPreferences.schema());
        combined.addAll(entries);
        schema = List.copyOf(combined);
        var map = new HashMap<String, SettingsPackets.GuiOpen.Entry>();
        for (var entry : schema) map.put(entry.key(), entry);
        byKey = map;
    }

    /** DoOpenSettingsGUI arrived while this screen waited for it. */
    public void granted(SettingsPackets.GuiOpen packet) {
        if (!packet.granted()) {
            state = State.DENIED;
            denyReason = packet.reason();
            rebuildWidgets();
            return;
        }
        backend = new SettingsBackend.Remote(packet);
        schema(packet.entries());
        state = State.READY;
        rebuildWidgets();
    }

    public void scanFinished() {
        browser.reload();
    }

    public void configSaved(SettingsPackets.ConfigSaved packet) {
        if (backend instanceof SettingsBackend.Remote remote) remote.saved(packet);
    }

    @Override
    protected void init() {
        pw = Math.min(620, width - 16);
        ph = Math.min(340, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        contentX = px + SIDEBAR + 12;
        contentY = py + CONTENT_TOP;
        contentW = px + pw - 12 - contentX;
        contentH = py + ph - FOOTER - 6 - contentY;
        int footerY = py + ph - FOOTER + 4;
        int buttonWidth = Math.min(60, Math.max(32, (contentW - 8) / 3));
        int buttonRight = px + pw - 12;
        apply =
                addRenderableWidget(
                        new Flat(
                                        buttonRight - buttonWidth * 3 - 8,
                                        footerY,
                                        buttonWidth,
                                        16,
                                        Component.translatable("settings.retrocraft.apply"),
                                        this::applyDraft)
                                .accent(true));
        addRenderableWidget(
                new Flat(
                        buttonRight - buttonWidth * 2 - 4,
                        footerY,
                        buttonWidth,
                        16,
                        Component.translatable("settings.retrocraft.reset"),
                        this::resetDraft));
        addRenderableWidget(
                new Flat(
                        buttonRight - buttonWidth,
                        footerY,
                        buttonWidth,
                        16,
                        Component.translatable("settings.retrocraft.done"),
                        this::onClose));
        SettingsClient.screen(this);
        if (state == State.CONNECTING) {
            requestedAt = System.nanoTime();
            if (SettingsClient.supported()) SettingsClient.request();
        }
        {
            buildRows();
            browser.init(contentX, contentY + ROW_H + 6, contentW, contentH - ROW_H - 6);
            browser.setActive(
                    state == State.READY && category == ConfigKey.Category.LIBRARY.ordinal());
            corePane.init(contentX, contentY, contentW, contentH);
            corePane.setActive(
                    state == State.READY && category == ConfigKey.Category.CORES.ordinal());
        }
        controlsPane.init(contentX, contentY, contentW, contentH);
        controlsPane.setActive(category == CONTROLS);
        refreshFooter();
    }

    private void buildRows() {
        rows.clear();
        for (var entry : schema) if (entry.kind() != SettingsPackets.CORE) rows.add(row(entry));
        scroll = 0;
    }

    private Row row(SettingsPackets.GuiOpen.Entry entry) {
        var row = new Row(entry);
        switch (entry.kind()) {
            case SettingsPackets.INT -> {
                var field = SettingsWidgets.field(font, 0, 0, 44, value(entry));
                field.setMaxLength(10);
                field.setResponder(
                        text -> {
                            if (!syncing) {
                                (entry.category() == CLIENT ? clientDraft : draft)
                                        .put(entry.key(), text);
                                refreshFooter();
                            }
                        });
                row.parts.add(addWidget(field));
                row.intField = field;
                row.parts.add(
                        addWidget(
                                new Glyph(
                                        0,
                                        0,
                                        GLYPH_MINUS,
                                        () -> step(row, -1),
                                        Component.translatable("settings.retrocraft.decrease"))));
                row.parts.add(
                        addWidget(
                                new Glyph(
                                        0,
                                        0,
                                        GLYPH_PLUS,
                                        () -> step(row, 1),
                                        Component.translatable("settings.retrocraft.increase"))));
            }
            case SettingsPackets.BOOL -> {
                var toggle =
                        new Toggle(
                                0,
                                0,
                                "true".equals(value(entry)),
                                on -> {
                                    if (!syncing) {
                                        (entry.category() == CLIENT ? clientDraft : draft)
                                                .put(entry.key(), on ? "true" : "false");
                                        refreshFooter();
                                    }
                                });
                row.toggle = toggle;
                row.parts.add(addWidget(toggle));
            }
            case SettingsPackets.ENUM, SettingsPackets.CORE -> {
                var cycle =
                        new Cycle(
                                0,
                                0,
                                150,
                                entry.options(),
                                value(entry),
                                v -> {
                                    if (!syncing) {
                                        (entry.category() == CLIENT ? clientDraft : draft)
                                                .put(entry.key(), v);
                                        refreshFooter();
                                    }
                                });
                row.cycle = cycle;
                row.parts.add(addWidget(cycle));
            }
            default -> {}
        }
        return row;
    }

    private void step(Row row, int dir) {
        var entry = row.entry;
        int v = entry.min();
        try {
            v = Integer.parseInt(value(entry));
        } catch (NumberFormatException ignored) {
        }
        v = Math.max(entry.min(), Math.min(entry.max(), v + dir));
        row.intField.setValue(Integer.toString(v));
    }

    private Map<String, String> activeDraft() {
        return category == CLIENT ? clientDraft : draft;
    }

    private boolean ready() {
        return category < 0 || state == State.READY;
    }

    private String value(SettingsPackets.GuiOpen.Entry entry) {
        var current = byKey.getOrDefault(entry.key(), entry);
        return (entry.category() == CLIENT ? clientDraft : draft)
                .getOrDefault(entry.key(), current.value());
    }

    private SettingsPackets.GuiOpen.Entry entry(String key) {
        return byKey.get(key);
    }

    private void applyDraft() {
        if (category == CONTROLS) {
            try {
                controlsPane.apply();
                status(Component.translatable("settings.retrocraft.saved"), GOOD);
            } catch (IOException ex) {
                status(Component.literal(ex.getMessage()), BAD);
            }
            refreshFooter();
            return;
        }
        if (category == CLIENT) {
            try {
                ClientPreferences.apply(clientDraft);
                clientDraft.clear();
                schema(backend == null ? List.of() : backend.schema());
                syncRows();
                refreshFooter();
                status(Component.translatable("settings.retrocraft.saved"), GOOD);
            } catch (IOException | IllegalArgumentException ex) {
                status(Component.literal(ex.getMessage()), BAD);
            }
            return;
        }
        var changes =
                draft.entrySet().stream()
                        .map(e -> new SettingsPackets.ConfigApply.Change(e.getKey(), e.getValue()))
                        .toList();
        if (changes.isEmpty()) return;
        backend.apply(
                changes,
                (ok, message, entries) -> {
                    schema(entries);
                    if (ok) {
                        draft.clear();
                        status(Component.translatable("settings.retrocraft.saved"), GOOD);
                    } else status(Component.literal(message == null ? "?" : message), BAD);
                    syncRows();
                    refreshFooter();
                });
        refreshFooter();
    }

    private void resetDraft() {
        if (category == CONTROLS) {
            controlsPane.resetDraft();
            refreshFooter();
            return;
        }
        if (activeDraft().isEmpty()) return;
        activeDraft().clear();
        syncRows();
        refreshFooter();
    }

    private void syncRows() {
        syncing = true;
        try {
            for (var row : rows) {
                if (row.entry == null) continue; // section header
                var entry = entry(row.entry.key());
                if (entry == null) continue;
                if (row.intField != null) row.intField.setValue(value(entry));
                if (row.toggle != null) row.toggle.value("true".equals(value(entry)));
                if (row.cycle != null) row.cycle.value(value(entry));
            }
        } finally {
            syncing = false;
        }
    }

    private int pendingChanges() {
        return category == CONTROLS ? controlsPane.dirtyCount() : activeDraft().size();
    }

    private void refreshFooter() {
        apply.active =
                ready()
                        && pendingChanges() > 0
                        && (category < 0
                                || !(backend instanceof SettingsBackend.Remote remote)
                                || !remote.applying());
    }

    void status(Component text, int color) {
        status = text;
        statusColor = color;
        statusUntil = System.currentTimeMillis() + 4500;
    }

    private void selectCategory(int index) {
        if (index == category) return;
        category = index;
        controlsPane.setActive(index == CONTROLS);
        scroll = 0;
        setFocused(null);
        for (var row : rows) {
            row.hide();
            if (row.intField != null) row.intField.setFocused(false);
        }
        refreshFooter();
        browser.setActive(state == State.READY && index == ConfigKey.Category.LIBRARY.ordinal());
        corePane.setActive(state == State.READY && index == ConfigKey.Category.CORES.ordinal());
        SettingsWidgets.click(minecraft);
    }

    private int categoryAt(double mx, double my) {
        if (mx >= px + 6 && mx < px + SIDEBAR - 6 && my >= py + 68 && my < py + 86) return CLIENT;
        if (mx >= px + 6 && mx < px + SIDEBAR - 6 && my >= py + 88 && my < py + 106)
            return CONTROLS;
        int y = py + 126;
        for (int i = 0; i < ConfigKey.Category.values().length; i++, y += 18)
            if (mx >= px + 6 && mx < px + SIDEBAR - 6 && my >= y && my < y + 18) return i;
        return -3;
    }

    @Override
    public void tick() {
        if (backend instanceof SettingsBackend.Remote remote) remote.tick();
        if (state == State.CONNECTING && System.nanoTime() - requestedAt > 5_000_000_000L) {
            state = State.DENIED;
            denyReason = SettingsClient.supported() ? "timeout" : "not_installed";
            rebuildWidgets();
        }
        if (status != null && System.currentTimeMillis() > statusUntil) status = null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!acceptingInput()) return true;
        if (category == CONTROLS && controlsPane.recording()) {
            controlsPane.mouseClicked(mx, my, button);
            refreshFooter();
            return true;
        }
        {
            int idx = categoryAt(mx, my);
            if (idx >= CONTROLS) {
                selectCategory(idx);
                return true;
            }
            if (category == CONTROLS && controlsPane.mouseClicked(mx, my, button)) {
                refreshFooter();
                return true;
            }
            if (!ready()) return super.mouseClicked(mx, my, button);
            if (scrollTrackHit(mx, my)) {
                scrollDrag = true;
                dragScroll(my);
                return true;
            }
            if (category == ConfigKey.Category.LIBRARY.ordinal()
                    && browser.mouseClicked(mx, my, button)) return true;
            if (category == ConfigKey.Category.CORES.ordinal()
                    && corePane.mouseClicked(mx, my, button)) return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double deltaX, double deltaY) {
        if (!acceptingInput()) return true;
        if (category == CONTROLS && controlsPane.mouseDragged(my)) return true;
        if (scrollDrag) {
            dragScroll(my);
            return true;
        }
        if (ready()
                && category == ConfigKey.Category.LIBRARY.ordinal()
                && browser.mouseDragged(mx, my)) return true;
        if (ready()
                && category == ConfigKey.Category.CORES.ordinal()
                && corePane.mouseDragged(mx, my)) return true;
        return super.mouseDragged(mx, my, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        scrollDrag = false;
        browser.mouseReleased();
        corePane.mouseReleased();
        controlsPane.mouseReleased();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!acceptingInput()) return true;
        if (category == CONTROLS && controlsPane.mouseScrolled(mx, my, vertical)) return true;
        if (ready()
                && category == ConfigKey.Category.LIBRARY.ordinal()
                && browser.mouseScrolled(mx, my, horizontal, vertical)) return true;
        if (ready()
                && category == ConfigKey.Category.CORES.ordinal()
                && corePane.mouseScrolled(mx, my, horizontal, vertical)) return true;
        if (ready()
                && mx >= contentX
                && mx < contentX + contentW
                && my >= contentY
                && my < contentY + contentH) {
            scroll = Math.max(0, Math.min(scroll - (float) vertical * 14, maxScroll()));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private float maxScroll() {
        int total = 0;
        for (var row : rows) if (rowCategory(row) == category) total += row.height();
        return Math.max(0, total - contentH);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (closing()) return true;
        if (category == CONTROLS && acceptingInput() && controlsPane.keyPressed(key, scanCode)) {
            refreshFooter();
            return true;
        }
        if (com.sammwy.retrocraft.compat.ClientInput.matches(
                RetrocraftClient.SETTINGS_KEY, key, scanCode)) {
            onClose();
            return true;
        }
        if (!acceptingInput()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) onClose();
            return true;
        }
        if (ready()
                && category == ConfigKey.Category.LIBRARY.ordinal()
                && browser.keyPressed(key, scanCode, modifiers)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!acceptingInput() || controlsPane.recording()) return true;
        if (ready()
                && category == ConfigKey.Category.LIBRARY.ordinal()
                && browser.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        if (!acceptingInput()) return;
        if (ready() && category == ConfigKey.Category.LIBRARY.ordinal())
            browser.filesDropped(files);
    }

    private int rowCategory(Row row) {
        if (row.entry == null) return ConfigKey.Category.EMULATION.ordinal();
        return row.entry.category();
    }

    @Override
    protected void renderContents(GuiGraphics context, int mx, int my, float delta) {
        var tr = font;
        for (var row : rows) row.hide();
        // Panel
        rounded(context, px + 3, py + 5, pw, ph, 0x90000000, 6);
        rounded(context, px, py, pw, ph, BG, 6);
        context.fill(px + 4, py, px + pw - 4, py + 1, 0x30FFFFFF);
        // Sidebar
        rounded(context, px + 1, py + 1, SIDEBAR - 1, ph - 2, 0xFF0E0B16, 5);
        context.fill(px + SIDEBAR - 7, py + 4, px + SIDEBAR, py + ph - 4, 0xFF0E0B16);
        ClientGraphics.blit(
                context,
                LOGO_TEX,
                px + (SIDEBAR - 104) / 2,
                py + 9,
                104,
                22,
                0,
                0,
                LOGO_W,
                LOGO_H,
                LOGO_W,
                LOGO_H);
        String version = "v" + Platform.version();
        context.drawCenteredString(
                tr,
                version + "  ·  " + (category < 0 ? "Client" : "Server"),
                px + SIDEBAR / 2 + 1,
                py + 36,
                MUTED);
        context.fill(px + 8, py + 48, px + SIDEBAR - 8, py + 49, 0x18FFFFFF);
        renderCategories(context, tr, mx, my);
        // Content header
        if (ready()) {
            String categoryName =
                    category < 0
                            ? category == CONTROLS ? "controls" : "client"
                            : ConfigKey.Category.values()[category].name().toLowerCase(Locale.ROOT);
            context.drawString(
                    tr,
                    Component.translatable("settings.retrocraft.category." + categoryName),
                    contentX,
                    py + 12,
                    TEXT);
            context.fill(contentX, py + 26, px + pw - 12, py + 27, 0x18FFFFFF);
        }
        switch (category < 0 ? State.READY : state) {
            case CONNECTING -> {
                int dots = (int) ((System.currentTimeMillis() / 350) % 4);
                context.drawCenteredString(
                        tr,
                        Component.translatable("settings.retrocraft.connecting")
                                .append(".".repeat(dots)),
                        px + SIDEBAR + (pw - SIDEBAR) / 2,
                        py + ph / 2 - 4,
                        MUTED);
            }
            case DENIED -> {
                int cx = px + SIDEBAR + (pw - SIDEBAR) / 2;
                draw(context, GLYPH_CLOCK, cx - 4, py + ph / 2 - 22, BAD);
                var message = Component.translatable("settings.retrocraft.denied." + denyReason);
                if ("operator".equals(denyReason))
                    message.append("\n\n")
                            .append(Component.translatable("settings.retrocraft.denied_hint"));
                var lines = tr.split(message, Math.max(1, contentW - 24));
                int lineY = py + ph / 2 - 6;
                for (var line : lines) {
                    context.drawString(
                            tr, line, contentX + (contentW - tr.width(line)) / 2, lineY, TEXT);
                    lineY += tr.lineHeight + 2;
                }
            }
            case READY -> {
                if (category == CONTROLS) controlsPane.render(context, mx, my);
                else if (category == ConfigKey.Category.LIBRARY.ordinal())
                    renderLibrary(context, tr, mx, my, delta);
                else if (category == ConfigKey.Category.CORES.ordinal())
                    corePane.render(context, mx, my, delta);
                else renderSettings(context, tr, mx, my, delta);
            }
        }
        // Footer
        context.fill(
                px + SIDEBAR + 8, py + ph - FOOTER, px + pw - 8, py + ph - FOOTER + 1, 0x18FFFFFF);
        if (status != null) {
            context.fill(
                    px + SIDEBAR + 14,
                    py + ph - FOOTER + 9,
                    px + SIDEBAR + 18,
                    py + ph - FOOTER + 13,
                    statusColor);
            context.drawString(
                    tr,
                    tr.plainSubstrByWidth(status.getString(), pw - SIDEBAR - 250),
                    px + SIDEBAR + 24,
                    py + ph - FOOTER + 7,
                    statusColor == BAD ? 0xFFF0A8A2 : MUTED);
        } else if (ready() && pendingChanges() > 0) {
            context.fill(
                    px + SIDEBAR + 14,
                    py + ph - FOOTER + 9,
                    px + SIDEBAR + 18,
                    py + ph - FOOTER + 13,
                    ACCENT);
            context.drawString(
                    tr,
                    Component.translatable("settings.retrocraft.unsaved", pendingChanges()),
                    px + SIDEBAR + 24,
                    py + ph - FOOTER + 7,
                    MUTED);
        }
        renderWidgets(context, mx, my, delta);
    }

    private void renderCategories(GuiGraphics context, Font tr, int mx, int my) {
        String[][] icons = {GLYPH_CHIP, GLYPH_WAVES, GLYPH_CLOCK, GLYPH_CORE, GLYPH_FOLDER};
        context.drawString(tr, Component.literal("Client"), px + 12, py + 56, MUTED);
        boolean clientSelected = category == CLIENT;
        rounded(
                context,
                px + 6,
                py + 68,
                SIDEBAR - 12,
                18,
                clientSelected ? 0xFF32284A : CONTROL,
                3);
        context.drawString(
                tr,
                Component.translatable("settings.retrocraft.category.client"),
                px + 24,
                py + 73,
                clientSelected ? TEXT : MUTED);
        boolean controlsSelected = category == CONTROLS;
        boolean controlsHot =
                mx >= px + 6 && mx < px + SIDEBAR - 6 && my >= py + 88 && my < py + 106;
        if (controlsSelected || controlsHot)
            rounded(
                    context,
                    px + 6,
                    py + 88,
                    SIDEBAR - 12,
                    18,
                    controlsSelected ? 0xFF32284A : 0x14FFFFFF,
                    3);
        context.drawString(
                tr,
                Component.translatable("settings.retrocraft.category.controls"),
                px + 24,
                py + 93,
                controlsSelected ? TEXT : MUTED);
        context.drawString(tr, Component.literal("Server"), px + 12, py + 114, MUTED);
        int y = py + 126;
        for (var cat : ConfigKey.Category.values()) {
            boolean selected = cat.ordinal() == category;
            boolean hot = mx >= px + 6 && mx < px + SIDEBAR - 6 && my >= y && my < y + 18;
            if (selected) {
                rounded(context, px + 6, y, SIDEBAR - 12, 18, 0xFF32284A, 3);
            } else if (hot) rounded(context, px + 6, y, SIDEBAR - 12, 18, 0x14FFFFFF, 3);
            draw(context, icons[cat.ordinal()], px + 12, y + 5, selected ? ACCENT : 0xFF6B7288);
            context.drawString(
                    tr,
                    Component.translatable(
                            "settings.retrocraft.category." + cat.name().toLowerCase(Locale.ROOT)),
                    px + 24,
                    y + 5,
                    selected ? TEXT : MUTED);
            y += 18;
        }
    }

    private void renderSettings(GuiGraphics context, Font tr, int mx, int my, float delta) {
        int clipY = contentY, clipH = contentH;
        // Position rows first so hover math and widget coordinates agree.
        int ry = contentY - (int) scroll;
        for (var row : rows) {
            if (rowCategory(row) != category) {
                row.hide();
                continue;
            }
            row.y = ry;
            boolean visible = ry >= clipY && ry + row.height() <= clipY + clipH;
            row.layout(contentX + contentW - 6, ry, visible);
            ry += row.height();
        }
        UiDrawing.scissor(context, contentX, clipY, contentX + contentW, clipY + clipH);
        for (var row : rows) {
            if (rowCategory(row) != category
                    || row.y + row.height() <= clipY
                    || row.y >= clipY + clipH) continue;
            row.render(context, tr, mx, my, delta);
        }
        context.disableScissor();
        // Scrollbar hugging the panel's inner right edge, draggable via the thumb or track.
        float max = maxScroll();
        if (max > 0) {
            int trackX = px + pw - 10;
            context.fill(trackX, clipY, trackX + 3, clipY + clipH, 0x30FFFFFF);
            int thumb = scrollThumb();
            int ty = clipY + (int) ((clipH - thumb) * scroll / max);
            context.fill(trackX, ty, trackX + 3, ty + thumb, 0xB0FFFFFF);
        }
    }

    private int scrollThumb() {
        return Math.max(12, contentH * contentH / Math.max(1, (int) maxScroll() + contentH));
    }

    private boolean scrollTrackHit(double mx, double my) {
        return category != CONTROLS
                && category != ConfigKey.Category.LIBRARY.ordinal()
                && maxScroll() > 0
                && mx >= px + pw - 13
                && mx < px + pw - 5
                && my >= contentY
                && my < contentY + contentH;
    }

    private void dragScroll(double my) {
        float max = maxScroll();
        scroll =
                Math.max(
                        0,
                        Math.min(
                                max,
                                (float)
                                        ((my - contentY - scrollThumb() / 2.0)
                                                / Math.max(1, contentH - scrollThumb())
                                                * max)));
    }

    private void renderLibrary(GuiGraphics context, Font tr, int mx, int my, float delta) {
        // The one scalar that lives in this category stays pinned above the browser.
        for (var row : rows) {
            if (rowCategory(row) != category) {
                row.hide();
                continue;
            }
            row.y = contentY;
            row.layout(contentX + contentW - 6, contentY, true);
            row.render(context, tr, mx, my, delta);
        }
        browser.render(context, mx, my, delta);
    }

    @Override
    public void renderBackground(GuiGraphics context, int mx, int my, float delta) {}

    @Override
    protected void renderDimmer(GuiGraphics context, float visibility) {
        context.fill(0, 0, width, height, ((int) (176 * visibility) << 24) | 0x080A0E);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        SettingsClient.screen(null);
        if (backend != null) backend.close();
        super.removed();
    }

    @Override
    protected void finishClose() {
        if (parent != null && minecraft != null) minecraft.setScreen(parent);
        else super.finishClose();
    }

    private final class Row {
        final SettingsPackets.GuiOpen.Entry entry; // null → section header
        final List<AbstractWidget> parts = new ArrayList<>();
        EditBox intField;
        Toggle toggle;
        Cycle cycle;
        int y;

        Row(SettingsPackets.GuiOpen.Entry entry) {
            this.entry = entry;
        }

        int height() {
            return entry == null ? SECTION_H : ROW_H;
        }

        void hide() {
            for (var part : parts) part.visible = false;
        }

        void layout(int right, int ry, boolean visible) {
            int cy = ry + (ROW_H - 16) / 2;
            if (entry == null) return;
            for (var part : parts) part.visible = visible;
            if (!visible) return;
            switch (entry.kind()) {
                case SettingsPackets.INT -> {
                    parts.get(1).setX(right - 76);
                    parts.get(1).setY(cy + 1); // minus
                    intField.setX(right - 60);
                    intField.setY(cy);
                    intField.setWidth(42);
                    parts.get(2).setX(right - 15);
                    parts.get(2).setY(cy + 1); // plus
                }
                case SettingsPackets.BOOL -> {
                    toggle.setX(right - 34);
                    toggle.setY(cy + 1);
                }
                case SettingsPackets.ENUM, SettingsPackets.CORE -> {
                    cycle.setX(right - 150);
                    cycle.setY(cy);
                }
                default -> {}
            }
        }

        void render(GuiGraphics context, Font tr, int mx, int my, float delta) {
            if (entry == null) {
                context.fill(contentX + 2, y + 12, contentX + contentW - 8, y + 13, 0x18FFFFFF);
                context.drawString(
                        tr,
                        Component.translatable("settings.retrocraft.cores"),
                        contentX + 2,
                        y + 4,
                        MUTED);
                return;
            }
            boolean hot = mx >= contentX && mx < contentX + contentW && my >= y && my < y + ROW_H;
            if (hot) rounded(context, contentX, y, contentW, ROW_H, 0x0FFFFFFF, 3);
            // Cartridge icon for core mappings; otherwise a plain label.
            int labelX = contentX + 6;
            if (entry.kind() == SettingsPackets.CORE) {
                Item item =
                        ConsoleRegistry.media(ResourceLocation.tryParse(entry.key().substring(6)));
                if (item == null) item = Items.AIR;
                context.renderItem(new ItemStack(item), contentX + 4, y + 8);
                context.drawString(
                        tr,
                        tr.plainSubstrByWidth(
                                new ItemStack(item).getHoverName().getString(), contentW - 170),
                        contentX + 24,
                        y + 8,
                        TEXT);
            } else {
                context.drawString(
                        tr,
                        tr.plainSubstrByWidth(
                                Component.translatable("settings.retrocraft.key." + entry.key())
                                        .getString(),
                                Math.max(0, contentW - 100)),
                        labelX,
                        y + 6,
                        TEXT);
                context.drawString(
                        tr,
                        tr.plainSubstrByWidth(
                                Component.translatable(
                                                "settings.retrocraft.key." + entry.key() + ".desc")
                                        .getString(),
                                Math.max(0, contentW - 100)),
                        labelX,
                        y + 17,
                        0xFF67707F);
            }
            boolean invalid = entry.kind() == SettingsPackets.INT && !intValid();
            if (invalid)
                context.fill(contentX + 2, y + ROW_H - 1, contentX + contentW - 8, y + ROW_H, BAD);
            for (var part : parts) part.render(context, mx, my, delta);
        }

        private boolean intValid() {
            try {
                int v = Integer.parseInt(value(entry));
                return v >= entry.min() && v <= entry.max();
            } catch (NumberFormatException ex) {
                return false;
            }
        }
    }

    private final class BrowserHost implements RomBrowser.Host {
        @Override
        public SettingsBackend backend() {
            return backend;
        }

        @Override
        public Font textRenderer() {
            return font;
        }

        @Override
        public Minecraft client() {
            return minecraft;
        }

        @Override
        public void status(Component text, int color) {
            SettingsScreen.this.status(text, color);
        }

        @Override
        public <T extends GuiEventListener & Renderable & NarratableEntry> T add(T widget) {
            return addRenderableWidget(widget);
        }

        @Override
        public <T extends GuiEventListener & NarratableEntry> T addHidden(T element) {
            return addWidget(element);
        }

        @Override
        public void removeInput(GuiEventListener element) {
            removeWidget(element);
        }
    }

    private final class CoreHost implements CorePane.Host {
        @Override
        public SettingsBackend backend() {
            return backend;
        }

        @Override
        public Font textRenderer() {
            return font;
        }

        @Override
        public void status(Component text, int color) {
            SettingsScreen.this.status(text, color);
        }
    }
}
