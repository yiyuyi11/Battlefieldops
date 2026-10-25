package cn.bfops;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

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
    private static final int SECTOR = 0xff8a7440;
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

    Minimap terrain(TerrainPreview preview) {
        this.terrain = preview;
        return this;
    }

    private Minimap(int x, int y, int w, int h, double cx, double cz, double span) {
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

    static Minimap local(int x,int y,int size,double playerX,double playerZ,double span){
        return new Minimap(x,y,size,size,Math.floor(playerX/8)*8,Math.floor(playerZ/8)*8,span);
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

    private void line(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int[] clipped=MapGeometry.clip(x0,y0,x1,y1,x,y,x+w-1,y+h-1);
        if(clipped==null)return;
        x0=clipped[0];y0=clipped[1];x1=clipped[2];y1=clipped[3];
        color|=0xff000000;
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int px = x0 + (int) Math.round((x1 - x0) * (i / (double) Math.max(1, steps)));
            int py = y0 + (int) Math.round((y1 - y0) * (i / (double) Math.max(1, steps)));
            graphics.fill(px, py, px + 1, py + 1, color);
        }
    }

    void polygon(GuiGraphics graphics, List<Model.Vec> points, int color) {
        if (points == null || points.size() < 2) return;
        for (int i = 0; i < points.size(); i++) {
            Model.Vec a = points.get(i);
            Model.Vec b = points.get((i + 1) % points.size());
            line(graphics, sx(a.x()), sy(a.z()), sx(b.x()), sy(b.z()), color);
        }
    }

    void ring(GuiGraphics graphics, double worldX, double worldZ, double radius, int color) {
        int steps = 40;
        for (int i = 0; i < steps; i++) {
            double a0 = i * Math.PI * 2 / steps;
            double a1 = (i + 1) * Math.PI * 2 / steps;
            line(graphics,
                    sx(worldX + Math.cos(a0) * radius), sy(worldZ + Math.sin(a0) * radius),
                    sx(worldX + Math.cos(a1) * radius), sy(worldZ + Math.sin(a1) * radius), color);
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
     * @param state   last server snapshot, or {@code null} to draw a map without live ownership
     * @param selfTeam team whose spawns are shown, or -1 to show both
     * @param spawns  whether to draw spawn markers at all
     */
    void draw(GuiGraphics graphics, Font font, Model.Arena arena, JsonObject state, int selfTeam,
              double selfX, double selfZ, float selfYaw, boolean spawns) {
        graphics.fill(x, y, x + w, y + h, PANEL);
        graphics.enableScissor(x, y, x + w, y + h);

        // Real terrain first, when a layer was supplied; the grid then reads as a ruler over the
        // landscape instead of as the whole image.
        if (terrain != null) {
            terrain.draw(graphics, x, y, w, h, cx, cz, span,
                    arena == null || arena.dimension == null ? "" : arena.dimension);
        }

        for (int i = 1; i < 8; i++) {
            if(terrain!=null)break;
            int gx = x + i * w / 8;
            graphics.fill(gx, y, gx + 1, y + h, GRID);
            int gy = y + i * h / 8;
            graphics.fill(x, gy, x + w, gy + 1, GRID);
        }

        if (arena != null) {
            polygon(graphics, arena.boundary, BOUNDARY);
            for (Model.Sector sector : arena.sectors) polygon(graphics, sector.boundary, SECTOR);
            if (spawns) {
                for (Model.Spawn spawn : arena.spawns) {
                    if (selfTeam >= 0 && spawn.team != selfTeam) continue;
                    cross(graphics, spawn.x, spawn.z, 3, color(spawn.team));
                }
            }
        }

        JsonArray flags = array(state, "flags");
        if (arena != null) {
            for (int i = 0; i < arena.points.size(); i++) {
                Model.Point point = arena.points.get(i);
                if(!CombatHud.active(arena,point,state==null?0:Engine.num(state,"stage",0)))continue;
                int owner = -1;
                double progress = 50;
                boolean contested = false;
                if (flags != null && i < flags.size() && flags.get(i).isJsonObject()) {
                    JsonObject flag = flags.get(i).getAsJsonObject();
                    owner = flag.has("owner") ? flag.get("owner").getAsInt() : -1;
                    progress = flag.has("progress") ? flag.get("progress").getAsDouble() : 50;
                    contested = flag.has("contested") && flag.get("contested").getAsBoolean();
                }
                ring(graphics, point.x, point.z, point.radius,
                        owner == 0 ? 0x6677bbff : owner == 1 ? 0x66ff7777 : 0x44cccccc);

                int px = sx(point.x), py = sy(point.z);
                graphics.fill(px - 4, py - 4, px + 5, py + 5, 0xff101820);
                graphics.fill(px - 3, py - 3, px + 4, py + 4, TacticalColors.objective(owner,selfTeam));
                TacticalColors.label(graphics,font,point.id,px,py-15,TacticalColors.objective(owner,selfTeam));

                int barWidth = 22;
                int barLeft = px - barWidth / 2;
                graphics.fill(barLeft - 1, py + 5, barLeft + barWidth + 1, py + 9, 0xff000000);
                int filled = (int) Math.round(barWidth * Math.max(0, Math.min(100, selfTeam==1?100-progress:progress)) / 100.0);
                if (filled > 0) graphics.fill(barLeft, py + 6, barLeft + filled, py + 8, selfTeam<0?BLUE|0xff000000:TacticalColors.FRIEND);
                if (filled < barWidth) graphics.fill(barLeft + filled, py + 6, barLeft + barWidth, py + 8, selfTeam<0?RED|0xff000000:TacticalColors.ENEMY);
                if (contested) TacticalColors.label(graphics,font,"!",px,py+9,TacticalColors.ENEMY);
            }
        }

        // Vehicles: friend/foe only, plus whether the hull is occupied. Drawn after players so a
        // vehicle under a player dot does not hide the person riding it.
        JsonArray vehicles = array(state, "vehicles");
        if (vehicles != null) {
            for (JsonElement element : vehicles) {
                if (!element.isJsonObject()) continue;
                JsonObject vehicle = element.getAsJsonObject();
                if (!vehicle.has("x") || !vehicle.has("z")) continue;
                // Neutral/unknown ownership must read as hostile-looking, not as friendly: colouring
                // an unclaimed hull cool invites teammates to ignore armour that may be the enemy's.
                boolean friendly = vehicle.has("friendly") && vehicle.get("friendly").getAsBoolean();
                int px = sx(vehicle.get("x").getAsDouble());
                int py = sy(vehicle.get("z").getAsDouble());
                int color = friendly ? TacticalColors.FRIEND : TacticalColors.ENEMY;
                // A 5x5 hollow square reads as a vehicle rather than a player dot without needing
                // an icon asset, which keeps this asset-free like the rest of the overlay.
                graphics.fill(px - 3, py - 3, px + 4, py + 4, color);
                graphics.fill(px - 2, py - 2, px + 3, py + 3, 0xff15181c);
                // Occupied hulls get a solid core so an empty truck and a loaded one differ at a
                // glance; that is the difference between "transport available" and "drive on".
                boolean occupied = vehicle.has("occupants") && vehicle.get("occupants").getAsInt() > 0;
                if (occupied) graphics.fill(px - 1, py - 1, px + 2, py + 2, color);
            }
        }

        // Manual marks go under the contacts: a mark indicates where somebody was seen, and a live contact
        // must stay readable on top of it.
        JsonArray marks = array(state, "marks");
        if (marks != null) {
            for (JsonElement element : marks) {
                if (!element.isJsonObject()) continue;
                JsonObject mark = element.getAsJsonObject();
                if (!mark.has("x") || !mark.has("z")) continue;
                int markX = sx(mark.get("x").getAsDouble());
                int markY = sy(mark.get("z").getAsDouble());
                // A ring only: the countdown was removed because it competed with the markers for attention
                // while telling the player something they act on rather than read.
                TacticalColors.mark(graphics, markX, markY, 5, TacticalColors.MARK);
            }
        }

        JsonArray mates = array(state, "mates");
        if (mates != null) {
            for (JsonElement element : mates) {
                if (!element.isJsonObject()) continue;
                JsonObject mate = element.getAsJsonObject();
                if (!mate.has("x") || !mate.has("z")) continue;
                if (mate.has("self") && mate.get("self").getAsBoolean()) continue;
                boolean hostile = mate.has("hostile") && mate.get("hostile").getAsBoolean();
                int px = sx(mate.get("x").getAsDouble());
                int py = sy(mate.get("z").getAsDouble());
                float mateYaw = mate.has("yaw") ? mate.get("yaw").getAsFloat() : 0f;
                if (hostile) {
                    // Enemies are a plain dot: a heading arrow would imply the map knows where they are
                    // looking, which it does not. A dot says only "seen here".
                    TacticalColors.dot(graphics, px, py, 2, TacticalColors.ENEMY);
                } else {
                    // Teammates are arrows, and smaller than the player's own so the two never trade places.
                    TacticalColors.arrow(graphics, px, py, mateYaw, 4, TacticalColors.FRIEND);
                }
                // Riders get a small tick above the marker: knowing a teammate is in an APC changes the
                // decision to push, and it costs one glyph.
                if (mate.has("vehicle")) {
                    graphics.fill(px - 3, py - 5, px + 4, py - 4,
                            hostile ? TacticalColors.ENEMY : TacticalColors.FRIEND);
                }
            }
        }

        // The player's view cone first, so every other marker draws on top of it.
        int selfPx = sx(selfX), selfPy = sy(selfZ);
        double blocksPerPixel = Math.max(1e-6, span / Math.max(1, w));
        // The cone reaches as far as the spotting range does, so what the map shows and what the map is
        // allowed to show agree.
        int coneRadius = (int) Math.min(Math.max(w, h) * 0.45, Tactics.ENEMY_GLOW_RANGE / blocksPerPixel);
        TacticalColors.viewCone(graphics, selfPx, selfPy, selfYaw, VIEW_HALF_ANGLE, coneRadius,
                TacticalColors.VIEW_CONE, TacticalColors.VIEW_EDGE);
        // Own marker: cyan-green, filled, and deliberately smaller than it used to be so a busy map reads as
        // markers rather than as one big triangle.
        TacticalColors.arrow(graphics, selfPx, selfPy, selfYaw, 5, TacticalColors.SELF);

        graphics.disableScissor();
        graphics.fill(x - 1, y - 1, x + w + 1, y, FRAME);
        graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, FRAME);
        graphics.fill(x - 1, y, x, y + h, FRAME);
        graphics.fill(x + w, y, x + w + 1, y + h, FRAME);
    }

    private static JsonArray array(JsonObject state, String key) {
        if (state == null || !state.has(key) || !state.get(key).isJsonArray()) return null;
        return state.getAsJsonArray(key);
    }
}
