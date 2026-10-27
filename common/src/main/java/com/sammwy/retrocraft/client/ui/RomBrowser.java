package com.sammwy.retrocraft.client.ui;

import static com.sammwy.retrocraft.client.ui.SettingsWidgets.*;

import java.nio.file.*;
import java.util.*;

import org.lwjgl.util.tinyfd.TinyFileDialogs;

import com.sammwy.retrocraft.client.net.SettingsBackend;
import com.sammwy.retrocraft.client.net.SettingsClient;
import com.sammwy.retrocraft.client.ui.SettingsWidgets.Flat;
import com.sammwy.retrocraft.client.ui.SettingsWidgets.Glyph;
import com.sammwy.retrocraft.roms.RomCatalog;
import com.sammwy.retrocraft.roms.RomLibrary;
import com.sammwy.retrocraft.roms.discovery.ArcadeSets;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The ROM Library pane: a roms/ file browser with rename/move/delete/upload, and a platform view
 * listing every game the server published, grouped by cartridge.
 */
final class RomBrowser {
    /** Supplied by the screen so the pane can register widgets and report status. */
    interface Host {
        SettingsBackend backend();

        Font textRenderer();

        Minecraft client();

        void status(Component text, int color);

        <T extends GuiEventListener & Renderable & NarratableEntry> T add(T widget);

        <T extends GuiEventListener & NarratableEntry> T addHidden(T element);

        void removeInput(GuiEventListener element);
    }

    private static final int TOOLBAR = 24, ROW_H = 16, SECTION_H = 20;
    private static final int FILES = 0, GAMES = 1;

    private static final String[] GLYPH_FOLDER = {
        "XXX     ", "X  XXXXX", "X      X", "X      X", "X      X", "X      X", "XXXXXXX "
    };
    private static final String[] GLYPH_FILE = {
        "XXXXXX  ", "X     X ", "X    XXX", "X      X", "X      X", "X      X", "XXXXXXXX"
    };
    private static final String[] GLYPH_UP = {
        "   X   ", "  XXX  ", " X X X ", "X  X  X", "   X   ", "   X   ", "   X   "
    };
    private static final String[] GLYPH_REFRESH = {
        " XXXXX ", "X     X", "X X   X", "X  X   ", "X   X  ", "X   X X", " XXXXX "
    };
    private static final String[] GLYPH_SCAN_NEW = {
        " XXXXX ", "X     X", "X XX  X", "X XX   ", "X   X  ", "X   X X", " XXXXX "
    };
    private static final String[] GLYPH_PLUS = {
        "       ", "   X   ", "   X   ", " XXXXX ", "   X   ", "   X   ", "       "
    };
    private static final String[] GLYPH_UPLOAD = {
        "   X   ", "  XXX  ", " X X X ", "X  X  X", "   X   ", "XXXXXXX", "X     X"
    };
    private static final String[] GLYPH_EDIT = {
        "     XX", "    X X", "   X X ", "  X X  ", " X X   ", " XX    ", "X      "
    };
    private static final String[] GLYPH_MOVE = {
        "       ", "   X   ", "    X  ", " XXXXXX", "    X  ", "   X   ", "       "
    };
    private static final String[] GLYPH_TRASH = {
        " XXXXX ", "X X X X", " XXXXX ", " X   X ", " X X X ", " X   X ", " XXXXX "
    };
    private static final String[] GLYPH_CHECK = {
        "       ", "      X", "     X ", "X   X  ", " X X   ", "  X    ", "       "
    };
    private static final String[] GLYPH_CROSS = {
        "       ", " X   X ", "  X X  ", "   X   ", "  X X  ", " X   X ", "       "
    };
    private static final String[] GLYPH_PASTE = {
        "  XXX  ", " X   X ", "XXXXXXX", "X     X", "X XXX X", "X     X", "XXXXXXX"
    };
    private static final String[] GLYPH_CART = {
        "XXXXXXX", "X     X", "X XXX X", "X XXX X", "X     X", "X  XX X", "XXXXXXX"
    };

