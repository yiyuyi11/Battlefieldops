package cn.bfops;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Top-down squared arena map, shared by the in-match HUD and the deployment page.
 *
 * <p>Pure drawing geometry: it takes an arena plus the last server snapshot and renders boundary,
 * sectors, capture points (owner colour + 0-100 control bar), own-team spawns, teammates and the
 * local player's heading. It never reads or writes match state, so the HUD and the editor cannot
 * drift apart.
 *
 * <p>Scale uses the panel width for both axes so the map stays square even when the panel is not
 * square; extra height simply becomes padding.
 */
final class Minimap {
    static final int BLUE = 0x77bbff;
    static final int RED = 0xff7777;
    static final int NEUTRAL = 0xcccccc;

    private static final int GRID = 0xff2a3d49;
    private static final int BOUNDARY = 0xff69bdff;

    /** Half the horizontal field of view drawn for the player. 35° is roughly a normal spyglass-free view. */
    private static final double VIEW_HALF_ANGLE = 35;
    private static final int SECTOR = 0xff667d91;
    private static final int FRAME = 0xff3c5566;
    private static final int PANEL = 0xff141f28;

    final int x, y, w, h;
    final double cx, cz, span;

    /**
     * Optional terrain layer.
     *
     * <p>The HUD minimap is the same map as everywhere else, so it should show the same landscape
     * rather than a flat grid. Left nullable because the widget is also used headlessly by the offline
     * checks, where there is no client level to sample.
     */
    private TerrainPreview terrain;
    private MinimapOptions options;
    Minimap hudOptions(MinimapOptions value){options=value;return this;}

    Minimap terrain(TerrainPreview preview) {
        this.terrain = preview;
        return this;
    }

    /**
     * Package-private rather than private: {@link MapBake} rasterises the static layer through a
     * zero-origin instance whose paint lands in a pixel array, which keeps one copy of the geometry.
     */
    Minimap(int x, int y, int w, int h, double cx, double cz, double span) {
        this.x = x;
        this.y = y;
        this.w = Math.max(1, w);
        this.h = Math.max(1, h);
        this.cx = cx;
        this.cz = cz;
        this.span = Math.max(8, span);
    }

    /** Fits the arena into a laid-out rect, which is how every screen in this mod allocates space. */
    static Minimap fit(Model.Arena arena, Layout.Rect rect, double fallbackX, double fallbackZ) {
        return fit(arena, rect.x(), rect.y(), rect.w(), rect.h(), fallbackX, fallbackZ);
    }

    /** Fits the whole arena boundary into the panel, falling back to a 256-block window. */
    static Minimap fit(Model.Arena arena, int x, int y, int w, int h, double fallbackX, double fallbackZ) {
        if (arena == null || arena.boundary.size() < 3) {
            return new Minimap(x, y, w, h, fallbackX, fallbackZ, 256);
        }
        double x0 = arena.boundary.stream().mapToDouble(Model.Vec::x).min().orElse(fallbackX);
        double x1 = arena.boundary.stream().mapToDouble(Model.Vec::x).max().orElse(fallbackX);
        double z0 = arena.boundary.stream().mapToDouble(Model.Vec::z).min().orElse(fallbackZ);
        double z1 = arena.boundary.stream().mapToDouble(Model.Vec::z).max().orElse(fallbackZ);
        return new Minimap(x, y, w, h, (x0 + x1) / 2, (z0 + z1) / 2,
                Math.max(48, Math.max(x1 - x0, z1 - z0) * 1.15));
    }

    int sx(double worldX) {
        return x + w / 2 + (int) Math.round((worldX - cx) / span * w);
    }

    /**
     * A map centred on a continuously-moving point, for the in-match HUD.
     *
     * <p>The centre used to be snapped to a multiple of eight blocks, so the terrain texture underneath
     * only had to be re-baked when the player crossed a grid line. The cost was a map that stepped in
     * eight-block jumps while walking, which is visible on any reasonably large minimap. The snapping is
     * gone: the caller passes an already-smoothed position (see {@link MapFollow}) and bake work is
     * bounded by a movement threshold instead of by a grid — which bounds it better, since it also stops
     * re-baking when the player stands still inside one cell.
     */
    static Minimap local(int x, int y, int size, double playerX, double playerZ, double span) {
        return new Minimap(x, y, size, size, playerX, playerZ, span);
    }

