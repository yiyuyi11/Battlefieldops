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
    String deploymentSelection="";
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

    /** Whether this instance is the tactical map (rotates with heading) rather than the editor. */
    boolean rotating;
    /** Rotation in radians currently in force, from the last {@link #draw} call's yaw. */
    private double rotation;

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
    int highlightElite=-1;
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

        // Follow-heading: one pose transform rotates the terrain and every overlay together, so the map
        // and the markers can never disagree about which way is up. Clicks are un-rotated by {@link
        // #unrotate} against the same angle.
        rotation = rotating && ClientPrefs.followHeading() ? MapRotation.radians(yaw) : 0;
        graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        if (rotation != 0) {
            float centerX = body.x() + body.w() / 2f, centerY = body.y() + body.h() / 2f;
            graphics.pose().pushPose();
            graphics.pose().translate(centerX, centerY, 0);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) Math.toDegrees(rotation)));
            graphics.pose().translate(-centerX, -centerY, 0);
        }

        try {
        int margin=rotationMargin(body);
        terrain.draw(graphics,body.x()-margin,body.y()-margin,body.w()+2*margin,body.h()+2*margin,
                cx,cz,span*(body.w()+2*margin)/body.w(),dim);

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
            int color = sector.index == highlightSector ? Ui.GOLD : 0xff667d91;
            polygon(graphics, body, sector.boundary, color, sector.index == highlightSector ? 2 : 1);
        }
        // Activity zones, tinted the team colour and drawn under the markers so they read as ground
        // rather than as objects. Only zones that actually constrain something are drawn: an empty zone
        // means "no restriction", and drawing an empty outline would suggest a boundary that is not there.
        if (arena.zones != null) {
            for (Model.Zone zone : arena.zones) {
                if (zone == null || !zone.active()) continue;
                boolean selected = zone.team + 2*java.util.List.of("ALL","I","G","A").indexOf(zone.mobility==null?"ALL":zone.mobility) == highlightZone;
                int baseColor=TacticalColors.objective(zone.team,selfTeam);
                int color = selected ? Ui.GOLD : baseColor;
                polygonShade(graphics, body, zone.boundary, (baseColor&0xffffff)|0x18000000);
                polygon(graphics, body, zone.boundary, color, selected ? 2 : 1);
            }
        }
        if (showPoints) {
            JsonArray flags = array(state, "flags");
            for (int i = 0; i < arena.points.size(); i++) {
                Model.Point point = arena.points.get(i);
                if(showPlayers&&state!=null&&!CombatHud.active(arena,point,Engine.num(state,"stage",0)))continue;
                int owner = -1;
                double progress = 50;
                if (flags != null && i < flags.size() && flags.get(i).isJsonObject()) {
                    JsonObject flag = flags.get(i).getAsJsonObject();
                    owner = flag.has("owner") ? flag.get("owner").getAsInt() : -1;
                    progress = flag.has("progress") ? flag.get("progress").getAsDouble() : 50;
                }
                int ringColor = owner == 0 ? 0x6677bbff : owner == 1 ? 0x66ff7777 : 0x55cccccc;
                if(point.boundary!=null&&point.boundary.size()>=3){polygonShade(graphics,body,point.boundary,(ringColor&0xffffff)|0x18000000);polygon(graphics,body,point.boundary,ringColor,1);}
                else ring(graphics, body, point.x, point.z, point.radius, ringColor);

                int px = sx(body, point.x), pz = sz(body, point.z);
                boolean selected = i == highlightPoint;
                MapIcons.capture(graphics,font,point.id,px,pz,(float)progress,owner,selfTeam,selected,rotation);
            }
        }
        if(showSpawns)for(var spawn:arena.spawns){
            if(selfTeam>=0&&spawn.team!=selfTeam)continue;
            int tint=TacticalColors.objective(spawn.team,selfTeam);
            if(spawn.boundary!=null&&spawn.boundary.size()>=3){polygonShade(graphics,body,spawn.boundary,(tint&0xffffff)|0x18000000);polygon(graphics,body,spawn.boundary,tint,1);}
            else if(spawn.radius>0)ring(graphics,body,spawn.x,spawn.z,spawn.radius,tint);
        }
        if (showSpawns && showPlayers && state!=null && state.has("deploySites")) {
            for(var item:state.getAsJsonArray("deploySites")) {
                var site=item.getAsJsonObject();
                String kind=Engine.str(site,"kind","BASE");
                if(kind.equals("POINT")&&site.has("spawn")&&site.get("spawn").getAsBoolean())kind="BASE";
                    if(!kind.equals("BASE")&&!kind.equals("AIR")&&!kind.equals("BEACON"))continue;
                int px=sx(body,site.get("x").getAsDouble()), pz=sz(body,site.get("z").getAsDouble());
                MapIcons.site(graphics,font,Engine.str(site,"kind","BASE"),px,pz,false,rotation);
                String label=DeploymentText.label(Engine.str(site,"id",""),kind,Engine.str(site,"label",""));
                MapIcons.upright(graphics,px,pz,rotation,()->TacticalColors.label(graphics,font,label,px+9+font.width(label)/2,pz-4,TacticalColors.FRIEND));
            }
        } else if (showSpawns) {
            for (int i = 0; i < arena.spawns.size(); i++) {
                Model.Spawn spawn = arena.spawns.get(i);
                if (selfTeam >= 0 && spawn.team != selfTeam) continue;
                int color = spawn.team == 0 ? Ui.BLUE : Ui.RED;
                int px = sx(body, spawn.x), pz = sz(body, spawn.z);
                MapIcons.site(graphics,font,spawn.allows('A')?"AIR":"BASE",px,pz,i==highlightSpawn,rotation);
                String label = spawn.allows('A') ? spawn.id + " ✈" : spawn.id;
                MapIcons.upright(graphics,px,pz,rotation,()->TacticalColors.label(graphics,font,label,px+9+font.width(label)/2,pz-4,color));
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
                MapIcons.marker(graphics,px,pz,rotation,supply.health()?cn.bfops.conquest.client.marker.TacticalMarkerRenderer.MarkerVisualType.MEDICAL_BOX:cn.bfops.conquest.client.marker.TacticalMarkerRenderer.MarkerVisualType.AMMO_BOX,"",1);
                if (i == highlightSupply) {
                    Ui.rectOutline(graphics, new Layout.Rect(px - 7, pz - 7, 15, 15), Ui.GOLD);
                }
                MapIcons.upright(graphics,px,pz,rotation,()->TacticalColors.label(graphics, font, supply.id(), px + 9 + font.width(supply.id()) / 2,
                        pz - 9, color));
            }
        }
        if(!showPlayers)for(var point:arena.eliteSpawns){
            int px=sx(body,point.x),py=sz(body,point.z);
            MapIcons.upright(graphics,px,py,rotation,()->{
                HeroIcons.draw(graphics,point.type,px,py,16,point.enabled?1:.4f);
                if(arena.eliteSpawns.indexOf(point)==highlightElite)Ui.rectOutline(graphics,new Layout.Rect(px-10,py-10,21,21),Ui.GOLD);
            });
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
        if(state!=null&&state.has("deploySites")&&!deploymentSelection.isEmpty())for(var value:state.getAsJsonArray("deploySites")){
            var site=value.getAsJsonObject();if(!deploymentSelection.equals(Engine.str(site,"id","")))continue;
            int px=sx(body,site.get("x").getAsDouble()),py=sz(body,site.get("z").getAsDouble());
            MapIcons.upright(graphics,px,py,rotation,()->SelectionReticle.draw(graphics,px,py,10));break;
        }
        } finally {
            if (rotation != 0) graphics.pose().popPose();
            graphics.disableScissor();
        }
    }

    /** Un-rotates a mouse position back to north-up coordinates, for hit-testing a rotated map. */
    double[] unrotate(Layout.Rect body, double mouseX, double mouseY) {
        if (rotation == 0) return new double[]{mouseX, mouseY};
        double cx = body.x() + body.w() / 2.0, cy = body.y() + body.h() / 2.0;
        double rx = mouseX - cx, ry = mouseY - cy;
        double cos = Math.cos(rotation), sin = Math.sin(rotation);
        return new double[]{cx + rx * cos + ry * sin, cy - rx * sin + ry * cos};
    }

    /** Player blips: self as a heading triangle, mates as dots, spotted enemies as diamonds. */
    private void players(GuiGraphics graphics, Layout.Rect body, JsonObject state,
                         double selfX, double selfZ, float yaw) {
        MapIcons.contacts(graphics,net.minecraft.client.Minecraft.getInstance().font,state,value->sx(body,value),value->sz(body,value),rotation);
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

        // The map may be rotated; hit-test in north-up coordinates against the same projection it drew with.
        double[] um = unrotate(body, mouseX, mouseY);
        double ux = um[0], uy = um[1];

        final int radius = 10;
        if (mode == Mode.PICK) {
            int spawn = nearestSpawn(body, arena, ux, uy, radius);
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
        int spawn = nearestSpawn(body, arena, ux, uy, radius);
        if (spawn >= 0) {
            Model.Spawn found = arena.spawns.get(spawn);
            return new Hit(Kind.SPAWN, spawn, found.x, found.y, found.z);
        }
        int point = nearestPoint(body, arena, ux, uy, radius);
        if (point >= 0) {
            Model.Point found = arena.points.get(point);
            return new Hit(Kind.POINT, point, found.x, found.y, found.z);
        }
        double x = worldX(body, ux), z = worldZ(body, uy);
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
                net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
        if (chunk == null || !dimension.equals(level.dimension().location().toString())) return Double.NaN;
        return chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) + 1;
    }

    private int rotationMargin(Layout.Rect body){
        return rotation==0?0:(int)Math.ceil((Math.hypot(body.w(),body.h())-Math.min(body.w(),body.h()))/2)+2;
    }

    // ------------------------------------------------------------------ primitives

    void polygon(GuiGraphics graphics, Layout.Rect body, List<Model.Vec> points, int color, int thickness) {
        if (points == null || points.size() < 2) return;
        // Collect every clipped edge first, then rasterise them in one coverage pass. Per-edge Ui.line
        // sampled each segment pixel by pixel with a fill per sample; the editor redraws this canvas every
        // frame, so a boundary plus its zones was tens of thousands of draws and the canvas felt sticky.
        java.util.List<int[]> segments = new java.util.ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            Model.Vec a = points.get(i);
            Model.Vec b = points.get((i + 1) % points.size());
            int[] segment=MapGeometry.clip(sx(body,a.x()),sz(body,a.z()),sx(body,b.x()),sz(body,b.z()),
                    body.x()-rotationMargin(body),body.y()-rotationMargin(body),body.right()-1+rotationMargin(body),body.bottom()-1+rotationMargin(body));
            if(segment!=null)segments.add(segment);
        }
        if (!segments.isEmpty()) BatchLine.polyline(graphics, segments, thickness, color);
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
        int margin=rotationMargin(body);
        top = Math.max(top, body.y()-margin);
        bottom = Math.min(bottom, body.bottom() - 1+margin);
        for (int py = top; py <= bottom; py++) {
            // Inverse-map the row through the same projection the outline uses. Inverting with span/h here
            // (as it once did) misplaces the shade whenever the panel is not exactly square, because the
            // projection scales both world axes by the panel width and treats the extra height as padding.
            double rowZ = worldZ(body, py + 0.5);
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                Model.Vec a = points.get(i), b = points.get((i + 1) % n);
                double az = a.z(), bz = b.z();
                if ((az > rowZ) == (bz > rowZ)) continue;
                double t = (rowZ - az) / (bz - az);
                double x = a.x() + t * (b.x() - a.x());
                lo = Math.min(lo, x);
                hi = Math.max(hi, x);
            }
            if (lo > hi) continue;
            int x0 = Math.max(body.x()-margin, sx(body, lo));
            int x1 = Math.min(body.right() - 1+margin, sx(body, hi));
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
        double[] position=unrotate(body,mouseX,mouseY);
        return String.format(Locale.ROOT, "X %.0f  Z %.0f · 视野 %.0f 格 · %.2f 格/像素",
                worldX(body, position[0]), worldZ(body, position[1]), span, blocksPerPixel(body));
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
