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
    /** The player's own marker is a yellow dot, matching Battlefield's "you are here" reading. */
    static final int SELF = 0xffffd23f, FRIEND = 0xff619fea, ENEMY = 0xffe96772, NEUTRAL = 0xffa3b3bd;
    /**
     * Your own class, on a teammate marker.
     *
     * <p>The reference pack colours the squad variant green (110,251,145) and the team variant blue. Green is
     * out of this mod's agreed palette, so the distinction is kept — it is genuinely useful, "is that person
     * running my kit" — and carried in cyan instead: near enough to the squad reading, and still separated
     * from `FRIEND` blue and from the cyan-green that marks the player themselves.
     */
    static final int MATE = 0xff6efb91;
    /**
     * A downed teammate's label.
     *
     * <p>Deliberately <em>not</em> used for the marker glyph: the reference pack draws the death glyph in the
     * same team/squad colour as the living one, so shape carries "downed" and colour stays reserved for the
     * relationship. That is the rule this file already follows elsewhere, and it keeps the palette at cyan,
     * blue and red as agreed. The amber is only for the text under the glyph, where the extra urgency is
     * worth one hue.
     */
    static final int DOWNED = 0xffffb347;
    /** The view cone is translucent on purpose: it has to overlay terrain without hiding it. */
    static final int VIEW_CONE = 0x40ffffff;
    static final int VIEW_EDGE = 0x80ffffff;
    /** Marks are a ring, so they cannot be confused with the red dot of a live contact. */
    static final int MARK = 0xffffb347;

    private TacticalColors() { }

    static void label(GuiGraphics g, Font font, String text, int centerX, int y, int color) {
        int alpha = color >>> 24;
        if(alpha < 4 || text == null || text.isEmpty())return;
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);
        int half = font.width(text) / 2;
        // A translucent charcoal backing fades with the label instead of becoming an opaque black block.
        g.fill(centerX - half - 2, y - 1, centerX + half + 3, y + 9, HudLabelStyle.backing(color));
        g.drawCenteredString(font, text, centerX, y, color);
        g.flush();
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

    /**
     * A manual mark: an inverted triangle for an enemy, the friendly blue for a teammate.
     *
     * <p><b>Why an inverted triangle and not the ring this used to draw.</b> The ring was indistinguishable
     * from a capture point at map scale, and at eleven pixels a hollow shape is one pixel of stroke that
     * disappears over light terrain. A filled inverted triangle is the reference pack's own enemy-mark
     * shape, and it cannot be confused with anything else on this map: contacts are dots, teammates are
     * arrows and class glyphs, objectives are diamonds, vehicles are hollow squares.
     *
     * <p>The point aims down, which both reads as "here, precisely" and differs from the player's own
     * forward arrow even when the two overlap.
     *
     * @param kind  the mark's side, from the snapshot's {@code kind} field
     * @param alpha 0..1, for the fade as it expires
     */
    static void mark(GuiGraphics g, int x, int y, int radius, String kind, float alpha) {
        boolean mate = "MATE".equalsIgnoreCase(kind == null ? "" : kind);
        if (mate) {
            // A teammate mark is a soft blue halo rather than a hard triangle. The two marks mean different
            // things -- "enemy here" is a warning, "friendly here" is an invitation -- and the friendly one
            // should not compete for the same visual alarm. A halo also layers correctly under the teammate's
            // own contact marker, which the warning triangle would cover.
            halo(g, x, y, radius, withAlpha(FRIEND, alpha));
            return;
        }
        // Enemy: a filled inverted triangle, the reference pack's own enemy-mark shape.
        int color = withAlpha(ENEMY, alpha);
        triangle(g, x - radius, y - radius, x + radius + 1, y - radius, x, y + radius + 1, color);
        // A dark line along the wide edge: over bright terrain a flat fill loses its top border and the
        // triangle then reads as a shapeless wedge.
        g.fill(x - radius, y - radius - 1, x + radius + 1, y - radius, withAlpha(0xff0a1218, alpha * 0.8f));
    }

    /**
     * A soft ring of decreasing opacity, used for a teammate mark.
     *
     * <p>Three concentric one-pixel rings at falling alpha, rather than a blur: at map scale a real blur is
     * both expensive and invisible, while three rings read as a glow and cost three circle passes. The
     * outermost is faint enough not to hide the terrain it sits on.
     */
    static void halo(GuiGraphics g, int x, int y, int radius, int color) {
        int r = Math.max(2, radius);
        for (int ring = 0; ring < 3; ring++) {
            double scale = 1.0 + ring * 0.55;
            int rr = (int) Math.round(r * scale);
            int tint = withAlpha(color, (0.55f - ring * 0.15f) * ((color >>> 24) / 255f));
            for (int i = 0; i < 16; i++) {
                double a0 = i * Math.PI * 2 / 16, a1 = (i + 1) * Math.PI * 2 / 16;
                Ui.line(g, x + (int) Math.round(Math.cos(a0) * rr), y + (int) Math.round(Math.sin(a0) * rr),
                        x + (int) Math.round(Math.cos(a1) * rr), y + (int) Math.round(Math.sin(a1) * rr),
                        tint, 1);
            }
        }
    }

    /**
     * A manual mark: a ring with a cross, drawn where the team last called out a contact.
     *
     * @deprecated kept for the deploy view's spawn selection, which wants a locator ring rather than a
     *         side-coded marker. Use {@link #mark(GuiGraphics, int, int, int, String, float)} for contacts.
     */
    @Deprecated
    static void mark(GuiGraphics g, int x, int y, int radius, int color) {
        mark(g, x, y, radius, color, 1f);
    }

    /**
     * A locator ring with a cross, for "the thing you selected is here".
     *
     * <p>Deliberately not side-coded: this marks a place the player chose, not a person somebody reported.
     *
     * @param alpha 0..1, where 1 is fully opaque
     */
    static void mark(GuiGraphics g, int x, int y, int radius, int color, float alpha) {
        int faded = withAlpha(color, alpha);
        int r = Math.max(2, radius);
        for (int i = 0; i < 12; i++) {
            double a0 = i * Math.PI * 2 / 12, a1 = (i + 1) * Math.PI * 2 / 12;
            Ui.line(g, x + (int) Math.round(Math.cos(a0) * r), y + (int) Math.round(Math.sin(a0) * r),
                    x + (int) Math.round(Math.cos(a1) * r), y + (int) Math.round(Math.sin(a1) * r), faded, 1);
        }
        g.fill(x - r - 2, y, x - r + 1, y + 1, faded);
        g.fill(x + r, y, x + r + 3, y + 1, faded);
        g.fill(x, y - r - 2, x + 1, y - r + 1, faded);
        g.fill(x, y + r, x + 1, y + r + 3, faded);
    }

    /**
     * Replaces a colour's alpha channel.
     *
     * <p>Kept in one place because every marker colour here is stored with an opaque alpha, and a fade
     * that accidentally cleared the RGB bits instead would draw black glyphs. An
     * {@code & 0x00FFFFFF} written by hand at three call sites is three chances to write
     * {@code & 0xFFFFFF00} at one of them.
     */
    static int withAlpha(int argb, float alpha) {
        int a = (int) Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (argb & 0x00FFFFFF) | (a << 24);
    }
}
