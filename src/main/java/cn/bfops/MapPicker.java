package cn.bfops;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Locale;

/**
 * The one map widget every screen uses: real terrain, the arena overlay, and click-to-place.
 *
 * <p>Before this existed, three different things drew "a map": the line editor drew terrain plus
 * overlays by hand, the deploy preview drew a {@link Minimap} over a terrain blit, and everything else
 * drew only the {@link Minimap} schematic with no terrain at all. That is why some pages showed the
 * actual landscape and others showed a flat grid — and why the points and spawn pages, which need to
 * place things precisely, had only number boxes.
 *
 * <p>So the drawing is unified here: terrain is always underneath, the overlay stack is always the
 * same, and picking is available wherever it makes sense. Coordinates are converted with the exact
 * same projection that paints the markers, so a click lands on the pixel the operator aimed at.
 */
final class MapPicker {
    /** What a click is allowed to do. */
    enum Mode {
        /** No picking; the map is informational (stats, room list). */
        VIEW,
        /** Free placement: a new boundary vertex, a point, or a spawn. */
        PLACE,
        /** Pick an existing marker of the given kind. */
        PICK
    }

    /** What kind of marker the caller is placing or picking. */
    enum Kind { BOUNDARY, POINT, SPAWN }

    /** Result of a click, in world coordinates. */
    record Hit(Kind kind, int index, double x, double y, double z) {}

    private final Minecraft minecraft = Minecraft.getInstance();
    private TerrainPreview terrain;

    /** World centre and span, kept here so zoom/pan survives rebuilds. */
    double cx, cz, span = 256;

    /** Editor overlay toggles. */
    boolean showGrid = true;
    boolean showPoints = true;
    boolean showSpawns = true;
    boolean showPlayers = true;

    /** Index of the point / spawn / sector to ring in gold, or -1 for none. */
    int highlightPoint = -1;
    int highlightSpawn = -1;
    /** Index of the supply pad to ring in gold, or -1 for none. */
    int highlightSupply = -1;
    int highlightSector = -1;
    /** Team whose activity zone is being edited (-1 for none), so the canvas rings it in gold. */
    int highlightZone = -1;
    /** Polygon being drawn but not yet committed (the line editor's work in progress). */
    List<Model.Vec> pendingBoundary;

    private String dimension = "";

    void close() {
        if (terrain != null) {
            terrain.close();
            terrain = null;
        }
    }

    /**
     * Frames the whole arena boundary, so opening any page shows the map rather than wherever the
     * player happens to stand.
     */
    void frame(Model.Arena arena, double fallbackX, double fallbackZ) {
        if (arena == null || arena.boundary.size() < 3) {
            cx = fallbackX;
            cz = fallbackZ;
            span = 256;
            return;
        }
        double x0 = arena.boundary.stream().mapToDouble(Model.Vec::x).min().orElse(fallbackX);
        double x1 = arena.boundary.stream().mapToDouble(Model.Vec::x).max().orElse(fallbackX);
        double z0 = arena.boundary.stream().mapToDouble(Model.Vec::z).min().orElse(fallbackZ);
        double z1 = arena.boundary.stream().mapToDouble(Model.Vec::z).max().orElse(fallbackZ);
        cx = (x0 + x1) / 2;
        cz = (z0 + z1) / 2;
        span = Math.max(64, Math.max(x1 - x0, z1 - z0) * 1.35);
    }

    /** Blocks per screen pixel: above 1 the view is too coarse for precise placement. */
    double blocksPerPixel(Layout.Rect body) {
        return Layout.blocksPerPixel(span, Math.max(1, body.w()));
    }

    boolean crisp(Layout.Rect body) {
        return Layout.crisp(span, Math.max(1, body.w()));
    }

    // ------------------------------------------------------------------ projection

    /** The projection currently in force; every draw and every hit test goes through this. */
    MapGeometry.MapProjection projection(Layout.Rect body) {
        return MapGeometry.of(body, cx, cz, span);
    }

    int sx(Layout.Rect body, double worldX) {
        return projection(body).sx(worldX);
    }

    int sz(Layout.Rect body, double worldZ) {
        return projection(body).sz(worldZ);
    }

    double worldX(Layout.Rect body, double screenX) {
        return projection(body).worldX(screenX);
    }

    double worldZ(Layout.Rect body, double screenY) {
        return projection(body).worldZ(screenY);
    }

    void pan(Layout.Rect body, double dragX, double dragY) {
        cx -= dragX * blocksPerPixel(body);
        cz -= dragY * blocksPerPixel(body);
    }

