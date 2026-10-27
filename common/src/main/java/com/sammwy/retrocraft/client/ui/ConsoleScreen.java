package com.sammwy.retrocraft.client.ui;

import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.client.ClientPreferences;
import com.sammwy.retrocraft.client.InputControls;
import com.sammwy.retrocraft.client.RetrocraftClient;
import com.sammwy.retrocraft.client.config.DualScreenLayout;
import com.sammwy.retrocraft.client.render.CameraRig;
import com.sammwy.retrocraft.client.render.ScreenProjection;
import com.sammwy.retrocraft.client.render.ScreenTextures;
import com.sammwy.retrocraft.client.ui.hud.*;
import com.sammwy.retrocraft.compat.ClientGraphics;
import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.cores.worker.PadInput;
import com.sammwy.retrocraft.platform.ClientNetwork;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.server.console.ConsoleDefinition;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.console.ConsoleType;
import com.sammwy.retrocraft.server.net.LinkPackets;
import com.sammwy.retrocraft.server.net.Packets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class ConsoleScreen extends AnimatedScreen {
    private static final int HEADER = 18, MARGIN = 8;
    public final UUID instance;
    private final ConsoleTheme theme;
    private final ResourceLocation definitionId;
    private final ConsoleDefinition definition;
    private final Component consoleName, displayTitle;
    private boolean controller, canManage, inWorld, subscribed = true;
    private long screen;
    private ResourceLocation textureId;
    private int frameWidth = 256, frameHeight = 240;
    private final ItemStack consoleStack;
    private boolean hudWasHidden, hudHiddenByUs;
    private IconButton zoomOut, zoomIn;
    private final ConsoleHudRenderer hudRenderer;
    private ConsoleHudLayout hudLayout;
    private DualScreenLayout layout;
    private boolean touchHeld;
    private double touchX, touchY;

    /** A light gun is plugged in: the mouse aims at the picture and the left button fires. */
    private boolean aims;

    private HudRect flatVideo;
    private LinkDialog linkDialog;
    private boolean linkConnected;

    public void link(LinkPackets.State state) {
        if (!instance.equals(state.instance())) return;
        if (state.mode() == LinkPackets.CLOSED) {
            if (state.token().equals(LinkPackets.NONE)) {
                linkConnected = false;
                if (linkDialog != null) linkDialog.serverClosed();
                rebuildWidgets();
            } else if (linkDialog != null && linkDialog.token().equals(state.token()))
                linkDialog.serverClosed();
            return;
        }
        if (state.mode() == LinkPackets.CONNECTED) {
            linkConnected = true;
            rebuildWidgets();
        }
        linkDialog = new LinkDialog(state, () -> linkDialog = null);
    }

    private boolean hasLinkPort() {
        return definition != null
                && definition.componentSlots().stream()
                        .anyMatch(
                                slot ->
                                        slot.accepts(
                                                com.sammwy.retrocraft.server.console.component
                                                        .Components.GB_LINK_CABLE));
    }

    private void renderLink(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (linkDialog != null)
            linkDialog.render(context, font, width, height, mouseX, mouseY, delta);
    }

    public ConsoleScreen(Packets.Ui packet) {
        super(Component.literal(packet.title()));
        instance = packet.instance();
        controller = packet.controller();
        canManage = packet.canManage();
        inWorld = packet.inWorld();
        screen = packet.screen();
        aims = packet.aims();
        definitionId =
                Ids.parse(
                        packet.console().contains(":")
                                ? packet.console()
                                : "retrocraft:" + packet.console());
        theme = ConsoleTheme.of(definitionId.getPath());
        definition = ConsoleRegistry.get(definitionId);
        hudRenderer =
                definition != null && definition.type() == ConsoleType.HANDHELD
                        ? ConsoleHudRegistry.require(definitionId)
                        : null;
        if (hudRenderer != null) {
            var config = hudRenderer.config();
            frameWidth = config.nativeWidth();
            frameHeight = config.nativeHeight();
            layout =
                    config.dualScreen() == null
                            ? DualScreenLayout.VERTICAL
                            : ClientPreferences.get().hudLayout(config.layoutPreference());
        }
        consoleName =
                Component.translatable(
                        "console." + definitionId.getNamespace() + "." + definitionId.getPath());
        displayTitle = Component.empty().append(consoleName).append(" · ").append(title);
        consoleStack = new ItemStack(ContentProperties.get(BuiltInRegistries.ITEM, definitionId));
    }

    public String consoleId() {
        return theme.id();
    }

    public void update(Packets.Ui packet) {
        boolean changed = controller != packet.controller() || canManage != packet.canManage();
        controller = packet.controller();
        canManage = packet.canManage();
        inWorld = packet.inWorld();
        screen = packet.screen();
        aims = packet.aims();
        if (changed) rebuildWidgets();
    }

    @Override
    protected void init() {
        touchHeld = false;
        hudLayout = null;
        if (minecraft != null && !hudHiddenByUs) {
            hudWasHidden = com.sammwy.retrocraft.compat.ClientHud.hidden(minecraft);
            com.sammwy.retrocraft.compat.ClientHud.hidden(minecraft, true);
            hudHiddenByUs = true;
        }
        boolean focused = focusActive();
        if (focused) CameraRig.focus(BlockPos.of(screen));
        // Miniature actions in the top-right corner; Escape closes the screen.
        int x = width - MARGIN;
        zoomOut = zoomIn = null;
        if (canManage) {
            x -= 14;
            addRenderableWidget(
                    new IconButton(
                            x,
                            4,
                            IconButton.Icon.POWER,
                            b ->
                                    ClientNetwork.send(
                                            Packets.Request.of(Packets.POWER_OFF, instance)),
                            Component.translatable("screen.retrocraft.power_off")));
            if (definition == null || !definition.internalRom()) {
                x -= 16;
                addRenderableWidget(
                        new IconButton(
                                x,
                                4,
                                IconButton.Icon.EJECT,
                                b ->
                                        ClientNetwork.send(
                                                Packets.Request.of(Packets.EJECT, instance)),
                                Component.translatable("screen.retrocraft.eject")));
            }
        }
        if (canManage && hasLinkPort()) {
            x -= 90;
            addRenderableWidget(
                    new SettingsWidgets.Flat(
                                    x,
                                    4,
                                    86,
                                    14,
                                    Component.translatable(
                                            linkConnected
                                                    ? "screen.retrocraft.link.connected"
                                                    : "screen.retrocraft.link.title"),
                                    () ->
                                            ClientNetwork.send(
                                                    new LinkPackets.Request(
                                                            LinkPackets.LIST,
                                                            instance,
                                                            LinkPackets.NONE)))
                            .accent(linkConnected));
        }
        if (hasDualScreens()) {
            x -= 132;
            addRenderableWidget(
                    new SettingsWidgets.Flat(
                            x,
                            4,
                            128,
                            14,
                            Component.translatable("screen.retrocraft.layout." + layout.id()),
                            () -> {
                                touchHeld = false;
                                layout = layout.next();
                                try {
                                    ClientPreferences.applyHudLayout(
                                            hudRenderer.config().layoutPreference(), layout);
                                } catch (java.io.IOException ex) {
                                    Retrocraft.LOG.warn("Cannot save screen layout", ex);
                                }
                                rebuildWidgets();
                            }));
        }
        if (focused) {
            // Camera zoom group: [-] pct [+], sitting left of the power/eject cluster.
            x -= 16;
            zoomIn =
                    addRenderableWidget(
                            new IconButton(
                                    x,
                                    4,
                                    IconButton.Icon.ZOOM_IN,
                                    b -> CameraRig.zoomStep(1),
                                    Component.translatable("screen.retrocraft.zoom_in")));
            x -= 38;
            zoomOut =
                    addRenderableWidget(
                            new IconButton(
                                    x,
                                    4,
                                    IconButton.Icon.ZOOM_OUT,
                                    b -> CameraRig.zoomStep(-1),
                                    Component.translatable("screen.retrocraft.zoom_out")));
            x -= 2;
        }
        if (!controller) {
            x -= 74;
            addRenderableWidget(
                    new SettingsWidgets.Flat(
                            x,
                            4,
                            70,
                            14,
                            Component.translatable("screen.retrocraft.take_control"),
                            () ->
                                    ClientNetwork.send(
                                            Packets.Request.of(Packets.CLAIM_INPUT, instance))));
        }
    }

    /** In-world screen being framed by the camera rig — where zooming applies. */
    private boolean focusActive() {
        return inWorld
                && ClientPreferences.get().cameraFocus
                && ClientPreferences.get().renderWorldScreens;
    }

    @Override
    public void tick() {
        if (!subscribed || !ClientNetwork.canSend(Packets.Request.ID)) return;
        var input =
                controller && linkDialog == null ? InputControls.sample(minecraft) : PadInput.IDLE;
        ClientNetwork.send(
                new Packets.Request(
                        Packets.INPUT, instance, input, RetrocraftClient.VIDEO.sequence(instance)));
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (closing()) return true;
        if (linkDialog != null) {
            linkDialog.keyPressed(key, modifiers);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (com.sammwy.retrocraft.compat.ClientInput.matches(
                RetrocraftClient.SETTINGS_KEY, key, scanCode)) {
            transitionTo(SettingsScreen.modMenu(null));
            return true;
        }
        if (focusActive()) {
            if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
                CameraRig.zoomStep(1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
                CameraRig.zoomStep(-1);
                return true;
            }
        }
        // Game input must not activate focused GUI buttons (Enter/Space).
        return true;
    }

    @Override
    protected void renderContents(GuiGraphics context, int mouseX, int mouseY, float delta) {
        var entry = ScreenTextures.get(instance);
        textureId = entry == null ? null : entry.id();
        if (entry != null) {
            frameWidth = entry.width();
            frameHeight = entry.height();
        }
        if (hudRenderer == null && inWorld && ClientPreferences.get().renderWorldScreens) {
            // The cabinet block shows the video; only the key legend and the miniature actions are
            // drawn.
            context.renderItem(consoleStack, MARGIN, 3);
            context.drawString(
                    font,
                    font.plainSubstrByWidth(
                            displayTitle.getString(),
                            Math.max(0, width - 2 * MARGIN - 20 - titleReserve())),
                    MARGIN + 20,
                    7,
                    0xffe6e9f0);
            if (!controller)
                context.drawString(
                        font,
                        Component.translatable("screen.retrocraft.spectator"),
                        MARGIN + 20,
                        17,
                        0xffa8b0c0);
            if (textureId == null)
                context.drawCenteredString(
                        font,
                        Component.translatable("screen.retrocraft.connecting"),
                        width / 2,
                        height - PlayerHud.reservedHeight(theme) - 10,
                        0xffd8dde6);
            PlayerHud.render(context, font, minecraft, theme, width, height, instance);
            zoomLabel(context);
            renderWidgets(context, mouseX, mouseY, delta);
            drawSight(context, mouseX, mouseY);
            renderLink(context, mouseX, mouseY, delta);
            return;
        }
        context.renderItem(consoleStack, MARGIN, 3);
        context.drawString(
                font,
                font.plainSubstrByWidth(
                        displayTitle.getString(),
                        Math.max(0, width - 2 * MARGIN - 20 - titleReserve())),
                MARGIN + 20,
                7,
                0xffe6e9f0);
        if (!controller)
            context.drawString(
                    font,
                    Component.translatable("screen.retrocraft.spectator"),
                    MARGIN + 20,
                    17,
                    0xffa8b0c0);

        if (hudRenderer != null) {
            var runtime = hudContext(mouseX, mouseY, delta);
            hudLayout = hudRenderer.render(context, runtime, panel -> drawHudVideo(context, panel));
            if (controller && hudLayout.touchAt(mouseX, mouseY).isPresent()) {
                int color = touchHeld ? 0xff72e5d4 : 0xffe9eef2;
                context.fill(mouseX - 3, mouseY, mouseX + 4, mouseY + 1, color);
                context.fill(mouseX, mouseY - 3, mouseX + 1, mouseY + 4, color);
            }
        } else {
            // Flat-view fallback when world screens are disabled. TV and arcade renderers stay
            // separate.
            var area = hudViewport();
            var bezel = WorldConsoleFrame.insets(theme);
            int availableW = Math.max(1, area.width() - bezel.left() - bezel.right());
            int availableH = Math.max(1, area.height() - bezel.top() - bezel.bottom());
            double fit =
                    Math.min((double) availableW / frameWidth, (double) availableH / frameHeight);
            int w = Math.max(1, (int) Math.floor(frameWidth * fit));
            int h = Math.max(1, (int) Math.floor(frameHeight * fit));
            var video =
                    new HudRect(
                            area.x() + bezel.left() + (availableW - w) / 2,
                            area.y() + bezel.top() + (availableH - h) / 2,
                            w,
                            h);
            flatVideo = video;
            WorldConsoleFrame.render(context, theme, video);
            drawHudVideo(
                    context,
                    new ConsoleHudLayout.Panel(
                            video,
                            video,
                            new com.sammwy.retrocraft.cores.stream.DualScreen.Crop(0, 0, 1, 1),
                            false));
        }
        PlayerHud.render(context, font, minecraft, theme, width, height, instance);
        zoomLabel(context);
        renderWidgets(context, mouseX, mouseY, delta);
        drawSight(context, mouseX, mouseY);
        renderLink(context, mouseX, mouseY, delta);
    }

    /** On-screen picture a light gun aims at: projected TV/cabinet glass or the flat view. */
    private HudRect aimTarget() {
        if (!aims || hudRenderer != null || !controller) return null;
        return inWorld && ClientPreferences.get().renderWorldScreens
                ? ScreenProjection.get(textureId)
                : flatVideo;
    }

    private static boolean inside(HudRect rect, double x, double y) {
        return rect != null
                && x >= rect.x()
                && y >= rect.y()
                && x < rect.x() + rect.width()
                && y < rect.y() + rect.height();
    }

    private void drawSight(GuiGraphics context, int mouseX, int mouseY) {
        if (!inside(aimTarget(), mouseX, mouseY)) return;
        int color = touchHeld ? 0xffff5a4a : 0xffe9eef2;
        context.fill(mouseX - 5, mouseY, mouseX - 1, mouseY + 1, color);
        context.fill(mouseX + 2, mouseY, mouseX + 6, mouseY + 1, color);
        context.fill(mouseX, mouseY - 5, mouseX + 1, mouseY - 1, color);
        context.fill(mouseX, mouseY + 2, mouseX + 1, mouseY + 6, color);
    }

    private PadInput withAim(PadInput input) {
        HudRect target = aimTarget();
        if (target == null) return input;
        double scale =
                (double) minecraft.getWindow().getGuiScaledWidth()
                        / minecraft.getWindow().getScreenWidth();
        double x = minecraft.mouseHandler.xpos() * scale, y = minecraft.mouseHandler.ypos() * scale;
        boolean firing =
                GLFW.glfwGetMouseButton(
                                com.sammwy.retrocraft.compat.ClientInput.window(
                                        minecraft.getWindow()),
                                GLFW.GLFW_MOUSE_BUTTON_LEFT)
                        == GLFW.GLFW_PRESS;
        touchHeld = firing && inside(target, x, y);
        if (!inside(target, x, y)) return input;
        return input.withTouch(
                pointer((x - target.x()) / target.width()),
                pointer((y - target.y()) / target.height()),
                firing);
    }

    private static short pointer(double fraction) {
        double value = Math.max(0, Math.min(1, fraction)) * 65534 - 32767;
        return (short) Math.round(value);
    }

    private boolean hasDualScreens() {
        return hudRenderer != null && hudRenderer.config().dualScreen() != null;
    }

    private HudRect hudViewport() {
        int top = HEADER + MARGIN;
        return new HudRect(
                MARGIN,
                top,
                Math.max(1, width - 2 * MARGIN),
                Math.max(1, height - PlayerHud.reservedHeight(theme) - MARGIN - top));
    }

    private ConsoleHudContext hudContext(double mouseX, double mouseY, float delta) {
        return new ConsoleHudContext(
                instance,
                definitionId.toString(),
                hudViewport(),
                frameWidth,
                frameHeight,
                layout == null ? DualScreenLayout.VERTICAL : layout,
                controller,
                mouseX,
                mouseY,
                delta);
    }

    private ConsoleHudLayout currentHudLayout() {
        if (hudLayout == null && hudRenderer != null)
            hudLayout = hudRenderer.layout(hudContext(touchX, touchY, 0));
        return hudLayout;
    }

    private void drawHudVideo(GuiGraphics context, ConsoleHudLayout.Panel panel) {
        var rect = panel.video();
        var crop = panel.crop();
        int x = rect.x(), y = rect.y(), w = rect.width(), h = rect.height();
        if (textureId != null) {
            int sw = Math.max(1, Math.round((crop.u1() - crop.u0()) * frameWidth));
            int sh = Math.max(1, Math.round((crop.v1() - crop.v0()) * frameHeight));
            ClientGraphics.blit(
                    context,
                    textureId,
                    x,
                    y,
                    w,
                    h,
                    crop.u0() * frameWidth,
                    crop.v0() * frameHeight,
                    sw,
                    sh,
                    frameWidth,
                    frameHeight);
        } else {
            context.fill(
                    x,
                    y,
                    x + w,
                    y + h,
                    hudRenderer == null ? theme.screenOff() : hudRenderer.config().screenOff());
            if (crop.v0() == 0)
                context.drawCenteredString(
                        font,
                        Component.translatable("screen.retrocraft.connecting"),
                        x + w / 2,
                        y + h / 2 - 4,
                        0xffd8dde6);
        }
    }

    public PadInput withTouch(PadInput input) {
        if (linkDialog != null) return PadInput.IDLE;
        if (hudRenderer == null && controller && acceptingInput() && minecraft.isWindowActive())
            return withAim(input);
        if (hudRenderer == null
                || !controller
                || !touchHeld
                || !acceptingInput()
                || !minecraft.isWindowActive()) return input;
        if (GLFW.glfwGetMouseButton(
                        com.sammwy.retrocraft.compat.ClientInput.window(minecraft.getWindow()),
                        GLFW.GLFW_MOUSE_BUTTON_LEFT)
                != GLFW.GLFW_PRESS) {
            touchHeld = false;
            return input;
        }
        var panel = currentHudLayout().touchAt(touchX, touchY).orElse(null);
        if (panel == null) return input;
        return input.withTouch(panel.pointerX(touchX), panel.pointerY(touchY), true);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (linkDialog != null) {
            linkDialog.click(x, y, button);
            return true;
        }
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 0 && acceptingInput() && inside(aimTarget(), x, y)) return true;
        if (hudRenderer != null
                && button == 0
                && controller
                && acceptingInput()
                && currentHudLayout().touchAt(x, y).isPresent()) {
            touchHeld = true;
            touchX = x;
            touchY = y;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (linkDialog != null) {
            linkDialog.scroll(vertical);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (linkDialog != null) return true;
        if (touchHeld && button == 0) {
            touchX = x;
            touchY = y;
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (linkDialog != null) return true;
        if (button == 0 && touchHeld) {
            touchHeld = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    /** Pixels the top-right button cluster needs, so the title never runs underneath it. */
    private int titleReserve() {
        return (canManage ? 32 : 0)
                + (controller ? 0 : 74)
                + (focusActive() ? 56 : 0)
                + (hasDualScreens() ? 132 : 0)
                + (canManage && hasLinkPort() ? 90 : 0);
    }

    /** Zoom percentage between the [-] and [+] buttons, matching the header text. */
    private void zoomLabel(GuiGraphics context) {
        if (zoomOut == null || zoomIn == null) return;
        int cx = (zoomOut.getX() + zoomOut.getWidth() + zoomIn.getX()) / 2;
        context.drawCenteredString(
                font, Math.round(CameraRig.zoom() * 100) + "%", cx, 7, 0xffe6e9f0);
    }

    @Override
    protected void renderDimmer(GuiGraphics context, float visibility) {
        if (!(hudRenderer == null && inWorld && ClientPreferences.get().renderWorldScreens))
            context.fill(0, 0, width, height, (int) (112 * visibility) << 24);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {}

    public void serverClosed() {
        subscribed = false;
        onClose();
    }

    @Override
    public void removed() {
        if (hudHiddenByUs && minecraft != null) {
            com.sammwy.retrocraft.compat.ClientHud.hidden(minecraft, hudWasHidden);
            hudHiddenByUs = false;
        }
        CameraRig.release();
        PlayerHud.drop(instance);
        if (RetrocraftClient.EMULATION != null) RetrocraftClient.EMULATION.release(instance);
        if (subscribed && ClientNetwork.canSend(Packets.Request.ID))
            ClientNetwork.send(Packets.Request.of(Packets.CLOSE, instance));
        subscribed = false;
        super.removed();
    }
}
