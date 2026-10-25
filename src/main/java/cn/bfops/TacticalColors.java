package cn.bfops;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Colours and markers shared by the minimap and the map pickers, so a contact looks the same everywhere it is
 * drawn.
 *
 * <p>The marker glyphs are deliberately shaped differently from each other rather than only
 * colour-differentiated: red/green is the most common form of colour blindness, and a map where friend and
 * foe differ only by hue is unreadable for those players. A filled arrow, a smaller filled arrow and a dot
 * survive both colour blindness and a low-contrast screen.
 *
 * <p>The shapes are drawn with integer fills rather than icons because they have to be small: a 5px arrow made
 * from a texture gets cropped into an unrecognisable blob, whereas a hand-built triangle of a few fills still
 * reads as a heading.
 */
final class TacticalColors {
    /** Cyan-green, distinct from the teammate blue at a glance and from terrain colours. */
    static final int SELF = 0xff36e0b4, FRIEND = 0xff619fea, ENEMY = 0xffe96772, NEUTRAL = 0xffa3b3bd;
    /** The view cone is translucent on purpose: it has to overlay terrain without hiding it. */
    static final int VIEW_CONE = 0x4036e0b4;
    static final int VIEW_EDGE = 0x8036e0b4;
    /** Marks are a ring, so they cannot be confused with the red dot of a live contact. */
    static final int MARK = 0xffffb347;

    private TacticalColors() { }

    static void label(GuiGraphics g, Font font, String text, int centerX, int y, int color) {
        int half = font.width(text) / 2;
        g.fill(centerX - half - 2, y - 1, centerX + half + 3, y + 9, 0xe0000000);
        g.drawCenteredString(font, text, centerX, y, color);
    }

    static int objective(int owner, int self) {
        return owner < 0 ? NEUTRAL
                : self < 0 ? Minimap.color(owner) | 0xff000000
                : owner == self ? FRIEND : ENEMY;
    }

    /**
     * A contact marker centred on {@code x,y}.
     *
     * <p>Drawn through {@link UiIcons#marker} so the glyph lands on whole pixels. The size is 11px, which the
     * icon sheet serves by cropping rather than scaling — see {@code UiIcons} for why.
     */
    static void person(GuiGraphics g, int x, int y, boolean hostile) {
        UiIcons.marker(g, hostile ? UiIcons.MARKER_HOSTILE : UiIcons.MARKER_FRIEND, x, y, 11);
    }

    /** Marker for a vehicle, distinguished from infantry by shape so the two do not read alike. */
    static void vehicle(GuiGraphics g, int x, int y, boolean air, int color) {
        UiIcons.marker(g, air ? UiIcons.VEHICLE_AIR : UiIcons.VEHICLE_GROUND, x, y, 11);
    }

    /**
     * A filled heading arrow.
     *
     * <p>Used for the player and for teammates, so a squad's facing is readable at a glance — which is the
     * information that decides whether to push with them or wait.
     *
     * @param yawDegrees Minecraft yaw: 0 looks towards +Z, increasing yaw turns towards -X
     * @param size       tip length in pixels; the tail is a little over half of it
     */
    static void arrow(GuiGraphics g, int x, int y, float yawDegrees, int size, int color) {
        double heading = Math.toRadians(yawDegrees);
        double forwardX = -Math.sin(heading), forwardZ = Math.cos(heading);
        int tipX = x + (int) Math.round(forwardX * size);
        int tipY = y + (int) Math.round(forwardZ * size);
        int tail = Math.max(1, size - 1);
        // Perpendicular, for the two tail corners.
        int backX = x - (int) Math.round(forwardX * size * 0.6);
        int backY = y - (int) Math.round(forwardZ * size * 0.6);
        int leftX = backX + (int) Math.round(forwardZ * tail * 0.7);
        int leftY = backY - (int) Math.round(forwardX * tail * 0.7);
        int rightX = backX - (int) Math.round(forwardZ * tail * 0.7);
        int rightY = backY + (int) Math.round(forwardX * tail * 0.7);
        triangle(g, tipX, tipY, leftX, leftY, rightX, rightY, color);
    }