    /** Zooms about the cursor, so the thing being pointed at stays put. */
    void zoom(Layout.Rect body, double screenX, double screenY, boolean in) {
        MapGeometry.MapProjection before = projection(body);
        double beforeX = before.worldX(screenX), beforeZ = before.worldZ(screenY);
        span = Math.max(16, Math.min(1_000_000, span * (in ? 1 / 1.25 : 1.25)));
        // Re-centre from the world point that was under the cursor, using the new span, so the point
        // being inspected does not slide away as the view scales.
        MapGeometry.MapProjection after = projection(body);
        cx = beforeX - (screenX - before.x() - before.w() / 2.0) * after.blocksPerPixel();
        cz = beforeZ - (screenY - before.y() - before.h() / 2.0) * after.blocksPerPixel();
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Paints terrain, then overlays, then the highlights, inside one scissor.
     *
     * @param state    live snapshot for flag ownership and player blips, or {@code null}
     * @param selfTeam team whose spawns are emphasised, or -1 for both
     */
    void draw(GuiGraphics graphics, Font font, Layout.Rect body, Model.Arena arena,
              JsonObject state, int selfTeam, double selfX, double selfZ, float yaw) {
        if (arena == null || body.w() <= 0 || body.h() <= 0) return;
        if (terrain == null) terrain = new TerrainPreview();
        String dim = arena.dimension == null ? "" : arena.dimension;
        dimension = dim;

        graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        terrain.draw(graphics, body.x(), body.y(), body.w(), body.h(), cx, cz, span, dim);

        if (showGrid) {
            double step = Layout.gridStep(span, Math.max(1, body.w()));
            if (step > 0) {
                double x0 = Math.ceil((cx - span / 2) / step) * step;
                for (double x = x0; x <= cx + span / 2; x += step) {
                    int px = sx(body, x);
                    if (px >= body.x() && px < body.right()) {
                        graphics.fill(px, body.y(), px + 1, body.bottom(), 0xff243642);
                    }
                }
                double z0 = Math.ceil((cz - span / 2) / step) * step;
                for (double z = z0; z <= cz + span / 2; z += step) {
                    int pz = sz(body, z);
                    if (pz >= body.y() && pz < body.bottom()) {
                        graphics.fill(body.x(), pz, body.right(), pz + 1, 0xff243642);
                    }
                }
            }
        }

        polygon(graphics, body, arena.boundary, 0xff69bdff, 2);
        for (Model.Sector sector : arena.sectors) {
            int color = sector.index == highlightSector ? Ui.GOLD : 0xff8a7440;
            polygon(graphics, body, sector.boundary, color, sector.index == highlightSector ? 2 : 1);
        }
        // Activity zones, tinted the team colour and drawn under the markers so they read as ground
        // rather than as objects. Only zones that actually constrain something are drawn: an empty zone
        // means "no restriction", and drawing an empty outline would suggest a boundary that is not there.
        if (arena.zones != null) {
            for (Model.Zone zone : arena.zones) {
                if (zone == null || !zone.active()) continue;
                boolean selected = zone.team == highlightZone;
                int color = selected ? Ui.GOLD : zone.team == 0 ? 0xff5a8fd6 : 0xffd65a5a;
                polygonShade(graphics, body, zone.boundary, zone.team == 0 ? 0x185a8fd6 : 0x18d65a5a);
                polygon(graphics, body, zone.boundary, color, selected ? 2 : 1);
            }
        }
        if (showPoints) {
            JsonArray flags = array(state, "flags");
            for (int i = 0; i < arena.points.size(); i++) {
                Model.Point point = arena.points.get(i);
                int owner = -1;
                double progress = 50;
                if (flags != null && i < flags.size() && flags.get(i).isJsonObject()) {
                    JsonObject flag = flags.get(i).getAsJsonObject();
                    owner = flag.has("owner") ? flag.get("owner").getAsInt() : -1;
                    progress = flag.has("progress") ? flag.get("progress").getAsDouble() : 50;
                }
                int ringColor = owner == 0 ? 0x6677bbff : owner == 1 ? 0x66ff7777 : 0x55cccccc;
                ring(graphics, body, point.x, point.z, point.radius, ringColor);

                int px = sx(body, point.x), pz = sz(body, point.z);
                boolean selected = i == highlightPoint;
                int fill = TacticalColors.objective(owner,selfTeam);
                graphics.fill(px - 4, pz - 4, px + 5, pz + 5, 0xff101820);
                graphics.fill(px - 3, pz - 3, px + 4, pz + 4, fill);
                if (selected) {
                    Ui.rectOutline(graphics, new Layout.Rect(px - 7, pz - 7, 15, 15), Ui.GOLD);
                }
                TacticalColors.label(graphics,font,point.id,px,pz-15,fill);
                if (progress > 0 && progress < 100) {
                    int barWidth = 20;
                    int barLeft = px - barWidth / 2;
                    int filled = (int) Math.round(barWidth * Math.max(0, Math.min(100, progress)) / 100.0);
                    graphics.fill(barLeft - 1, pz + 5, barLeft + barWidth + 1, pz + 9, 0xff000000);
                    if (filled > 0) graphics.fill(barLeft, pz + 6, barLeft + filled, pz + 8, Ui.BLUE);
                    if (filled < barWidth) {
                        graphics.fill(barLeft + filled, pz + 6, barLeft + barWidth, pz + 8, Ui.RED);
                    }
                }
            }
        }
        if (showSpawns) {
            for (int i = 0; i < arena.spawns.size(); i++) {
                Model.Spawn spawn = arena.spawns.get(i);
                if (selfTeam >= 0 && spawn.team != selfTeam) continue;
                int color = spawn.team == 0 ? Ui.BLUE : Ui.RED;
                int px = sx(body, spawn.x), pz = sz(body, spawn.z);
                if (spawn.allows('A')) {
                    // Air points get a bracket so a runway is distinguishable from a ground spawn.
                    Ui.rectOutline(graphics, new Layout.Rect(px - 6, pz - 6, 13, 13), color);
                    Ui.handle(graphics, px, pz, 2, TacticalColors.SELF);
                } else {
                    Ui.handle(graphics, px, pz, 3, color);
                }
                if (i == highlightSpawn) {
                    Ui.rectOutline(graphics, new Layout.Rect(px - 9, pz - 9, 19, 19), Ui.GOLD);
                }
                String label = spawn.allows('A') ? spawn.id + " ✈" : spawn.id;
                TacticalColors.label(graphics,font,label,px+9+font.width(label)/2,pz-4,color);
            }
        }
        // Supply pads: a ring at the service radius plus a cross, so the area they cover is visible
        // rather than being a point the operator has to remember the size of.
        if (arena.supplies != null) {
            for (int i = 0; i < arena.supplies.size(); i++) {
                VehicleSupply.Supply supply = arena.supplies.get(i);
                if (supply == null) continue;
                if (selfTeam >= 0 && !supply.serves(selfTeam)) continue;
                int color = supply.team() == 0 ? Ui.BLUE : supply.team() == 1 ? Ui.RED : Ui.TEXT_OK;
                ring(graphics, body, supply.x(), supply.z(), supply.radius(), color);
                int px = sx(body, supply.x()), pz = sz(body, supply.z());
                graphics.fill(px - 1, pz - 4, px + 2, pz + 5, color);
                graphics.fill(px - 4, pz - 1, px + 5, pz + 2, color);
                if (i == highlightSupply) {
                    Ui.rectOutline(graphics, new Layout.Rect(px - 7, pz - 7, 15, 15), Ui.GOLD);
                }
                TacticalColors.label(graphics, font, supply.id(), px + 7 + font.width(supply.id()) / 2,
                        pz - 9, color);
            }
        }
        if (pendingBoundary != null && !pendingBoundary.isEmpty()) {
            polygon(graphics, body, pendingBoundary, Ui.GOLD, 1);
            for (Model.Vec vertex : pendingBoundary) {
                Ui.handle(graphics, sx(body, vertex.x()), sz(body, vertex.z()), 2, Ui.GOLD);
            }
        }
        if (showPlayers && state != null) {
            players(graphics, body, state, selfX, selfZ, yaw);
        }
        graphics.disableScissor();
    }

    /** Player blips: self as a heading triangle, mates as dots, spotted enemies as diamonds. */
    private void players(GuiGraphics graphics, Layout.Rect body, JsonObject state,
                         double selfX, double selfZ, float yaw) {
        // Manual marks under the contacts, for the same reason as on the minimap: a live contact must stay
        // readable on top of the ring that records where somebody was last seen.
        for (JsonElement element : arrayOrEmpty(state, "marks")) {
            if (!element.isJsonObject()) continue;
            JsonObject mark = element.getAsJsonObject();
            if (!mark.has("x") || !mark.has("z")) continue;
            int markX = sx(body, mark.get("x").getAsDouble());
            int markY = sz(body, mark.get("z").getAsDouble());
            // Ring only; the countdown label was removed on request.
            TacticalColors.mark(graphics, markX, markY, 5, TacticalColors.MARK);
        }
        for (JsonElement element : arrayOrEmpty(state, "mates")) {
            if (!element.isJsonObject()) continue;
            JsonObject mate = element.getAsJsonObject();
            if (!mate.has("x") || !mate.has("z")) continue;
            if (mate.has("self") && mate.get("self").getAsBoolean()) continue;
            boolean hostile = mate.has("hostile") && mate.get("hostile").getAsBoolean();
            int px = sx(body, mate.get("x").getAsDouble());
            int pz = sz(body, mate.get("z").getAsDouble());
            if (hostile) {
                // A dot, not an arrow: the map knows an enemy was seen, not which way they face.
                TacticalColors.dot(graphics, px, pz, 2, TacticalColors.ENEMY);
            } else {
                float mateYaw = mate.has("yaw") ? mate.get("yaw").getAsFloat() : 0f;
                TacticalColors.arrow(graphics, px, pz, mateYaw, 5, TacticalColors.FRIEND);
            }
        }
        // Own marker plus view cone, matching the minimap so the two never disagree about what is visible.
        int selfPx = sx(body, selfX), selfPz = sz(body, selfZ);
        double blocksPerPixel = Math.max(1e-6, span / Math.max(1, body.w()));
        int coneRadius = (int) Math.min(Math.max(body.w(), body.h()) * 0.45,
                Tactics.ENEMY_GLOW_RANGE / blocksPerPixel);
        TacticalColors.viewCone(graphics, selfPx, selfPz, yaw, 35, coneRadius,
                TacticalColors.VIEW_CONE, TacticalColors.VIEW_EDGE);
        TacticalColors.arrow(graphics, selfPx, selfPz, yaw, 6, TacticalColors.SELF);
    }

    // ------------------------------------------------------------------ picking

    /**
     * Resolves a click to a marker or a free world position.
     *
     * <p>Marker hit-testing runs first and wins ties: when placing near an existing point the operator
     * almost always means to grab it, not to drop a new one on top.
     *
     * @return the hit, or {@code null} when the click was outside the map body
     */
    Hit click(Layout.Rect body, double mouseX, double mouseY, Model.Arena arena, Mode mode) {
        if (arena == null || !body.inside(mouseX, mouseY)) return null;
        if (mode == Mode.VIEW) return null;

        final int radius = 10;
        if (mode == Mode.PICK) {
            int spawn = nearestSpawn(body, arena, mouseX, mouseY, radius);
            if (spawn >= 0) {
                Model.Spawn found = arena.spawns.get(spawn);
                return new Hit(Kind.SPAWN, spawn, found.x, found.y, found.z);
            }
            int point = nearestPoint(body, arena, mouseX, mouseY, radius);
            if (point >= 0) {
                Model.Point found = arena.points.get(point);
                return new Hit(Kind.POINT, point, found.x, found.y, found.z);
            }
            return null;
        }

        // PLACE: grab an existing marker within reach, otherwise drop a new one here.
        int spawn = nearestSpawn(body, arena, mouseX, mouseY, radius);
        if (spawn >= 0) {
            Model.Spawn found = arena.spawns.get(spawn);
            return new Hit(Kind.SPAWN, spawn, found.x, found.y, found.z);
        }
        int point = nearestPoint(body, arena, mouseX, mouseY, radius);
        if (point >= 0) {
            Model.Point found = arena.points.get(point);
            return new Hit(Kind.POINT, point, found.x, found.y, found.z);
        }
        double x = worldX(body, mouseX), z = worldZ(body, mouseY);
        return new Hit(Kind.BOUNDARY, -1, Math.rint(x), 0, Math.rint(z));
    }

    int nearestSpawn(Layout.Rect body, Model.Arena arena, double mouseX, double mouseY, double radius) {
        int best = -1;
        double bestDistance = radius;
        for (int i = 0; i < arena.spawns.size(); i++) {
            Model.Spawn spawn = arena.spawns.get(i);
            double distance = Math.hypot(sx(body, spawn.x) - mouseX, sz(body, spawn.z) - mouseY);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    int nearestPoint(Layout.Rect body, Model.Arena arena, double mouseX, double mouseY, double radius) {
        int best = -1;
        double bestDistance = radius;
        for (int i = 0; i < arena.points.size(); i++) {
            Model.Point point = arena.points.get(i);
            double distance = Math.hypot(sx(body, point.x) - mouseX, sz(body, point.z) - mouseY);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /** Surface Y under the cursor, for placing a marker at ground level rather than at y=64. */
    double groundY(Layout.Rect body, double mouseX, double mouseY) {
        var level = minecraft.level;
        if (level == null) return Double.NaN;
        int x = (int) Math.floor(worldX(body, mouseX));
        int z = (int) Math.floor(worldZ(body, mouseY));
        var chunk = level.getChunkSource().getChunk(x >> 4, z >> 4,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
        if (chunk == null || !dimension.equals(level.dimension().location().toString())) return Double.NaN;
        return chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) + 1;
    }

    // ------------------------------------------------------------------ primitives

    void polygon(GuiGraphics graphics, Layout.Rect body, List<Model.Vec> points, int color, int thickness) {
        if (points == null || points.size() < 2) return;
        for (int i = 0; i < points.size(); i++) {
            Model.Vec a = points.get(i);
            Model.Vec b = points.get((i + 1) % points.size());
            int[] segment=MapGeometry.clip(sx(body,a.x()),sz(body,a.z()),sx(body,b.x()),sz(body,b.z()),
                    body.x(),body.y(),body.right()-1,body.bottom()-1);
            if(segment!=null)Ui.line(graphics,segment[0],segment[1],segment[2],segment[3],color,thickness);
        }
    }

    /**
     * Fills a polygon with a translucent colour so a zone reads as an area rather than an outline.
     *
     * <p>Scanline fill on screen rows, which is enough here: the polygons are convex-ish map regions a
     * few hundred pixels across, and a per-row span is both simple and free of the seams a triangle fan
     * would leave. Rows are clipped to the panel, so an off-screen vertex cannot paint outside it.
     */
    void polygonShade(GuiGraphics graphics, Layout.Rect body, List<Model.Vec> points, int color) {
        if (points == null || points.size() < 3) return;
        int n = points.size();
        int top = Integer.MAX_VALUE, bottom = Integer.MIN_VALUE;
        for (Model.Vec v : points) {
            int pz = sz(body, v.z());
            top = Math.min(top, pz);
            bottom = Math.max(bottom, pz);
        }
        top = Math.max(top, body.y());
        bottom = Math.min(bottom, body.bottom() - 1);
        for (int py = top; py <= bottom; py++) {
            double worldZ = cz + (py + 0.5 - (body.y() + body.h() / 2.0)) * span / Math.max(1, body.h());
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                Model.Vec a = points.get(i), b = points.get((i + 1) % n);
                double az = a.z(), bz = b.z();
                if ((az > worldZ) == (bz > worldZ)) continue;
                double t = (worldZ - az) / (bz - az);
                double x = a.x() + t * (b.x() - a.x());
                lo = Math.min(lo, x);
                hi = Math.max(hi, x);
            }
            if (lo > hi) continue;
            int x0 = Math.max(body.x(), sx(body, lo));
            int x1 = Math.min(body.right() - 1, sx(body, hi));
            if (x1 > x0) graphics.fill(x0, py, x1, py + 1, color);
        }
    }

    void ring(GuiGraphics graphics, Layout.Rect body, double worldX, double worldZ,
              double radius, int color) {
        int steps = 48;
        for (int i = 0; i < steps; i++) {
            double a0 = i * Math.PI * 2 / steps;
            double a1 = (i + 1) * Math.PI * 2 / steps;
            Ui.line(graphics,
                    sx(body, worldX + Math.cos(a0) * radius), sz(body, worldZ + Math.sin(a0) * radius),
                    sx(body, worldX + Math.cos(a1) * radius), sz(body, worldZ + Math.sin(a1) * radius),
                    color, 1);
        }
    }

    /** Cursor readout for the footer: world position and how coarse the view is. */
    String readout(Layout.Rect body, double mouseX, double mouseY) {
        if (!body.inside(mouseX, mouseY)) {
            return String.format(Locale.ROOT, "视野 %.0f 格 · %.2f 格/像素", span, blocksPerPixel(body));
        }
        return String.format(Locale.ROOT, "X %.0f  Z %.0f · 视野 %.0f 格 · %.2f 格/像素",
                worldX(body, mouseX), worldZ(body, mouseY), span, blocksPerPixel(body));
    }

    private static JsonArray array(JsonObject json, String key) {
        if (json == null || !json.has(key) || !json.get(key).isJsonArray()) return null;
        return json.getAsJsonArray(key);
    }

    private static JsonArray arrayOrEmpty(JsonObject json, String key) {
        JsonArray found = array(json, key);
        return found == null ? new JsonArray() : found;
    }
}
