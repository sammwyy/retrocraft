package com.sammwy.retrocraft.client.ui;

import com.sammwy.retrocraft.compat.ClientGraphics;
import com.sammwy.retrocraft.compat.UiPose;
import com.sammwy.retrocraft.cores.transfer.Downloads;
import com.sammwy.retrocraft.platform.ClientPlatform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Small side cards also render above open console/settings screens. */
public final class DownloadHud {
    public static Downloads DOWNLOADS = new Downloads();
    private static final DownloadNotifications NOTIFICATIONS = new DownloadNotifications();

    public static void register() {
        ClientPlatform.hud(DownloadHud::render);
    }

    private static void render(GuiGraphics context) {
        var client = Minecraft.getInstance();
        var entries = DOWNLOADS.entries();
        long now = System.nanoTime();
        int width = Math.min(168, context.guiWidth() - 16);
        int x = context.guiWidth() - width - 8;
        int count =
                Math.min(
                        client.screen == null ? 2 : 1,
                        Math.max(1, (context.guiHeight() - 32) / 60));
        var cards = NOTIFICATIONS.update(entries, count, now);
        if (cards.isEmpty()) return;
        var tr = client.font;
        UiPose.of(context).pushPose();
        UiPose.of(context).translate(0, 0, 400);
        for (var card : cards) {
            var entry = card.entry;
            int y = 8 + card.slot * 60;
            float visibility = card.motion.value(now);
            UiPose.of(context).pushPose();
            UiPose.of(context).translate((width + 12) * (1f - visibility), 0, 0);
            ClientGraphics.color(context, 1, 1, 1, visibility);
            boolean failed = entry.state() == Downloads.State.FAILED;
            int accent =
                    failed
                            ? SettingsWidgets.BAD
                            : entry.state() == Downloads.State.COMPLETE
                                    ? SettingsWidgets.GOOD
                                    : SettingsWidgets.ACCENT;
            SettingsWidgets.rounded(context, x, y + 2, width, 54, 0x40000000, 4);
            SettingsWidgets.rounded(context, x, y, width, 54, 0xF21E1930, 4);
            String status =
                    entry.state() == Downloads.State.DOWNLOADING
                            ? "Downloading " + entry.kind()
                            : failed ? entry.kind() + " failed" : entry.kind() + " ready";
            String percent =
                    failed
                            ? "!"
                            : entry.state() == Downloads.State.COMPLETE
                                    ? "100%"
                                    : entry.percent() < 0 ? "…" : entry.percent() + "%";
            context.drawString(
                    tr,
                    Component.literal(tr.plainSubstrByWidth(status, width - 53)),
                    x + 10,
                    y + 7,
                    SettingsWidgets.TEXT,
                    false);
            context.drawString(
                    tr, percent, x + width - 9 - tr.width(percent), y + 7, accent, false);
            context.drawString(
                    tr,
                    tr.plainSubstrByWidth(entry.name(), width - 20),
                    x + 10,
                    y + 19,
                    SettingsWidgets.MUTED,
                    false);
            if (entry.state() == Downloads.State.DOWNLOADING) {
                context.fill(x + 10, y + 32, x + width - 10, y + 35, SettingsWidgets.EDGE);
                int track = width - 20;
                if (entry.percent() >= 0)
                    context.fill(
                            x + 10, y + 32, x + 10 + track * entry.percent() / 100, y + 35, accent);
                else {
                    int segment = Math.max(1, track / 4);
                    int offset =
                            Math.round(
                                    (track - segment)
                                            * (1f - (float) Math.cos(now / 650_000_000d))
                                            / 2f);
                    context.fill(
                            x + 10 + offset, y + 32, x + 10 + offset + segment, y + 35, accent);
                }
            }
            context.drawString(
                    tr,
                    tr.plainSubstrByWidth(entry.size(), width - 20),
                    x + 10,
                    y + 40,
                    SettingsWidgets.MUTED,
                    false);
            ClientGraphics.color(context, 1, 1, 1, 1);
            UiPose.of(context).popPose();
        }
        if (entries.size() > count)
            context.drawString(
                    tr,
                    "+" + (entries.size() - count) + " downloads",
                    x + 10,
                    8 + count * 60,
                    SettingsWidgets.MUTED,
                    true);
        UiPose.of(context).popPose();
    }

    private DownloadHud() {}
}
