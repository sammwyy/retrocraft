package com.sammwy.retrocraft.client.ui;

import static com.sammwy.retrocraft.client.ui.SettingsWidgets.*;

import java.util.*;

import org.lwjgl.glfw.GLFW;

import com.sammwy.retrocraft.compat.ClientGraphics;
import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.compat.UiPose;
import com.sammwy.retrocraft.platform.ClientNetwork;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.server.net.LinkPackets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** A HUD modal that leaves the play screen and its console subscription alive. */
final class LinkDialog {
    private static final int HEADER = 60, FOOTER = 42, ROW = 44;
    private static final String[] CLOSE = {"X   X", " X X ", "  X  ", " X X ", "X   X"};
    private static final String[] REFRESH = {
        " XXXX X", "X    XX", "X   XXX", "X      ", "X     X", " X   X ", "  XXX  "
    };
    private final LinkPackets.State state;
    private final Runnable dismiss;
    private final UiMotion motion = new UiMotion();
    private final long receivedAt = System.nanoTime();
    private final List<AbstractWidget> buttons = new ArrayList<>();
    private final ItemStack cable =
            new ItemStack(
                    ContentProperties.get(
                            BuiltInRegistries.ITEM, Ids.of("retrocraft", "gb_link_cable")));
    private int width, height, panelW, panelH, page, pageSize = 4, focused = -1;
    private float originX, originY, scale = 1;

    LinkDialog(LinkPackets.State state, Runnable dismiss) {
        this.state = state;
        this.dismiss = dismiss;
    }

    UUID token() {
        return state.token();
    }

    private boolean acceptingInput() {
        return motion.shown() && motion.settled(System.nanoTime());
    }

    private void close() {
        motion.show(false, System.nanoTime());
    }

    void serverClosed() {
        close();
    }

    void cancel() {
        if (!motion.shown()) return;
        if (state.mode() == LinkPackets.OFFER) send(LinkPackets.DECLINE, state.token());
        else if (state.mode() == LinkPackets.WAITING) send(LinkPackets.CANCEL, state.token());
        close();
    }

    private void send(int action, UUID target) {
        ClientNetwork.send(new LinkPackets.Request(action, state.instance(), target));
    }

    private SettingsWidgets.Flat button(int x, int y, int w, Component label, Runnable action) {
        var button = new SettingsWidgets.Flat(x, y, w, 22, label, action);
        buttons.add(button);
        return button;
    }

    private void action(int action, UUID target) {
        send(action, target);
        close();
    }

    private int pages() {
        return Math.max(1, (state.entries().size() + pageSize - 1) / pageSize);
    }

    private void changePage(int direction) {
        page = Math.clamp(page + direction, 0, pages() - 1);
        layout(width, height);
    }