    private final Host host;
    private int x, y, w, h;
    private boolean active;
    private int mode = FILES;
    private String path = "";
    private List<SettingsPackets.RomList.Entry> entries = List.of();
    private boolean truncated, loading;
    private String error;
    private float scroll;
    private String clipboard;
    private int confirmDelete = -1;
    private int renameIndex = -1;
    private EditBox renameField;
    private boolean mkdir;
    private EditBox mkdirField;
    private boolean picking;
    private String uploadName;
    private float uploadProgress = -1;
    private int uploadId;
    private boolean scrollDrag;
    private List<Breadcrumb> breadcrumbs = List.of();
    // toolbar
    private SettingsWidgets.Glyph up, refresh, scanNew, newFolder, upload, paste, cancelMove;
    private SettingsWidgets.Flat filesTab, gamesTab;

    private record Breadcrumb(String label, String path, int x, int w) {}

    RomBrowser(Host host) {
        this.host = host;
    }

    void init(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        var tr = host.textRenderer();
        filesTab =
                host.add(
                        new Flat(
                                x,
                                y + 2,
                                52,
                                14,
                                Component.translatable("settings.retrocraft.roms.files"),
                                () -> mode(FILES)));
        gamesTab =
                host.add(
                        new Flat(
                                x + 56,
                                y + 2,
                                52,
                                14,
                                Component.translatable("settings.retrocraft.roms.games"),
                                () -> mode(GAMES)));
        int rx = x + w;
        cancelMove =
                host.add(
                        new Glyph(
                                rx -= 14,
                                y + 2,
                                GLYPH_CROSS,
                                () -> clipboard(null),
                                Component.translatable("settings.retrocraft.roms.cancel_move")));
        paste =
                host.add(
                        new Glyph(
                                rx -= 16,
                                y + 2,
                                GLYPH_PASTE,
                                this::pasteHere,
                                Component.translatable("settings.retrocraft.roms.paste")));
        upload =
                host.add(
                        new Glyph(
                                rx -= 16,
                                y + 2,
                                GLYPH_UPLOAD,
                                this::pickFile,
                                Component.translatable("settings.retrocraft.roms.upload")));
        newFolder =
                host.add(
                        new Glyph(
                                rx -= 16,
                                y + 2,
                                GLYPH_PLUS,
                                this::beginMkdir,
                                Component.translatable("settings.retrocraft.roms.new_folder")));
        refresh =
                host.add(
                        new Glyph(
                                rx -= 16,
                                y + 2,
                                GLYPH_REFRESH,
                                this::refresh,
                                Component.translatable("settings.retrocraft.roms.refresh")));
        scanNew =
                host.add(
                        new Glyph(
                                rx -= 16,
                                y + 2,
                                GLYPH_SCAN_NEW,
                                this::rescanNew,
                                Component.translatable("settings.retrocraft.roms.scan_new")));
        up =
                host.add(
                        new Glyph(
                                rx - 16,
                                y + 2,
                                GLYPH_UP,
                                this::up,
                                Component.translatable("settings.retrocraft.roms.up")));
        mkdirField = SettingsWidgets.field(tr, x, y, 10, "");
        mkdirField.setMaxLength(60);
        mkdirField.visible = false;
        host.addHidden(mkdirField);
        renameField = SettingsWidgets.field(tr, x, y, 10, "");
        renameField.setMaxLength(160);
        renameField.visible = false;
        host.addHidden(renameField);
        cancelEdits();
        rebuildBreadcrumbs();
        mode = FILES;
        filesTab.accent(true);
        gamesTab.accent(false);
        setActive(false);
    }

    /**
     * The screen owns one pane per category; outside Library every chrome widget and inline editor
     * is parked.
     */
    void setActive(boolean value) {
        boolean wasActive = active;
        active = value;
        for (var widget :
                new AbstractWidget[] {
                    up, refresh, newFolder, upload, paste, cancelMove, filesTab, gamesTab, scanNew
                }) if (widget != null) widget.visible = value;
        if (!value) cancelEdits();
        else if (!wasActive) load();
    }

    private void mode(int mode) {
        this.mode = mode;
        scroll = 0;
        confirmDelete = -1;
        cancelEdits();
        filesTab.accent(mode == FILES);
        gamesTab.accent(mode == GAMES);
        if (mode == FILES) load();
    }

    private void load() {
        if (mode != FILES) return;
        loading = true;
        error = null;
        String requested = path;
        host.backend()
                .list(
                        path,
                        (ok, list, isTruncated, message) -> {
                            loading = false;
                            if (!path.equals(requested)) return;
                            if (ok) {
                                entries = list;
                                truncated = isTruncated;
                                error = null;
                                rebuildBreadcrumbs();
                            } else {
                                entries = List.of();
                                error = message;
                            }
                        });
    }