    int sy(double worldZ) {
        return y + h / 2 + (int) Math.round((worldZ - cz) / span * w);
    }

    double worldX(double screenX) {
        return cx + (screenX - x - w / 2.0) * span / w;
    }

    double worldZ(double screenY) {
        return cz + (screenY - y - h / 2.0) * span / w;
    }

    boolean inside(double screenX, double screenY) {
        return screenX >= x && screenX < x + w && screenY >= y && screenY < y + h;
    }

    static int color(int owner) {
        return owner == 0 ? BLUE : owner == 1 ? RED : NEUTRAL;
    }

    static String holder(int owner) {
        return owner == 0 ? "蓝" : owner == 1 ? "红" : "中立";
    }

    /**
     * The small set of drawing operations the static layer needs.
     *
     * <p>Exists so the same rasterisation code can paint either onto the screen or into an off-screen
     * pixel array. Without it, the baked layer would need a second copy of all the geometry — and two
     * copies of "how a capture point ring looks" is exactly how a map ends up rendered two different ways
     * depending on which code path drew it.
     *
     * <p>Coordinates are local to the texture being painted, so the painter does not need to know where
     * the map sits on screen.
     */
    interface Painter {
        /** Fills a half-open rectangle. Alpha is honoured: the rings are translucent by design. */
        void fill(int x0, int y0, int x1, int y1, int argb);
    }