    private void layout(int w, int h) {
        width = w;
        height = h;
        panelW = Math.clamp(w - 32, 280, 336);
        pageSize = Math.clamp((h - 24 - HEADER - FOOTER) / ROW, 1, 4);
        page = Math.min(page, pages() - 1);
        int rows = Math.max(1, Math.min(pageSize, state.entries().size()));
        panelH =
                HEADER
                        + FOOTER
                        + (state.mode() == LinkPackets.CANDIDATES && !state.entries().isEmpty()
                                ? rows * ROW
                                : 112);
        scale = Math.min(1f, Math.min((w - 16f) / panelW, (h - 16f) / panelH));
        originX = (w - panelW * scale) / 2;
        originY = (h - panelH * scale) / 2;
        buttons.clear();
        focused = -1;
        buttons.add(
                new SettingsWidgets.Glyph(
                        panelW - 27, 12, CLOSE, this::cancel, Component.translatable("gui.close")));
        int y = panelH - 30;
        switch (state.mode()) {
            case LinkPackets.CANDIDATES -> {
                int start = page * pageSize;
                for (int i = start; i < Math.min(start + pageSize, state.entries().size()); i++) {
                    var entry = state.entries().get(i);
                    buttons.add(
                            new PlayerRow(
                                    12,
                                    HEADER + (i - start) * ROW,
                                    panelW - 24,
                                    entry,
                                    () -> action(LinkPackets.INVITE, entry.instance())));
                }
                button(12, y, 76, text("refresh"), () -> send(LinkPackets.LIST, LinkPackets.NONE));
                button(panelW - 88, y, 76, Component.translatable("gui.cancel"), this::cancel);
                if (pages() > 1) {
                    var previous =
                            button(
                                    panelW / 2 - 45,
                                    y,
                                    22,
                                    Component.literal("‹"),
                                    () -> changePage(-1));
                    previous.active = page > 0;
                    var next =
                            button(
                                    panelW / 2 + 23,
                                    y,
                                    22,
                                    Component.literal("›"),
                                    () -> changePage(1));
                    next.active = page + 1 < pages();
                }
            }
            case LinkPackets.OFFER -> {
                int half = (panelW - 32) / 2;
                button(12, y, half, text("decline"), this::cancel);
                button(
                                20 + half,
                                y,
                                half,
                                text("accept"),
                                () -> action(LinkPackets.ACCEPT, state.token()))
                        .accent(true);
            }
            case LinkPackets.WAITING ->
                    button(12, y, panelW - 24, text("cancel_request"), this::cancel);
            case LinkPackets.CONNECTED -> {
                int half = (panelW - 32) / 2;
                button(
                        12,
                        y,
                        half,
                        text("disconnect"),
                        () -> action(LinkPackets.DISCONNECT, LinkPackets.NONE));
                button(20 + half, y, half, Component.translatable("gui.done"), this::close)
                        .accent(true);
            }
            default -> {
                int half = (panelW - 32) / 2;
                button(12, y, half, Component.translatable("gui.done"), this::close);
                button(
                                20 + half,
                                y,
                                half,
                                text("find_players"),
                                () -> send(LinkPackets.LIST, LinkPackets.NONE))
                        .accent(true);
            }
        }
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.retrocraft.link." + key, args);
    }

    private String subtitle() {
        return switch (state.mode()) {
            case LinkPackets.CANDIDATES -> "select_player";
            case LinkPackets.OFFER -> "incoming";
            case LinkPackets.WAITING -> "waiting";
            case LinkPackets.CONNECTED -> "connected";
            case LinkPackets.DECLINED -> "declined";
            case LinkPackets.EXPIRED -> "expired";
            default -> "unavailable";
        };
    }

    void render(GuiGraphics context, Font font, int w, int h, int mx, int my, float delta) {
        if (width != w || height != h) layout(w, h);
        long now = System.nanoTime();
        float visibility = motion.value(now);
        if (!motion.shown() && motion.settled(now)) {
            dismiss.run();
            return;
        }
        context.fill(0, 0, w, h, (int) (144 * visibility) << 24);
        int mouseX = acceptingInput() ? (int) ((mx - originX) / scale) : -1;
        int mouseY = acceptingInput() ? (int) ((my - originY) / scale) : -1;
        UiPose.of(context).pushPose();
        try {
            UiPose.of(context).translate(originX, originY, 0);
            UiPose.of(context).scale(scale, scale, 1);
            UiDrawing.pop(context, panelW / 2f, panelH / 2f, visibility);
            rounded(context, 2, 4, panelW, panelH, 0x70000000, 7);
            rounded(context, 0, 0, panelW, panelH, EDGE, 7);
            rounded(context, 1, 1, panelW - 2, panelH - 2, BG, 6);
            rounded(context, 1, 1, panelW - 2, 46, PANEL, 6);
            context.fill(8, 1, panelW - 8, 2, 0x18FFFFFF);
            rounded(context, 12, 11, 28, 28, 0xFF322344, 5);
            context.renderItem(cable, 18, 17);
            context.drawString(font, text("title"), 50, 13, TEXT, false);
            context.drawString(font, text(subtitle()), 50, 27, MUTED, false);
            badge(context, font, now);
            if (state.mode() == LinkPackets.CANDIDATES) renderCandidates(context, font);
            else renderStatus(context, font, now);
            context.fill(12, panelH - FOOTER, panelW - 12, panelH - FOOTER + 1, EDGE);
            if (state.mode() == LinkPackets.CANDIDATES && pages() > 1)
                context.drawCenteredString(
                        font, (page + 1) + " / " + pages(), panelW / 2, panelH - 23, MUTED);
            for (var button : buttons) button.render(context, mouseX, mouseY, delta);
        } finally {
            ClientGraphics.color(context, 1, 1, 1, 1);
            UiPose.of(context).popPose();
        }
    }

