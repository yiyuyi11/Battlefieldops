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
            int y = anchorY + Feed.offsetY(p, now, i);
            // White skull for a kill, red skull for a headshot, amber for every other award.
            FeedIcons.draw(g, p.icon(), anchorX, y, argb(baseColor(p.icon()), alpha));
            int textX = anchorX + FeedIcons.width(p.icon()) + 3;
            // Shadowed near-white text: clear on bright ground or sky without a backing panel.
            g.drawString(mc.font, p.text(), textX, y, argb(0xffeaf2f5, alpha), true);
            String detail = p.detailText();
            if (!detail.isEmpty()) {
                int detailX = textX + mc.font.width(p.text()) + 5;
                int room = w - detailX - 6;
                if (room > 12)
                    g.drawString(mc.font, Ui.clip(mc.font, detail, room), detailX, y,
                            argb(0xff9fb2ba, alpha), true);
            }
        }
    }

    private static int baseColor(String icon) {
        return switch (icon == null ? "" : icon) {
            case "headshot" -> 0xffff5c68;
            case "kill" -> 0xffffffff;
            default -> 0xffffd479;
        };
    }

    private static int argb(int color, double alpha) {
        return ((int) (255 * Math.max(0, Math.min(1, alpha))) << 24) | (color & 0xffffff);
    }
}