    void reload() {
        if (active) load();
    }

    private void refresh() {
        load();
        host.backend()
                .action(
                        SettingsPackets.RESCAN,
                        "",
                        "",
                        (ok, message) ->
                                host.status(
                                        Component.translatable(
                                                ok
                                                        ? "settings.retrocraft.roms.rescanned"
                                                        : "settings.retrocraft.error",
                                                message),
                                        ok ? GOOD : BAD));
    }

    private void rescanNew() {
        load();
        host.backend()
                .action(
                        SettingsPackets.RESCAN_NEW,
                        "",
                        "",
                        (ok, message) ->
                                host.status(
                                        Component.translatable(
                                                ok
                                                        ? "settings.retrocraft.roms.rescanned"
                                                        : "settings.retrocraft.error",
                                                message),
                                        ok ? GOOD : BAD));
    }

    private void rebuildBreadcrumbs() {
        var crumbs = new ArrayList<Breadcrumb>();
        var tr = host.textRenderer();
        int cx = x + 116;
        String[] parts = path.isEmpty() ? new String[0] : path.split("/");
        StringBuilder current = new StringBuilder();
        List<String> labels = new ArrayList<>();
        labels.add("roms");
        List<String> paths = new ArrayList<>();
        paths.add("");
        for (String part : parts) {
            current.append(current.isEmpty() ? "" : "/").append(part);
            labels.add(part);
            paths.add(current.toString());
        }
        // Drop leading crumbs when they do not fit; the current folder always stays visible.
        int first = 0;
        while (first < labels.size() - 1) {
            int total = 0;
            for (int i = first; i < labels.size(); i++)
                total += tr.width(labels.get(i)) + (i == labels.size() - 1 ? 0 : 12);
            if (cx + total <= x + w - 112 || total < 0) break;
            first++;
        }
        for (int i = first; i < labels.size(); i++) {
            String label = (i == 0 && first > 0 ? "…/" : "") + labels.get(i);
            int cw = tr.width(label);
            crumbs.add(new Breadcrumb(label, paths.get(i), cx, cw));
            cx += cw + 12;
        }
        breadcrumbs = List.copyOf(crumbs);
    }

    private void navigate(String target) {
        if (Objects.equals(path, target)) return;
        path = target;
        scroll = 0;
        confirmDelete = -1;
        cancelEdits();
        load();
    }

    private void up() {
        if (path.isEmpty()) return;
        int slash = path.lastIndexOf('/');
        navigate(slash < 0 ? "" : path.substring(0, slash));
    }

    private String child(String name) {
        return path.isEmpty() ? name : path + "/" + name;
    }

    private void clipboard(String value) {
        clipboard = value;
        updateToolbar();
    }

    private void updateToolbar() {
        boolean files = mode == FILES && active;
        up.visible = files && !path.isEmpty();
        refresh.visible = files;
        newFolder.visible = files;
        upload.visible = files;
        paste.visible = files && clipboard != null;
        cancelMove.visible = files && clipboard != null;
        filesTab.visible = gamesTab.visible = active;
    }

    private void cancelEdits() {
        renameIndex = -1;
        renameField.visible = false;
        renameField.setFocused(false);
        mkdir = false;
        mkdirField.visible = false;
        mkdirField.setFocused(false);
    }

    private void beginMkdir() {
        cancelEdits();
        mkdir = true;
        mkdirField.setValue("");
        mkdirField.visible = true;
        mkdirField.setFocused(true);
        scroll = 0;
    }

    private void commitMkdir() {
        String name = mkdirField.getValue().trim();
        cancelEdits();
        if (!RomLibrary.safeName(name)) {
            host.status(Component.translatable("settings.retrocraft.roms.bad_name"), BAD);
            return;
        }
        host.backend()
                .action(
                        SettingsPackets.MKDIR,
                        child(name),
                        "",
                        (ok, message) -> done(ok, message, "settings.retrocraft.roms.created"));
    }

    private void beginRename(int index) {
        var entry = entries.get(index);
        cancelEdits();
        renameIndex = index;
        renameField.setValue(entry.name());
        renameField.visible = true;
        renameField.setFocused(true);
        renameField.moveCursorToEnd(false);
    }