    private void badge(GuiGraphics context, Font font, long now) {
        Component label =
                switch (state.mode()) {
                    case LinkPackets.CANDIDATES -> text("range");
                    case LinkPackets.OFFER, LinkPackets.WAITING ->
                            text("seconds", Math.max(0, 30 - (now - receivedAt) / 1_000_000_000L));
                    default -> null;
                };
        if (label == null) return;
        int width = font.width(label) + 12, x = panelW - 37 - width;
        rounded(context, x, 12, width, 16, CONTROL, 4);
        context.drawString(font, label, x + 6, 16, MUTED, false);
    }

    private void renderCandidates(GuiGraphics context, Font font) {
        if (!state.entries().isEmpty()) return;
        rounded(context, panelW / 2 - 20, HEADER + 4, 40, 40, PANEL, 8);
        UiDrawing.centeredGlyph(context, REFRESH, panelW / 2 - 20, HEADER + 4, 40, 40, ACCENT);
        centered(context, font, text("empty"), HEADER + 55, TEXT);
        paragraph(context, font, text("empty_hint"), HEADER + 73, MUTED);
    }

    private void renderStatus(GuiGraphics context, Font font, long now) {
        boolean success = state.mode() == LinkPackets.CONNECTED;
        boolean pending = state.mode() == LinkPackets.OFFER || state.mode() == LinkPackets.WAITING;
        int color = success ? GOOD : pending ? ACCENT : MUTED;
        String name = state.entries().isEmpty() ? "" : state.entries().getFirst().name();
        rounded(context, 12, HEADER, panelW - 24, 42, PANEL, 5);
        if (!name.isEmpty()) {
            avatar(context, name, 22, HEADER + 9, 24);
            context.drawString(
                    font, font.plainSubstrByWidth(name, panelW - 96), 56, HEADER + 9, TEXT, false);
            context.drawString(
                    font, text(success ? "peer_connected" : "peer"), 56, HEADER + 24, MUTED, false);
        } else {
            context.renderItem(cable, 24, HEADER + 13);
            context.drawString(font, text("title"), 50, HEADER + 17, TEXT, false);
        }
        rounded(context, panelW - 33, HEADER + 17, 6, 6, color, 3);
        String description =
                switch (state.mode()) {
                    case LinkPackets.OFFER -> "invitation_hint";
                    case LinkPackets.WAITING -> "waiting_hint";
                    case LinkPackets.CONNECTED -> "connected_hint";
                    case LinkPackets.DECLINED -> "declined_hint";
                    case LinkPackets.EXPIRED -> "expired_hint";
                    default -> "unavailable_hint";
                };
        paragraph(context, font, text(description), HEADER + 54, MUTED);
        if (pending) {
            int y = panelH - FOOTER - 10, barW = panelW - 32;
            rounded(context, 16, y, barW, 2, EDGE, 1);
            float remaining = Math.clamp(1f - (now - receivedAt) / 30_000_000_000f, 0f, 1f);
            int filled = Math.round(barW * remaining);
            if (filled > 1) rounded(context, 16, y, filled, 2, ACCENT, 1);
        }
    }

    private void centered(GuiGraphics context, Font font, Component label, int y, int color) {
        context.drawString(font, label, (panelW - font.width(label)) / 2, y, color, false);
    }

    private void paragraph(GuiGraphics context, Font font, Component label, int y, int color) {
        for (var line : font.split(label, panelW - 40)) {
            context.drawString(font, line, (panelW - font.width(line)) / 2, y, color, false);
            y += 11;
        }
    }