    /** A painter that writes into an ARGB pixel array, used by {@link MapBake}. */
    static Painter painter(int[] pixels, int width, int height) {
        return (x0, y0, x1, y1, argb) -> {
            int left = Math.max(0, Math.min(x0, x1));
            int right = Math.min(width, Math.max(x0, x1));
            int top = Math.max(0, Math.min(y0, y1));
            int bottom = Math.min(height, Math.max(y0, y1));
            if (right <= left || bottom <= top) return;
            int alpha = (argb >>> 24) & 0xff;
            int red = (argb >> 16) & 0xff, green = (argb >> 8) & 0xff, blue = argb & 0xff;
            for (int y = top; y < bottom; y++) {
                int row = y * width;
                for (int x = left; x < right; x++) {
                    int index = row + x;
                    if (alpha >= 255) {
                        pixels[index] = argb;
                    } else if (alpha > 0) {
                        // Source-over against what is already baked. Needed for the translucent range
                        // rings, which are meant to tint the terrain beneath rather than replace it.
                        int dst = pixels[index];
                        int dstA = (dst >>> 24) & 0xff;
                        int outA = alpha + (dstA * (255 - alpha)) / 255;
                        if (outA <= 0) {
                            pixels[index] = 0;
                            continue;
                        }
                        int r = (red * alpha + ((dst >> 16) & 0xff) * dstA * (255 - alpha) / 255) / outA;
                        int g = (green * alpha + ((dst >> 8) & 0xff) * dstA * (255 - alpha) / 255) / outA;
                        int b = (blue * alpha + (dst & 0xff) * dstA * (255 - alpha) / 255) / outA;
                        pixels[index] = (outA << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
                    }
                }
            }
        };
    }

    private static int clamp255(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }

    /** A painter that forwards to a {@link GuiGraphics}, so on-screen drawing uses the same geometry. */
    private static Painter painter(GuiGraphics graphics) {
        return (x0, y0, x1, y1, argb) -> graphics.fill(x0, y0, x1, y1, argb);
    }

    private static void line(Painter painter, int x0, int y0, int x1, int y1, int color, int clipX0,
                             int clipY0, int clipX1, int clipY1) {
        int[] clipped = MapGeometry.clip(x0, y0, x1, y1, clipX0, clipY0, clipX1, clipY1);
        if (clipped == null) return;
        x0 = clipped[0];
        y0 = clipped[1];
        x1 = clipped[2];
        y1 = clipped[3];
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) Math.max(1, steps);
            int px = x0 + (int) Math.round((x1 - x0) * f);
            int py = y0 + (int) Math.round((y1 - y0) * f);
            painter.fill(px, py, px + 1, py + 1, color);
        }
    }

    private void line(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        line(painter(graphics), x0, y0, x1, y1, color | 0xff000000, x, y, x + w - 1, y + h - 1);
    }

    void polygon(GuiGraphics graphics, List<Model.Vec> points, int color) {
        polygon(painter(graphics), points, color, x, y, x + w - 1, y + h - 1);
    }

    private void polygon(Painter painter, List<Model.Vec> points, int color, int clipX0, int clipY0,
                         int clipX1, int clipY1) {
        if (points == null || points.size() < 2) return;
        int solid = color | 0xff000000;
        for (int i = 0; i < points.size(); i++) {
            Model.Vec a = points.get(i);
            Model.Vec b = points.get((i + 1) % points.size());
            line(painter, sx(a.x()), sy(a.z()), sx(b.x()), sy(b.z()), solid, clipX0, clipY0, clipX1, clipY1);
        }
    }

    void ring(GuiGraphics graphics, double worldX, double worldZ, double radius, int color) {
        ring(painter(graphics), worldX, worldZ, radius, color, x, y, x + w - 1, y + h - 1);
    }

    private void ring(Painter painter, double worldX, double worldZ, double radius, int color, int clipX0,
                      int clipY0, int clipX1, int clipY1) {
        int steps = 40;
        for (int i = 0; i < steps; i++) {
            double a0 = i * Math.PI * 2 / steps;
            double a1 = (i + 1) * Math.PI * 2 / steps;
            line(painter,
                    sx(worldX + Math.cos(a0) * radius), sy(worldZ + Math.sin(a0) * radius),
                    sx(worldX + Math.cos(a1) * radius), sy(worldZ + Math.sin(a1) * radius),
                    color, clipX0, clipY0, clipX1, clipY1);
        }
    }

    /** Square marker used for spawn points, scalable so the selected one can stand out. */
    void cross(GuiGraphics graphics, double worldX, double worldZ, int size, int color) {
        color|=0xff000000;
        int px = sx(worldX), py = sy(worldZ);
        graphics.fill(px - size, py - 1, px + size + 1, py + 2, color);
        graphics.fill(px - 1, py - size, px + 2, py + size + 1, color);
    }

    /**
     * Supplies the cached static layer, or {@code null} to draw the static content live.
     *
     * <p>Both paths must stay available. The HUD uses the bake, because it redraws the same arena every
     * frame; the map picker and the editor canvas redraw on demand and would gain nothing but a texture
     * per thumbnail. Leaving the direct path intact is also what keeps this change from touching every
     * existing caller.
     */
    private MapBake bake;

    Minimap bake(MapBake baked) {
        this.bake = baked;
        return this;
    }

    /**
     * @param state   last server snapshot, or {@code null} to draw a map without live ownership
     * @param selfTeam team whose spawns are shown, or -1 to show both
     * @param spawns  whether to draw spawn markers at all
     */
    /** True when the player cannot see the sky, i.e. inside a cave or a building. */
    private static boolean caveMode() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        return !mc.level.canSeeSky(mc.player.blockPosition());
    }

    void draw(GuiGraphics graphics, Font font, Model.Arena arena, JsonObject state, int selfTeam,
              double selfX, double selfZ, float selfYaw, boolean spawns, double swayX, double swayY) {
        // Modern semi-transparent gray info screen: the map sits on a soft plate rather than a thin line frame.
        int panelX=(int)Math.round(swayX+x)-4,panelY=(int)Math.round(swayY+y)-4;
        graphics.fill(panelX,panelY,panelX+w+8,panelY+h+8,0x78283038);
        graphics.fill(panelX+1,panelY+1,panelX+w+7,panelY+h+7,0x9c202830);
        // Inside a cave or a building the terrain is dark and the map is hard to read, so cave mode lifts the
        // opacity. It is automatic: no toggle, no keybind, just a clearer map when the player needs it.
        boolean cave=caveMode();
        // The scissor must live in the same (already-swayed) space the content is drawn in. The HUD applies
        // an inertial translate around the whole match overlay; a scissor in the untranslated coordinates
        // would clip the map a few pixels off the frame whenever the view turns, which reads as the frame
        // detaching from the map.
        graphics.enableScissor((int) Math.round(x + swayX), (int) Math.round(y + swayY),
                (int) Math.round(x + w + swayX), (int) Math.round(y + h + swayY));

        // Follow-heading: one pose transform rotates the terrain, the baked layer and every marker
        // together, so they can never disagree about which way is up. The frame is drawn after the
        // pop, outside the rotation.
        double rotate = ClientPrefs.followHeading() ? MapRotation.radians(selfYaw) : 0;
        if (rotate != 0) {
            float centerX = x + w / 2f, centerY = y + h / 2f;
            graphics.pose().pushPose();
            graphics.pose().translate(centerX, centerY, 0);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) Math.toDegrees(rotate)));
            graphics.pose().translate(-centerX, -centerY, 0);
        }

        try {
        // Overscan by sqrt(2), as the reference Xaero overlay does: rotating a square must not
        // expose empty corners. World-to-pixel scale remains unchanged after extending the bounds.
        int margin=rotate==0?0:(int)Math.ceil(Math.max(w,h)*(Math.sqrt(2)-1)/2)+2;
        int layerW=w+2*margin,layerH=h+2*margin,layerX=x-margin,layerY=y-margin;
        double layerSpan=span*layerW/w;
        if(terrain!=null&&(options==null||options.terrain)){
            graphics.flush();com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            int terrainAlpha=options==null?100:options.terrainOpacity;
            if(cave)terrainAlpha=Math.min(100,terrainAlpha+25);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,terrainAlpha/100f);
            try{terrain.draw(graphics,layerX,layerY,layerW,layerH,cx,cz,layerSpan,arena==null||arena.dimension==null?"":arena.dimension);}
            finally{graphics.flush();com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);}
        }else graphics.fill(layerX,layerY,layerX+layerW,layerY+layerH,cave?0xf01e2a24:0xe0141f28);
        String dim = arena == null || arena.dimension == null ? "" : arena.dimension;
        ResourceLocation baked = bake == null ? null : bake.ensure(arena,state,cx,cz,layerSpan,layerW,layerH,dim,
                state == null ? 0 : Engine.num(state,"stage",0),selfTeam);
        if(baked!=null)graphics.blit(baked,layerX,layerY,layerW,layerH,0f,0f,bake.width(),bake.height(),bake.width(),bake.height());
        else {
            Minimap layer=new Minimap(0,0,layerW,layerH,cx,cz,layerSpan);
            layer.paintStatic((x0,y0,x1,y1,color)->graphics.fill(x0+layerX,y0+layerY,x1+layerX,y1+layerY,color),
                arena,state,state==null?0:Engine.num(state,"stage",0),false,terrain!=null);
        }
        if(options!=null&&options.grid){
            double step=Layout.gridStep(span,w);if(step>0){
                for(double at=Math.ceil((cx-span)/step)*step;at<cx+span;at+=step){int px=sx(at);graphics.fill(px,layerY,px+1,layerY+layerH,0x663d687b);}
                for(double at=Math.ceil((cz-span)/step)*step;at<cz+span;at+=step){int py=sy(at);graphics.fill(layerX,py,layerX+layerW,py+1,0x663d687b);}
            }
        }
        if (spawns && arena != null && (options==null||options.objectives)) {
            if(state!=null&&state.has("deploySites")) {
                for(var item:state.getAsJsonArray("deploySites")) {
                    var site=item.getAsJsonObject();
                    String kind=Engine.str(site,"kind","BASE");
                    if(kind.equals("POINT")&&site.has("spawn")&&site.get("spawn").getAsBoolean())kind="BASE";
                    if(!kind.equals("BASE")&&!kind.equals("AIR")&&!kind.equals("BEACON"))continue;
                    MapIcons.site(graphics,font,kind,sx(site.get("x").getAsDouble()),sy(site.get("z").getAsDouble()),false,rotate);
                }
            } else for (Model.Spawn spawn : arena.spawns) {
                if (selfTeam >= 0 && spawn.team != selfTeam) continue;
                if (arena.isStaged()&&spawn.sector!= (state==null?0:Engine.num(state,"stage",0)))continue;
                MapIcons.site(graphics,font,"BASE",sx(spawn.x),sy(spawn.z),false,rotate);
            }
        }
        if(spawns&&arena!=null&&(options==null||options.support))for(var pad:arena.supplyPoints()){
            if(pad.team()>=0&&pad.team()!=selfTeam)continue;
            MapIcons.marker(graphics,sx(pad.x()),sy(pad.z()),rotate,pad.health()?cn.bfops.conquest.client.marker.TacticalMarkerRenderer.MarkerVisualType.MEDICAL_BOX:cn.bfops.conquest.client.marker.TacticalMarkerRenderer.MarkerVisualType.AMMO_BOX,"",1);
        }
        if(options==null||options.objectives)drawPointLabels(graphics, font, arena, state, selfTeam, rotate);
        MapIcons.contacts(graphics,font,state,this::sx,this::sy,rotate,options);

        // The player's view cone first, so every other marker draws on top of it.
        int selfPx = sx(selfX), selfPy = sy(selfZ);
        double blocksPerPixel = Math.max(1e-6, span / Math.max(1, w));
        // The cone reaches as far as the spotting range does, so what the map shows and what the map is
        // allowed to show agree.
        int coneRadius = (int) Math.min(Math.max(w, h) * 0.45, Tactics.ENEMY_GLOW_RANGE / blocksPerPixel);
        if(options==null||options.viewCone)TacticalColors.viewCone(graphics, selfPx, selfPy, selfYaw, VIEW_HALF_ANGLE, coneRadius,
                TacticalColors.VIEW_CONE, TacticalColors.VIEW_EDGE);
        // Own marker: a yellow dot. The view cone already carries heading, so the dot reads as position.
        selfArrow(graphics, selfPx, selfPy, selfYaw, TacticalColors.SELF);

        } finally {
            if (rotate != 0) graphics.pose().popPose();
            graphics.disableScissor();
        }
        // Soft gray border around the plate, matching the semi-transparent panel rather than a hard frame.
        Ui.frame(graphics,new Layout.Rect(panelX,panelY,w+8,h+8),0x80a8b0b8);
        String[] directions={"N","E","S","W"};
        double radius=Math.min(w,h)/2.0-7;
        // Battlefield-style compass dial: degree ticks around a thin ring, so the minimap reads as a
        // bearing instrument rather than a plain square. Drawn before the N/E/S/W labels so they sit on top.
        int dialX=x+w/2, dialY=y+h/2;
        double dial=Math.min(w,h)/2.0-3;
        for(int deg=0;deg<360;deg+=15){
            double a=Math.toRadians(deg)+rotate;
            int len=(deg%90==0)?4:(deg%30==0?3:2);
            int x1=(int)Math.round(dialX+Math.sin(a)*(dial-len)),y1=(int)Math.round(dialY-Math.cos(a)*(dial-len));
            int x2=(int)Math.round(dialX+Math.sin(a)*dial),y2=(int)Math.round(dialY-Math.cos(a)*dial);
            Ui.line(graphics,x1,y1,x2,y2,0x8098a0a8,1);
        }
        for(int i=0;(options==null||options.compass)&&i<4;i++){
            double angle=i*Math.PI/2+rotate;
            int px=(int)Math.round(x+w/2.0+Math.sin(angle)*radius);
            int py=(int)Math.round(y+h/2.0-Math.cos(angle)*radius);
            graphics.drawCenteredString(font,directions[i],px,py-4,i==0?TacticalColors.SELF:TacticalColors.MATE);
        }
    }

    /** Cached terrain-independent overlay: battle-area mask, boundary and sector lines.
     * All ownership, capture progress and icon glyphs stay live in the shared native icon layer.
     */
    void paintStatic(Painter painter, Model.Arena arena, JsonObject state, int stage, boolean spawns,
                     boolean terrainUnderneath) {
        int clipX1 = w - 1, clipY1 = h - 1;
        // The panel is only an opaque backdrop when there is no terrain underneath. With a terrain layer the
        // panel goes transparent, so the landscape shows through and the grid reads as a ruler over it rather
        // than hiding it entirely.
        painter.fill(0, 0, w, h, terrainUnderneath ? (PANEL & 0x00ffffff) : PANEL);

        for (int i = 1; !terrainUnderneath && i < 8; i++) {
            int gx = i * w / 8;
            painter.fill(gx, 0, gx + 1, h, GRID);
            int gy = i * h / 8;
            painter.fill(0, gy, w, gy + 1, GRID);
        }

        if (arena != null) {
            Model.Zone ownZone=arena.zone(teamOf(state));
            if(arena.boundary!=null&&arena.boundary.size()>=3){
                for(int py=0;py<h;py++){
                    int run=-1;
                    for(int px=0;px<=w;px++){
                        boolean outside=px<w&&(!Model.contains(arena.boundary,worldX(px+.5),worldZ(py+.5))
                                ||ownZone!=null&&ownZone.active()&&!ownZone.contains(worldX(px+.5),worldZ(py+.5)));
                        if(outside&&run<0)run=px;
                        if(!outside&&run>=0){painter.fill(run,py,px,py+1,0xb0141820);run=-1;}
                    }
                }
            }
            polygon(painter, arena.boundary, BOUNDARY, 0, 0, clipX1, clipY1);
            for(int i=0;i<arena.points.size();i++){
                var point=arena.points.get(i);if(!CombatHud.active(arena,point,stage))continue;
                // The capture area outline is static geometry; ownership and progress are drawn live over it
                // (MapIcons.capture), so a neutral outline keeps the bake independent of flag ticks.
                polygon(painter,AreaShape.of(point).outline(),TacticalColors.withAlpha(TacticalColors.NEUTRAL,.6f),0,0,clipX1,clipY1);
            }
            if(spawns)for(var spawn:arena.spawns)if(spawn.team==teamOf(state)&&(!arena.isStaged()||spawn.sector==stage))
                polygon(painter,AreaShape.of(spawn).outline(),TacticalColors.withAlpha(TacticalColors.FRIEND,.5f),0,0,clipX1,clipY1);
            if(arena.zones!=null)for(var zone:arena.zones){
                if(zone!=null&&zone.active())polygon(painter,zone.boundary,TacticalColors.objective(zone.team,teamOf(state)),0,0,clipX1,clipY1);
            }
            if (arena.sectors != null) {
                for (Model.Sector sector : arena.sectors) {
                    polygon(painter, sector.boundary, SECTOR, 0, 0, clipX1, clipY1);
                }
            }
            if (spawns && arena.spawns != null) {
                for (Model.Spawn spawn : arena.spawns) {
                    int color = color(spawn.team) | 0xff000000;
                    int px = sx(spawn.x), py = sy(spawn.z);
                    painter.fill(px - 3, py - 1, px + 4, py + 2, color);
                    painter.fill(px - 1, py - 3, px + 2, py + 4, color);
                }
            }
        }

    }

    /** Live native objective diamonds, including perspective-correct ownership and progress. */
    private void drawPointLabels(GuiGraphics graphics, Font font, Model.Arena arena, JsonObject state, int selfTeam, double rotation) {
        if (arena == null || arena.points == null) return;
        JsonArray flags = array(state, "flags");
        int stage = state == null ? 0 : Engine.num(state, "stage", 0);
        for (int i = 0; i < arena.points.size(); i++) {
            Model.Point point = arena.points.get(i);
            if (!CombatHud.active(arena, point, stage)) continue;
            int owner = -1;
            boolean contested = false;
            float progress = 50;
            if (flags != null && i < flags.size() && flags.get(i).isJsonObject()) {
                JsonObject flag = flags.get(i).getAsJsonObject();
                owner = flag.has("owner") ? flag.get("owner").getAsInt() : -1;
                progress = flag.has("progress")?flag.get("progress").getAsFloat():50;
                contested = flag.has("contested") && flag.get("contested").getAsBoolean();
            }
            int px = sx(point.x), py = sy(point.z);
            MapIcons.capture(graphics,font,point.id,px,py,progress,owner,selfTeam,false,rotation);
            if (contested) MapIcons.upright(graphics,px,py,rotation,()->TacticalColors.label(graphics,font,"!",px,py+9,TacticalColors.ENEMY));
        }
    }

    /** The viewer's team from the snapshot, or -1 when unknown. */
    private static int teamOf(JsonObject state) {
        if (state == null || !state.has("me") || !state.get("me").isJsonObject()) return -1;
        return Engine.num(state.getAsJsonObject("me"), "team", -1);
    }

    private static JsonArray array(JsonObject state, String key) {
        if (state == null || !state.has(key) || !state.get(key).isJsonArray()) return null;
        return state.getAsJsonArray(key);
    }

    /**
     * Opacity for a mark that is about to expire.
     *
     * <p>Reads the millisecond field the server sends alongside the second field, because a fade driven
     * by whole seconds would complete within a single snapshot interval and look like a blink. Falls back
     * to fully opaque when the field is absent, so a newer client talking to an older server draws the
     * old way instead of hiding marks it cannot time.
     */
    static float fade(JsonObject mark) {
        if (!mark.has("leftMs")) return 1f;
        // 1200 ms rather than 1000: the fade then starts a little before the last whole second and is
        // already visibly thinning when the countdown's final tick would have shown, instead of racing it.
        return MarkerTiming.alpha(mark.get("leftMs").getAsLong(),Client.snapshotElapsed());
    }
    /**
     * The player's own marker: an arrow pointing along the view direction.
     *
     * <p>A dot plus a view cone loses all heading information the moment the cone is switched off, and a map
     * where "which way am I facing" is unanswerable is worse than one without a cone. The arrow uses the same
     * heading convention as {@code TacticalColors.viewCone}, so the two always agree.
     */
    private static void selfArrow(GuiGraphics g, int x, int y, float yawDegrees, int colour) {
        float size = ClientPrefs.selfArrowSize();
        double heading = Math.toRadians(yawDegrees);
        double dirX = -Math.sin(heading), dirY = Math.cos(heading);
        double sideX = dirY, sideY = -dirX;
        // All proportions hang off one number, so the configured size scales the whole shape evenly.
        double tip = size * 0.72, back = size * 0.39, half = size * 0.44, haloScale = 1.32;
        float tipX = (float) (x + dirX * tip), tipY = (float) (y + dirY * tip);
        float backX = (float) (x - dirX * back), backY = (float) (y - dirY * back);
        float leftX = (float) (backX + sideX * half), leftY = (float) (backY + sideY * half);
        float rightX = (float) (backX - sideX * half), rightY = (float) (backY - sideY * half);
        // White halo first: the marker has to stay readable over both dark terrain and bright sand.
        int halo = 0xd8ffffff;
        TacticalColors.triangle(g,
                x + (int) Math.round(dirX * tip * haloScale), y + (int) Math.round(dirY * tip * haloScale),
                x + (int) Math.round(-dirX * back * haloScale + sideX * half * haloScale),
                y + (int) Math.round(-dirY * back * haloScale + sideY * half * haloScale),
                x + (int) Math.round(-dirX * back * haloScale - sideX * half * haloScale),
                y + (int) Math.round(-dirY * back * haloScale - sideY * half * haloScale),
                halo);
        TacticalColors.triangle(g, (int) tipX, (int) tipY, (int) leftX, (int) leftY, (int) rightX, (int) rightY, colour);
    }

}