    private void commitRename() {
        if (renameIndex < 0 || renameIndex >= entries.size()) {
            cancelEdits();
            return;
        }
        var entry = entries.get(renameIndex);
        String newName = renameField.getValue().trim();
        String old = child(entry.name());
        cancelEdits();
        if (newName.equals(entry.name())) return;
        if (!RomLibrary.safeName(newName)) {
            host.status(Component.translatable("settings.retrocraft.roms.bad_name"), BAD);
            return;
        }
        host.backend()
                .action(
                        SettingsPackets.RENAME,
                        old,
                        newName,
                        (ok, message) -> done(ok, message, "settings.retrocraft.roms.renamed"));
    }

    private void commitDelete(int index) {
        var entry = entries.get(index);
        confirmDelete = -1;
        host.backend()
                .action(
                        SettingsPackets.DELETE,
                        child(entry.name()),
                        "",
                        (ok, message) -> done(ok, message, "settings.retrocraft.roms.deleted"));
    }

    private void markMove(int index) {
        clipboard(child(entries.get(index).name()));
        host.status(
                Component.translatable(
                        "settings.retrocraft.roms.moving", entries.get(index).name()),
                MUTED);
    }

    private void pasteHere() {
        if (clipboard == null) return;
        String source = clipboard;
        clipboard(null);
        host.backend()
                .action(
                        SettingsPackets.MOVE,
                        source,
                        path,
                        (ok, message) -> done(ok, message, "settings.retrocraft.roms.moved"));
    }

    private void done(boolean ok, String message, String successKey) {
        if (ok) {
            host.status(Component.translatable(successKey), GOOD);
            load();
        } else
            host.status(
                    Component.translatable(
                            "settings.retrocraft.error", message == null ? "?" : message),
                    BAD);
    }

    private void pickFile() {
        if (picking) return;
        picking = true;
        Thread.ofVirtual()
                .name("retrocraft-rom-pick")
                .start(
                        () -> {
                            String chosen =
                                    TinyFileDialogs.tinyfd_openFileDialog(
                                            "Select a ROM", "", null, null, true);
                            var client = host.client();
                            client.execute(
                                    () -> {
                                        picking = false;
                                        if (chosen == null) return;
                                        for (String file : chosen.split("\\|"))
                                            upload(Path.of(file));
                                    });
                        });
    }

    void filesDropped(List<Path> files) {
        if (mode != FILES || uploadProgress >= 0) return;
        for (Path file : files)
            if (Files.isRegularFile(file)) {
                upload(file);
                return;
            }
    }

    private void upload(Path file) {
        if (uploadProgress >= 0) {
            host.status(Component.translatable("settings.retrocraft.roms.upload_busy"), BAD);
            return;
        }
        uploadName = file.getFileName().toString();
        uploadProgress = 0;
        uploadId++;
        int id = uploadId;
        host.backend()
                .upload(
                        file,
                        path,
                        new SettingsBackend.UploadCallback() {
                            @Override
                            public void progress(float fraction) {
                                if (uploadId == id)
                                    uploadProgress = Math.max(uploadProgress, fraction);
                            }

                            @Override
                            public void done(boolean ok, String message) {
                                if (uploadId != id) return;
                                uploadProgress = -1;
                                if (ok) {
                                    host.status(
                                            Component.translatable(
                                                    "settings.retrocraft.roms.uploaded",
                                                    uploadName),
                                            GOOD);
                                    load();
                                } else
                                    host.status(
                                            Component.translatable(
                                                    "settings.retrocraft.error",
                                                    message == null ? "?" : message),
                                            BAD);
                            }
                        });
    }

    boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!active) return false;
        if (renameField.visible) {
            if (key == 257 || key == 335) {
                commitRename();
                return true;
            }
            if (key == 256) {
                cancelEdits();
                return true;
            }
            if (com.sammwy.retrocraft.compat.ClientInput.key(renameField, key, scanCode, modifiers))
                return true;
        }
        if (mkdirField.visible) {
            if (key == 257 || key == 335) {
                commitMkdir();
                return true;
            }
            if (key == 256) {
                cancelEdits();
                return true;
            }
            if (com.sammwy.retrocraft.compat.ClientInput.key(mkdirField, key, scanCode, modifiers))
                return true;
        }
        if (key == 256) {
            cancelEdits();
            confirmDelete = -1;
            return false;
        }
        return false;
    }

    boolean charTyped(char chr, int modifiers) {
        if (!active) return false;
        if (renameField.visible
                && com.sammwy.retrocraft.compat.ClientInput.character(renameField, chr, modifiers))
            return true;
        if (mkdirField.visible
                && com.sammwy.retrocraft.compat.ClientInput.character(mkdirField, chr, modifiers))
            return true;
        return false;
    }

    boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!active || !inside(mx, my)) return false;
        scroll = clampScroll(scroll - (float) vertical * 12);
        return true;
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (!active || !inside(mx, my)) return false;
        if (button != 0) return true;
        if (scrollTrackHit(mx, my)) {
            scrollDrag = true;
            dragScroll(my);
            return true;
        }
        for (var crumb : breadcrumbs)
            if (mx >= crumb.x && mx < crumb.x + crumb.w + 8 && my >= y + 4 && my < y + 14) {
                navigate(crumb.path);
                return true;
            }
        if (my < y + TOOLBAR + 4) return false; // the tab/glyph widgets own the toolbar strip
        int row = rowAt(my);
        if (row < 0) return true;
        if (mode == GAMES) return true;
        int listRow = mkdir ? row - 1 : row;
        // The inline editors own their row: the ✓/✕ glyphs commit or cancel, anything else just
        // focuses the field.
        if (mkdir && listRow == -1) {
            if (editorGlyph(mx, 0)) return true;
            return true;
        }
        if (!path.isEmpty() && listRow == 0) {
            up();
            return true;
        }
        int index = listRow - (path.isEmpty() ? 0 : 1);
        if (index < 0 || index >= entries.size()) return true;
        var entry = entries.get(index);
        if (confirmDelete == index) {
            int rw = w - 12;
            if (mx >= x + rw - 74 && mx < x + rw - 40) {
                commitDelete(index);
                return true;
            }
            if (mx >= x + rw - 38 && mx < x + rw) {
                confirmDelete = -1;
                return true;
            }
            return true;
        }
        if (renameIndex == index) {
            editorGlyph(mx, 1);
            return true;
        }
        cancelEdits();
        // Hover actions: three 14px slots measured back from the row's right edge — must match
        // render.
        int edge = x + w - 14;
        if (mx >= edge - 42 && mx < edge) {
            int slot = (int) (edge - mx) / 14;
            if (slot == 0) {
                confirmDelete = index;
                return true;
            }
            if (slot == 1) {
                markMove(index);
                return true;
            }
            if (slot == 2) {
                beginRename(index);
                return true;
            }
        }
        if (entry.directory()) navigate(child(entry.name()));
        return true;
    }

    /**
     * Hit-test for the ✓/✕ glyphs shown next to inline editors; returns true when the click was
     * consumed.
     */
    private boolean editorGlyph(double mx, int kind) {
        int check = x + w - 12 - 34, cross = x + w - 12 - 18;
        if (mx >= check && mx < check + 12) {
            if (kind == 0) commitMkdir();
            else commitRename();
            return true;
        }
        if (mx >= cross && mx < cross + 12) {
            cancelEdits();
            return true;
        }
        return false;
    }

    boolean mouseDragged(double mx, double my) {
        if (scrollDrag) {
            dragScroll(my);
            return true;
        }
        return false;
    }

    void mouseReleased() {
        scrollDrag = false;
    }

    private boolean scrollTrackHit(double mx, double my) {
        int listY = y + TOOLBAR + 4, listH = y + h - listY;
        int content = mode == GAMES ? gamesRows() : rowCount() * ROW_H;
        return content > listH
                && mx >= x + w - 7
                && mx < x + w
                && my >= listY
                && my < listY + listH;
    }

    private void dragScroll(double my) {
        int listY = y + TOOLBAR + 4, listH = y + h - listY;
        int content = mode == GAMES ? gamesRows() : rowCount() * ROW_H;
        int thumb = Math.max(12, listH * listH / content);
        float max = Math.max(0, content - listH);
        scroll =
                Math.max(
                        0,
                        Math.min(
                                max,
                                (float)
                                        ((my - listY - thumb / 2.0)
                                                / Math.max(1, listH - thumb)
                                                * max)));
    }

    private boolean inside(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int rowAt(double my) {
        int listY = y + TOOLBAR + 4;
        if (my < listY || my >= y + h) return -1;
        int row = (int) ((my - listY + scroll) / ROW_H);
        int total = rowCount();
        return row < total ? row : -1;
    }

    private int rowCount() {
        if (mode == GAMES) return gamesRows();
        int count = entries.size() + (path.isEmpty() ? 0 : 1);
        return count + (mkdir ? 1 : 0);
    }

    private int gamesRows() {
        int rows = 0;
        String last = null;
        for (var entry : RomCatalog.clientEntries()) {
            if (!entry.cartridge().equals(last)) {
                rows += SECTION_H;
                last = entry.cartridge();
            }
            rows += ROW_H;
        }
        return rows == 0 ? 0 : rows;
    }

    private float clampScroll(float value) {
        int content = mode == GAMES ? gamesRows() : rowCount() * ROW_H;
        return Math.max(0, Math.min(value, Math.max(0, content - (h - TOOLBAR - 8))));
    }

    void render(GuiGraphics context, int mx, int my, float delta) {
        updateToolbar();
        var tr = host.textRenderer();
        // Toolbar: tabs on the left, breadcrumb, action glyphs on the right (real widgets).
        if (mode == FILES) {
            for (int i = 0; i < breadcrumbs.size(); i++) {
                var crumb = breadcrumbs.get(i);
                boolean hot =
                        mx >= crumb.x && mx < crumb.x + crumb.w + 8 && my >= y + 4 && my < y + 14;
                context.drawString(
                        tr,
                        crumb.label,
                        crumb.x,
                        y + 5,
                        hot ? ACCENT : (i == breadcrumbs.size() - 1 ? TEXT : MUTED));
                if (i < breadcrumbs.size() - 1)
                    context.drawString(tr, "›", crumb.x + crumb.w + 4, y + 5, 0xFF5A6172);
            }
        }
        if (SettingsClient.scanning()) {
            int sx =
                    mode != FILES || breadcrumbs.isEmpty()
                            ? x + 116
                            : breadcrumbs.get(breadcrumbs.size() - 1).x
                                    + breadcrumbs.get(breadcrumbs.size() - 1).w
                                    + 12;
            String actor = SettingsClient.scanActor();
            String text =
                    "⟳ "
                            + Component.translatable(
                                            "settings.retrocraft.roms.scanning",
                                            actor.isBlank() ? "server" : actor)
                                    .getString();
            context.drawString(tr, text, Math.min(sx, x + w - 100 - tr.width(text)), y + 5, ACCENT);
        }
        int listY = y + TOOLBAR + 4, listH = y + h - listY;
        context.fill(x, listY - 2, x + w, listY - 1, 0x18FFFFFF);
        UiDrawing.scissor(context, x, listY, x + w, listY + listH);
        if (mode == GAMES) renderGames(context, tr, listY, listH);
        else renderFiles(context, tr, mx, my, listY, listH);
        context.disableScissor();
        // Scrollbar flush against the pane's right edge, draggable.
        int content = mode == GAMES ? gamesRows() : rowCount() * ROW_H;
        if (content > listH) {
            int trackX = x + w - 4;
            context.fill(trackX, listY, trackX + 3, listY + listH, 0x30FFFFFF);
            int thumb = Math.max(12, listH * listH / content);
            int ty = listY + (int) ((listH - thumb) * scroll / Math.max(1, content - listH));
            context.fill(trackX, ty, trackX + 3, ty + thumb, 0xB0FFFFFF);
        }
        renderUpload(context, tr);
    }

    private void renderFiles(GuiGraphics context, Font tr, int mx, int my, int listY, int listH) {
        int ry = listY - (int) scroll;
        mkdirField.visible = false;
        renameField.visible = false;
        if (mkdir) {
            if (rowVisible(ry, listY, listH)) {
                drawRowBg(context, ry, false);
                icon(context, GLYPH_PLUS, x + 6, ry + 4, ACCENT);
                mkdirField.setX(x + 20);
                mkdirField.setY(ry);
                mkdirField.setWidth(w - 72);
                mkdirField.visible = true;
                mkdirField.render(context, mx, my, 0);
                icon(context, GLYPH_CHECK, x + w - 12 - 34, ry + 4, GOOD);
                icon(context, GLYPH_CROSS, x + w - 12 - 18, ry + 4, BAD);
            }
            ry += ROW_H;
        }
        if (!path.isEmpty()) {
            if (rowVisible(ry, listY, listH)) {
                drawRowBg(context, ry, hovered(mx, my, ry));
                icon(context, GLYPH_UP, x + 6, ry + 4, MUTED);
                context.drawString(tr, "..", x + 20, ry + 4, MUTED);
            }
            ry += ROW_H;
        }
        for (int i = 0; i < entries.size(); i++, ry += ROW_H) {
            if (!rowVisible(ry, listY, listH)) continue;
            var entry = entries.get(i);
            boolean hot = hovered(mx, my, ry);
            drawRowBg(context, ry, hot);
            if (confirmDelete == i) {
                icon(context, GLYPH_TRASH, x + 6, ry + 4, BAD);
                context.drawString(
                        tr,
                        Component.translatable(
                                "settings.retrocraft.roms.confirm_delete",
                                tr.plainSubstrByWidth(entry.name(), w - 160)),
                        x + 20,
                        ry + 4,
                        TEXT);
                int bx = x + w - 12 - 74;
                drawMiniButton(
                        context,
                        tr,
                        bx,
                        ry + 1,
                        34,
                        Component.translatable("settings.retrocraft.roms.delete"),
                        mx,
                        my,
                        BAD);
                drawMiniButton(
                        context,
                        tr,
                        bx + 36,
                        ry + 1,
                        38,
                        Component.translatable("settings.retrocraft.roms.cancel"),
                        mx,
                        my,
                        MUTED);
                continue;
            }
            if (renameIndex == i) {
                icon(context, GLYPH_EDIT, x + 6, ry + 4, ACCENT);
                renameField.setX(x + 20);
                renameField.setY(ry);
                renameField.setWidth(w - 72);
                renameField.visible = true;
                renameField.render(context, mx, my, 0);
                icon(context, GLYPH_CHECK, x + w - 12 - 34, ry + 4, GOOD);
                icon(context, GLYPH_CROSS, x + w - 12 - 18, ry + 4, BAD);
                continue;
            }
            icon(
                    context,
                    entry.directory() ? GLYPH_FOLDER : GLYPH_FILE,
                    x + 6,
                    ry + 4,
                    entry.directory() ? ACCENT : 0xFF8B93A6);
            context.drawString(
                    tr, tr.plainSubstrByWidth(entry.name(), w - 130), x + 20, ry + 4, TEXT);
            if (!entry.directory() && !hot)
                context.drawString(
                        tr,
                        humanSize(entry.size()),
                        x + w - 12 - tr.width(humanSize(entry.size())),
                        ry + 4,
                        MUTED);
            if (hot) {
                // Actions replace the size label; keep in sync with the click slots above.
                int edge = x + w - 14;
                icon(context, GLYPH_TRASH, edge - 11, ry + 4, BAD);
                icon(context, GLYPH_MOVE, edge - 25, ry + 4, 0xFFE4E8F0);
                icon(context, GLYPH_EDIT, edge - 39, ry + 4, 0xFFE4E8F0);
            }
        }
        if (loading)
            context.drawCenteredString(
                    tr,
                    Component.translatable("settings.retrocraft.roms.loading"),
                    x + w / 2,
                    listY + listH / 2 - 4,
                    MUTED);
        else if (entries.isEmpty() && error == null && !mkdir)
            context.drawCenteredString(
                    tr,
                    Component.translatable("settings.retrocraft.roms.empty"),
                    x + w / 2,
                    listY + listH / 2 - 4,
                    MUTED);
        else if (error != null)
            context.drawCenteredString(
                    tr, Component.literal(error), x + w / 2, listY + listH / 2 - 4, BAD);
        if (truncated)
            context.drawString(
                    tr,
                    Component.translatable("settings.retrocraft.roms.truncated"),
                    x + 4,
                    listY + listH - 9,
                    BAD);
    }

    private void renderGames(GuiGraphics context, Font tr, int listY, int listH) {
        int ry = listY - (int) scroll;
        var catalog = RomCatalog.clientEntries();
        if (catalog.isEmpty()) {
            context.drawCenteredString(
                    tr,
                    Component.translatable("settings.retrocraft.roms.no_games"),
                    x + w / 2,
                    listY + listH / 2 - 4,
                    MUTED);
            return;
        }
        String last = null;
        for (var entry : catalog) {
            if (!entry.cartridge().equals(last)) {
                last = entry.cartridge();
                if (ry + SECTION_H > listY && ry < listY + listH) {
                    Item item = ConsoleRegistry.media(ResourceLocation.tryParse(entry.cartridge()));
                    if (item == null) item = Items.AIR;
                    context.renderItem(new ItemStack(item), x + 4, ry + 2);
                    String title = new ItemStack(item).getHoverName().getString();
                    long count =
                            catalog.stream()
                                    .filter(e -> e.cartridge().equals(entry.cartridge()))
                                    .count();
                    context.drawString(tr, title, x + 24, ry + 5, TEXT);
                    context.drawString(
                            tr, "· " + count, x + 24 + tr.width(title) + 4, ry + 5, MUTED);
                    context.fill(
                            x + 4, ry + SECTION_H - 3, x + w - 8, ry + SECTION_H - 2, 0x18FFFFFF);
                }
                ry += SECTION_H;
            }
            if (ry + ROW_H > listY && ry < listY + listH) {
                icon(context, GLYPH_CART, x + 8, ry + 4, 0xFF6B7288);
                var machine = arcade(entry.cartridge()) ? ArcadeSets.machine(entry.name()) : null;
                String label =
                        machine != null
                                ? entry.name()
                                        + " ("
                                        + machine.zip()
                                        + (machine.bios() != null
                                                ? " · BIOS: " + machine.bios() + ".zip"
                                                : "")
                                        + ")"
                                : entry.name();
                context.drawString(tr, tr.plainSubstrByWidth(label, w - 120), x + 24, ry + 4, TEXT);
                context.drawString(
                        tr,
                        entry.gameId().substring(0, 8),
                        x + w - 12 - tr.width(entry.gameId().substring(0, 8)),
                        ry + 4,
                        0xFF5A6172);
            }
            ry += ROW_H;
        }
    }

    private void renderUpload(GuiGraphics context, Font tr) {
        if (uploadProgress < 0 && !picking) return;
        int by = y + h - 12;
        String label =
                picking
                        ? Component.translatable("settings.retrocraft.roms.picking").getString()
                        : Component.translatable(
                                        "settings.retrocraft.roms.uploading",
                                        uploadName,
                                        Math.round(uploadProgress * 100))
                                .getString();
        context.fill(x + 1, by - 2, x + w - 1, by + 11, 0xE014171E);
        context.drawString(tr, tr.plainSubstrByWidth(label, w - 90), x + 6, by, TEXT);
        if (uploadProgress >= 0) {
            int barX = x + w - 76, barW = 68;
            context.fill(barX, by + 2, barX + barW, by + 7, 0xFF232833);
            context.fill(
                    barX,
                    by + 2,
                    barX + Math.round(barW * Math.max(0, Math.min(1, uploadProgress))),
                    by + 7,
                    ACCENT);
        }
    }

    private void drawRowBg(GuiGraphics context, int ry, boolean hot) {
        if (hot) rounded(context, x + 2, ry, w - 8, ROW_H, 0x14FFFFFF, 2);
    }

    private void drawMiniButton(
            GuiGraphics context,
            Font tr,
            int bx,
            int by,
            int bw,
            Component label,
            int mx,
            int my,
            int color) {
        boolean hot = mx >= bx && mx < bx + bw && my >= by && my < by + 14;
        rounded(context, bx, by, bw, 14, hot ? 0xFF3A4150 : 0xFF232833, 3);
        context.drawCenteredString(tr, label, bx + bw / 2, by + 3, color);
    }

    private void icon(GuiGraphics context, String[] art, int x, int y, int color) {
        draw(context, art, x, y, color);
    }

    private boolean hovered(int mx, int my, int ry) {
        return mx >= x + 2 && mx < x + w - 6 && my >= ry && my < ry + ROW_H;
    }

    private boolean rowVisible(int ry, int listY, int listH) {
        return ry + ROW_H > listY && ry < listY + listH;
    }

    private static boolean arcade(String system) {
        var media = ConsoleRegistry.media(ResourceLocation.tryParse(system));
        return media != null && media.system().arcade();
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KiB";
        return String.format("%.1f MiB", bytes / 1048576.0);
    }
}