    private static void avatar(GuiGraphics context, String name, int x, int y, int size) {
        var network = Minecraft.getInstance().getConnection();
        ResourceLocation skin =
                network == null
                        ? DefaultPlayerSkin.getDefaultTexture()
                        : network.getOnlinePlayers().stream()
                                .filter(
                                        p ->
                                                com.sammwy.retrocraft.compat.Profiles.name(
                                                                p.getProfile())
                                                        .equals(name))
                                .findFirst()
                                .map(p -> com.sammwy.retrocraft.compat.SkinTextures.texture(p))
                                .orElse(DefaultPlayerSkin.getDefaultTexture());
        rounded(context, x - 2, y - 2, size + 4, size + 4, EDGE, 4);
        ClientGraphics.blit(context, skin, x, y, size, size, 8f, 8f, 8, 8, 64, 64);
        ClientGraphics.blit(context, skin, x, y, size, size, 40f, 8f, 8, 8, 64, 64);
    }

    void click(double x, double y, int button) {
        if (!acceptingInput()) return;
        double mx = (x - originX) / scale, my = (y - originY) / scale;
        for (var widget : List.copyOf(buttons))
            if (com.sammwy.retrocraft.compat.ClientInput.click(widget, mx, my, button)) return;
    }

    void scroll(double amount) {
        if (acceptingInput() && state.mode() == LinkPackets.CANDIDATES && amount != 0)
            changePage(amount < 0 ? 1 : -1);
    }

    void keyPressed(int key, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return;
        }
        if (!acceptingInput()) return;
        if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            int direction =
                    key == GLFW.GLFW_KEY_UP || (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
            if (focused < 0 && direction < 0) focused = 0;
            for (int i = 0; i < buttons.size(); i++) {
                focused = Math.floorMod(focused + direction, buttons.size());
                if (buttons.get(focused).active) break;
            }
            for (int i = 0; i < buttons.size(); i++) buttons.get(i).setFocused(i == focused);
        } else if (key == GLFW.GLFW_KEY_ENTER
                || key == GLFW.GLFW_KEY_KP_ENTER
                || key == GLFW.GLFW_KEY_SPACE) {
            if (focused >= 0) {
                var button = buttons.get(focused);
                button.playDownSound(Minecraft.getInstance().getSoundManager());
                com.sammwy.retrocraft.compat.ClientInput.activate(button);
            }
        } else if (state.mode() == LinkPackets.CANDIDATES) {
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) changePage(-1);
            if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) changePage(1);
        }
    }

    private static final class PlayerRow extends com.sammwy.retrocraft.compat.InputWidget {
        private final LinkPackets.Entry entry;
        private final Runnable invite;

        PlayerRow(int x, int y, int width, LinkPackets.Entry entry, Runnable invite) {
            super(x, y, width, 40, text("invite_player", entry.name()));
            this.entry = entry;
            this.invite = invite;
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            boolean hot = isHovered() || isFocused();
            rounded(
                    context,
                    getX(),
                    getY(),
                    getWidth(),
                    getHeight(),
                    hot ? CONTROL_HOVER : PANEL,
                    5);
            context.fill(
                    getX() + 5,
                    getY(),
                    getX() + getWidth() - 5,
                    getY() + 1,
                    hot ? 0x50C45CF0 : 0x14FFFFFF);
            avatar(context, entry.name(), getX() + 10, getY() + 10, 20);
            var font = Minecraft.getInstance().font;
            context.drawString(
                    font,
                    font.plainSubstrByWidth(entry.name(), getWidth() - 84),
                    getX() + 40,
                    getY() + 9,
                    TEXT,
                    false);
            rounded(context, getX() + 40, getY() + 25, 3, 3, GOOD, 1);
            context.drawString(font, text("ready"), getX() + 48, getY() + 23, MUTED, false);
            rounded(
                    context,
                    getX() + getWidth() - 30,
                    getY() + 10,
                    20,
                    20,
                    hot ? ACCENT : CONTROL,
                    4);
            context.drawString(
                    font,
                    "›",
                    getX() + getWidth() - 22,
                    getY() + 15,
                    hot ? ON_ACCENT : MUTED,
                    false);
        }

        @Override
        public void onClick(double x, double y) {
            invite.run();
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput builder) {
            defaultButtonNarrationText(builder);
        }
    }
}
