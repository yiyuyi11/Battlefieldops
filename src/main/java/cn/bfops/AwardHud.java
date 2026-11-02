package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;

/**
 * Boxless, Battlefield-style kill feed: a small stack of shadowed text, no panel or frame.
 *
 * <p>The previous version drew one boxed "tactical card" per award and only drew when a whole card
 * could be placed without touching the minimap, hotbar or crosshair. On small or windowed GUIs that
 * placement often failed, so the feed silently never appeared. A text feed has no such constraint, so
 * it is drawn every frame at a fixed, clamped anchor — the windowed-mode bug is gone by construction.
 */
final class AwardHud {
    private static long lastFrame;

    /**
     * Icon box edge in GUI pixels.
     *
     * <p>18 rather than the old 11: the reference kill-icon art is 1024-2048px square, and below roughly
     * sixteen pixels a downsampled emblem loses its silhouette entirely. {@link KillIconRenderer} states the
     * threshold and falls back to the crisp hand-built bitmap when the box is smaller than it.
     */
    private static final int ICON_BOX = 18;

    /**
     * Vertical gap between feed rows.
     *
     * <p>Larger than {@link Feed#LINE_HEIGHT} because the icon box is now taller than a line of text: at the
     * old spacing an 18px icon would overlap the row above it on a three-kill burst.
     */
    private static final int ROW_HEIGHT = 20;

    static void draw(GuiGraphics g, long now, boolean vehicle, boolean scoped) {
        var mc = Minecraft.getInstance();
        long elapsed = lastFrame == 0 ? 0 : Math.max(0, Math.min(1000, now - lastFrame));
        lastFrame = now;

        // A kill line must never be drawn across a vehicle optic. Freeze the feed while scoped so a
        // kill made mid-scope is still there when the scope closes, instead of expiring unseen.
        if (scoped) {
            var held = new ArrayList<Feed.Popup>();
            for (var p : Client.awards)
                held.add(new Feed.Popup(p.label(), p.points(),
                        p.bornAt() + Math.min(elapsed, Math.max(0, now - p.bornAt())),
                        p.detail(), p.icon()));
            Client.awards = held;
            return;
        }

        Client.awards = Feed.prune(Client.awards, now);
        if (Client.awards.isEmpty()) return;

        int w = g.guiWidth(), h = g.guiHeight();
        // Right of centre, just below the crosshair: out of the crosshair's way, always on-screen.
        int anchorX = Math.max(8, Math.min(w - 96, w / 2 + 40));
        int anchorY = h / 2 + 26;
        for (int i = 0; i < Client.awards.size(); i++) {
            Feed.Popup p = Client.awards.get(i);
            double alpha = Feed.alpha(p, now);
            if (alpha <= 0.02) continue;
            // Stacked by the icon box rather than by Feed's text line height: the feed's own spacing
            // predates the icons and would let an 18px glyph overlap the row above it.
            int y = anchorY + Feed.offsetY(p, now, 0) + i * ROW_HEIGHT;

            // Entrance scale, from FeedAnim. Rounded to whole pixels because the text feed stacks in whole
            // pixels and a fractional origin would put every row on a half-pixel boundary. Only the newest
            // entry is usually still animating, and the call itself is two multiplications.
            int scale = (int) Math.round(FeedAnim.entranceScale(p.bornAt(), now));

            // Icon box: the pack's art is 256-2048px square and needs room to read as art rather than as a
            // smudge, so the feed's icon box is 18px rather than the old 11px bitmap.
            int iconBox = ICON_BOX;
            // The icon grows from its own centre, so a larger scale must also shift the origin or the glyph
            // would jump sideways on the frame the scale changes.
            int iconX = anchorX - (iconBox * (scale - 1)) / 2;
            int iconY = y - (iconBox * (scale - 1)) / 2;
            // The 656 icon for this award, falling back to the hand-built bitmap when the pack has no art for
            // it. `KillIcons` owns the mapping from an award name to a `KillType` and the texture lookup.
            int iconW = KillIcons.drawAward(g, p.icon(), iconX, iconY, iconBox, alpha);

            // The score counts up rather than appearing. FeedAnim explains why; the short version is that a
            // number arriving at its final value is a label, and one that moves is an event.
            String text = Feed.format(FeedAnim.rollingPoints(p.points(), p.bornAt(), now), p.label());
            // Right of the glyph, in its settled position rather than its scaled one: the text must not
            // jitter sideways while the icon animates above it.
            int textX = anchorX + Math.max(iconW, iconBox) + 4;
            // Shadowed near-white text: clear on bright ground or sky without a backing panel.
            g.drawString(mc.font, text, textX, y, argb(0xffeaf2f5, alpha), true);
            String detail = p.detailText();
            if (!detail.isEmpty()) {
                int detailX = textX + mc.font.width(text) + 5;
                int room = w - detailX - 6;
                if (room > 12)
                    g.drawString(mc.font, Ui.clip(mc.font, detail, room), detailX, y,
                            argb(0xff9fb2ba, alpha), true);
            }
        }
    }

    private static int argb(int color, double alpha) {
        return ((int) (255 * Math.max(0, Math.min(1, alpha))) << 24) | (color & 0xffffff);
    }
}