    /**
     * Fills a triangle by scanline.
     *
     * <p>Cheaper and crisper than drawing its outline and filling a bounding box: the arrow has to sit on the
     * same pixels as everything else on the map, and a bounding-box fill would paint over neighbours.
     */
    static void triangle(GuiGraphics g, int x0, int y0, int x1, int y1, int x2, int y2, int color) {
        int top = Math.min(y0, Math.min(y1, y2));
        int bottom = Math.max(y0, Math.max(y1, y2));
        if (bottom - top > 64) return;
        int[] xs = {x0, x1, x2};
        int[] ys = {y0, y1, y2};
        for (int y = top; y <= bottom; y++) {
            int left = Integer.MAX_VALUE, right = Integer.MIN_VALUE;
            for (int i = 0; i < 3; i++) {
                int j = (i + 1) % 3;
                int ya = ys[i], yb = ys[j];
                if (ya == yb) {
                    // Horizontal edge: both ends contribute.
                    if (y == ya) {
                        left = Math.min(left, Math.min(xs[i], xs[j]));
                        right = Math.max(right, Math.max(xs[i], xs[j]));
                    }
                    continue;
                }
                if (y < Math.min(ya, yb) || y > Math.max(ya, yb)) continue;
                double t = (y - ya) / (double) (yb - ya);
                int cross = (int) Math.round(xs[i] + (xs[j] - xs[i]) * t);
                left = Math.min(left, cross);
                right = Math.max(right, cross);
            }
            if (right < left) continue;
            g.fill(left, y, right + 1, y + 1, color);
        }
    }

    /** A small filled square standing in for an enemy dot. */
    static void dot(GuiGraphics g, int x, int y, int radius, int color) {
        int r = Math.max(0, radius);
        g.fill(x - r, y - r, x + r + 1, y + r + 1, color);
    }

    /**
     * The player's view cone, drawn under the markers.
     *
     * <p>Shows what the player is actually looking at on the map, which makes the spotting rule legible: a
     * contact that appears without being in somebody's cone looks like a bug, and now it visibly is not.
     *
     * @param halfAngleDegrees half the horizontal field of view to draw
     * @param radiusPixels     how far the cone reaches on screen
     */
    static void viewCone(GuiGraphics g, int x, int y, float yawDegrees, double halfAngleDegrees,
                         int radiusPixels, int fill, int edge) {
        if (radiusPixels <= 2) return;
        double heading = Math.toRadians(yawDegrees);
        int steps = 14;
        int previousX = 0, previousY = 0;
        for (int i = 0; i <= steps; i++) {
            double offset = Math.toRadians(-halfAngleDegrees)
                    + Math.toRadians(halfAngleDegrees * 2) * i / steps;
            double angle = heading + offset;
            int px = x + (int) Math.round(-Math.sin(angle) * radiusPixels);
            int py = y + (int) Math.round(Math.cos(angle) * radiusPixels);
            if (i > 0) triangle(g, x, y, previousX, previousY, px, py, fill);
            previousX = px;
            previousY = py;
        }
        // A faint outline so the cone's edge is readable over light terrain.
        double leftAngle = heading - Math.toRadians(halfAngleDegrees);
        double rightAngle = heading + Math.toRadians(halfAngleDegrees);
        Ui.line(g, x, y, x + (int) Math.round(-Math.sin(leftAngle) * radiusPixels),
                y + (int) Math.round(Math.cos(leftAngle) * radiusPixels), edge, 1);
        Ui.line(g, x, y, x + (int) Math.round(-Math.sin(rightAngle) * radiusPixels),
                y + (int) Math.round(Math.cos(rightAngle) * radiusPixels), edge, 1);
    }

    /** A manual mark: a ring with a cross, drawn where the team last called out a contact. */
    static void mark(GuiGraphics g, int x, int y, int radius, int color) {
        int r = Math.max(2, radius);
        for (int i = 0; i < 12; i++) {
            double a0 = i * Math.PI * 2 / 12, a1 = (i + 1) * Math.PI * 2 / 12;
            Ui.line(g, x + (int) Math.round(Math.cos(a0) * r), y + (int) Math.round(Math.sin(a0) * r),
                    x + (int) Math.round(Math.cos(a1) * r), y + (int) Math.round(Math.sin(a1) * r), color, 1);
        }
        g.fill(x - r - 2, y, x - r + 1, y + 1, color);
        g.fill(x + r, y, x + r + 3, y + 1, color);
        g.fill(x, y - r - 2, x + 1, y - r + 1, color);
        g.fill(x, y + r, x + 1, y + r + 3, color);
    }
}
