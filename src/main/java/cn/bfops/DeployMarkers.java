package cn.bfops;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Snapshot-authorised deployment markers and the actual team boundary. */
final class DeployMarkers {

    /** Pad id -> seconds left on its cooldown, refreshed from the snapshot each frame. */
    private static final java.util.Map<String, Long> PAD_READY = new java.util.HashMap<>();

    /** The cooldown under a supply icon, so "why did nothing happen" is answerable in the HUD too. */
    private static void padCountdown(GuiGraphics graphics, String label, int cx, int cy, int size) {
        if (label == null) return;
        int space = label.lastIndexOf(' ');
        if (space < 0) return;
        Long readyIn = PAD_READY.get(label.substring(space + 1));
        if (readyIn == null || readyIn <= 0) return;
        var font = Minecraft.getInstance().font;
        String text = readyIn + "s";
        graphics.drawString(font, text, cx - font.width(text) / 2, cy + size + 2, 0xffd9a566, false);
    }

    /** Markers below this depth are too close to the overhead camera to be meaningful. */
    private static final double MIN_DEPTH = 1.0;

    /** Beyond this the marker is skipped: from deploy height, the far corner of a large map is 200+ blocks. */
    private static final double MAX_DEPTH = 4096.0;

    /** Deploy-area mask: black at alpha 0.48, the reference pack's dim for land outside the boundary. */
    private static final int MASK_COLOR = 0x7a000000;

    /**
     * One deploy-phase element, mirroring the reference pack's six kinds.
     *
     * @param kind  {@code CAPTURE}, {@code SPAWN}, {@code TEAMMATE}, {@code DOWNED}, {@code BOUNDARY},
     *              {@code BEACON}, {@code VEHICLE}, {@code AMMO}, {@code MEDICAL} or {@code ENEMY_MARK}
     * @param label short text, or {@code null} for none
     * @param team  owning team, or -1 for neutral
     * @param kit   the member's class id, for teammate markers; {@code ""} when unknown
     * @param squad whether this teammate is running the viewer's class
     */
    private record Element(String kind, double x, double y, double z, String label, int team,
                           boolean emphasised, String kit, boolean squad, float yaw, float progress) {
        Element(String kind, double x, double y, double z, String label, int team, boolean emphasised) {
            this(kind, x, y, z, label, team, emphasised, "", false, 0f, 50f);
        }
    }

    private DeployMarkers() { }

    /** Reused between frames: deploy markers are rebuilt only when the deploy state changes. */
    private static final List<Element> ELEMENTS = new ArrayList<>();

    /** Camera pose the current element list was built for; a change means rebuild. */
    private static String builtFor = "";
    private static JsonObject builtState;
    private static final java.util.Map<Element,JsonObject> markData=new java.util.HashMap<>();
    private static List<Model.Vec> boundary=List.of();
    private static double boundaryY;

    /**
     * Draws every deploy marker.
     *
     * <p>Returns immediately unless the player is dead-and-deploying, which is the only time the overhead
     * camera is up. Checking that first means a live match pays nothing at all for this class.
     */
    /** The spawn id the player has selected, or {@code null}/empty for none. Set by the deploy screen. */
    private static String selectedSpawn = "";

    /** Records which spawn the player is pointing at, so it can be highlighted among the markers. */
    static void select(String spawnId) {
        String next=spawnId==null?"":spawnId;
        if(next.equals(selectedSpawn))return;selectedSpawn=next;
        if(!selectedSpawn.isEmpty())SoundCues.play("select");
    }

    static void syncSelection(String id){selectedSpawn=id==null?"":id;}

    static void draw(GuiGraphics graphics, Minecraft mc) {
        JsonObject state = Client.state;
        if (!DeploymentLock.room(state)) return;
        if (!state.has("me")) return;
        JsonObject me = state.getAsJsonObject("me");
        // Deploying is exactly "in a match but not alive". The server owns that flag; deriving it here from
        // the gamemode would be wrong the moment spectator mode is used for anything else.
        if (!me.has("alive") || me.get("alive").getAsBoolean()) return;

        Camera camera = Projector.camera(mc);
        if (camera == null) return;

        String signature = signature(camera, state);
        if (state != builtState || !signature.equals(builtFor)) {
            builtState=state;
            // Only rebuild when the view actually moved. Under a stationary overhead camera this runs once
            // per deploy, which is what makes a scanline-filled boundary affordable.
            rebuild(state, me);
            builtFor = signature;
        }
        if (ELEMENTS.isEmpty()) return;

        int selfTeam = Engine.num(me, "team", -1);

        // The outside mask goes down first, under the boundary and markers, so the deployable area stays
        // bright and everything around it dims — the boundary only means something once the two sides read
        // differently.
        drawOutsideMask(graphics, mc, camera);

        // The boundary is projected and then drawn as ONE batch. Projecting is unavoidable per vertex — a
        // vertex behind the camera cannot participate in a segment, and that decision is per-vertex — but the
        // drawing is not: a per-sample `fill` cost a few thousand calls per edge, repainting the same pixels
        // once per overlapping sample. `BatchLine` merges the covered runs first and emits one fill per row,
        // which is the "boundary batch drawing" item the handover document lists under P2.
        List<int[]> segments = new ArrayList<>();
        for (int i=0;i<boundary.size();i++) {
            Model.Vec a=boundary.get(i), b=boundary.get((i+1)%boundary.size());
            // Subdivide long edges so partially visible boundaries remain visible.
            int steps=Math.min(512,Math.max(1,(int)Math.ceil(Math.hypot(b.x()-a.x(),b.z()-a.z())/8)));
            for(int j=0;j<steps;j++) {
                double u=j/(double)steps,v=(j+1)/(double)steps;
                var p=Projector.project(mc,camera,a.x()+(b.x()-a.x())*u,boundaryY,a.z()+(b.z()-a.z())*u,1.2f);
                var q=Projector.project(mc,camera,a.x()+(b.x()-a.x())*v,boundaryY,a.z()+(b.z()-a.z())*v,1.2f);
                // Line alpha 0.96 and width 2.5 are the reference pack's boundary constants. Width is
                // rounded to 2 because the fill is whole-pixel; 0.96 alpha is 0xf5.
                if(p!=null&&q!=null)
                    segments.add(new int[] {Math.round(p.x()),Math.round(p.y()),Math.round(q.x()),Math.round(q.y())});
            }
        }
        BatchLine.polyline(graphics, segments, 2, 0xf565b5fa);

        for (Element element : ELEMENTS) {
            if ("BOUNDARY".equals(element.kind())) continue;
            Projector.Screen at = Projector.project(mc, camera, element.x(), element.y(), element.z(), 1.3f);
            if (at == null || at.depth() < MIN_DEPTH || at.depth() > MAX_DEPTH) continue;
            // Enemy marks are always hostile: their "team" field is the marking team, not the target's, so
            // routing them through objective() would colour them friendly blue.
            boolean mate = "TEAMMATE".equals(element.kind()) || "DOWNED".equals(element.kind());
            int color = "ENEMY_MARK".equals(element.kind())
                    ? TacticalColors.ENEMY
                    : mate ? MateMarker.labelTint(element.squad(), "DOWNED".equals(element.kind()))
                    : TacticalColors.objective(element.team(), selfTeam);
            JsonObject timed=markData.get(element);
            if(timed!=null){float alpha=Minimap.fade(timed);if(alpha<=.02f)continue;color=TacticalColors.withAlpha(color,alpha);}
            // The selected spawn gets a visible ring behind it: the marker the player is committing to has
            // to stand apart from the unselected ones, or "did my click register?" is never answered.
            boolean selected = selected(element,state);

            int size = markerSize(element.kind(), at.depth(), element.emphasised());
            if("SPAWN".equals(element.kind())) {
                String deploymentKind="BASE";
                if(state.has("deploySites"))for(var row:state.getAsJsonArray("deploySites")){
                    var site=row.getAsJsonObject();if(Engine.str(site,"id","").equals(element.label())){deploymentKind=Engine.str(site,"kind","BASE");break;}
                }
                if("BASE".equals(deploymentKind))DeployIcon.base(graphics,mc.font,at.x(),at.y(),7.5f,element.team(),color,selected);
                else DeployIcon.spawn(graphics,mc.font,"BEACON".equals(deploymentKind)?"●":"▼",at.x(),at.y(),"BEACON".equals(deploymentKind)?5.2f:7.5f,1,color,selected,false);
            } else if(element.kind().equals("TEAMMATE")&&MateMarker.find(state,element.label())!=null&&!Engine.str(MateMarker.find(state,element.label()),"elite","").isEmpty()){
                HeroIcons.draw(graphics,Engine.str(MateMarker.find(state,element.label()),"elite",""),at.x(),at.y(),16,1);
            } else glyph(graphics, element.kind(), Math.round(at.x()), Math.round(at.y()), size, color,
                    element.kit(), element.squad(), element.yaw(), element.progress(), element.team(), selfTeam, element.label(),selected);
            if(selected)SelectionReticle.draw(graphics,Math.round(at.x()),Math.round(at.y()),10);
            if (element.label() != null && !element.label().isEmpty()) {
                String label=element.label();
                if("SPAWN".equals(element.kind())&&state.has("deploySites"))for(var row:state.getAsJsonArray("deploySites")){
                    var site=row.getAsJsonObject();if(Engine.str(site,"id","").equals(label)){label=DeploymentText.label(label,Engine.str(site,"kind",""),Engine.str(site,"label",""));break;}
                }
                TacticalColors.label(graphics, mc.font, label,
                        (int) at.x(), (int) (at.y() + size + 3), color);
            }
        }
    }

    private static boolean selected(Element e,JsonObject state){
        if(selectedSpawn.isEmpty())return false;
        if(e.kind().equals("SPAWN")&&selectedSpawn.equals(e.label()))return true;
        if(e.kind().equals("CAPTURE"))return selectedSpawn.equals("据点 "+e.label());
        if(!state.has("deploySites"))return false;
        for(var row:state.getAsJsonArray("deploySites")){
            var site=row.getAsJsonObject();if(!selectedSpawn.equals(Engine.str(site,"id","")))continue;
            String kind=Engine.str(site,"kind","");
            if(!e.kind().equals(kind))continue;
            return Math.hypot(site.get("x").getAsDouble()-e.x(),site.get("z").getAsDouble()-e.z())<2;
        }
        return false;
    }

    /**
     * Dims everything outside the team's deployable boundary.
     *
     * <p>The boundary polygon is projected to screen space and the area outside it is filled with the
     * reference pack's half-transparent mask. A scanline fill is used — not a bounding box — so a concave
     * boundary dims exactly the forbidden land and no more. If any vertex is behind the camera the whole
     * mask is skipped for the frame rather than drawn wrong: an overhead deploy camera never produces that,
     * and a wrong dim is worse than no dim.
     */
    private static void drawOutsideMask(GuiGraphics graphics, Minecraft mc, Camera camera) {
        if (boundary.size() < 3) return;
        List<int[]> polygon = new ArrayList<>(boundary.size());
        for (Model.Vec v : boundary) {
            Projector.Screen s = Projector.projectPoly(mc, camera, v.x(), boundaryY, v.z());
            if (s == null) return; // vertex behind the camera: cannot fill correctly this frame
            polygon.add(new int[] { Math.round(s.x()), Math.round(s.y()) });
        }
        PolygonMask.fillOutside(graphics, polygon, 0, 0,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), MASK_COLOR);
    }

    /**
     * Rebuilds the element list from the snapshot.
     *
     * <p>Everything comes from the server snapshot, never from local entity scans. A deploy marker for a
     * teammate has to reflect the same list the deploy list was built from, or the two disagree and the
     * player sees a marker for a slot that is not selectable.
     */
    private static void rebuild(JsonObject state, JsonObject me) {
        ELEMENTS.clear();markData.clear();
        int selfTeam = Engine.num(me, "team", -1);
        Model.Arena arena = FactionArt.arena();
        if (arena == null) return;

        double height = arena.maxY > arena.minY ? (arena.minY + arena.maxY) / 2.0 : 64.0;

        // CAPTURE — every capture point, with its owner and letter.
        if (state.has("flags") && state.get("flags").isJsonArray() && arena.points != null) {
            JsonArray flags = state.getAsJsonArray("flags");
            for (int i = 0; i < arena.points.size(); i++) {
                Model.Point point = arena.points.get(i);
                if(!CombatHud.active(arena,point,Engine.num(state,"stage",0)))continue;
                JsonObject flag=i<flags.size()&&flags.get(i).isJsonObject()?flags.get(i).getAsJsonObject():new JsonObject();
                int owner=Engine.num(flag,"owner",-1);
                ELEMENTS.add(new Element("CAPTURE",point.x,point.y+3.5,point.z,point.id,owner,false,"",false,0,flag.has("progress")?flag.get("progress").getAsFloat():50));
            }
        }

        List<Model.Vec> area=arena.viewArea(selfTeam);
        boundary=area==null?List.of():area;
        boundaryY=height;
        if(state.has("deploySites")&&!state.getAsJsonArray("deploySites").isEmpty())
            boundaryY=state.getAsJsonArray("deploySites").get(0).getAsJsonObject().get("y").getAsDouble()+0.1;

        // SPAWN — the exact sites the server will accept, including capture points this team holds.
        // Taken from the snapshot's own deploySites list rather than recomputed: the list a player picks
        // from has to be the list the server validates against.
        if (state.has("deploySites") && state.get("deploySites").isJsonArray()) {
            for (var element : state.getAsJsonArray("deploySites")) {
                if (!element.isJsonObject()) continue;
                JsonObject site = element.getAsJsonObject();
                String kind = Engine.str(site, "kind", "BASE");
                if(kind.equals("POINT")||kind.equals("TEAMMATE")||kind.equals("VEHICLE"))continue; // Draw the corresponding objective/person/hull once.
                ELEMENTS.add(new Element("SPAWN", Engine.num(site, "x", 0), Engine.num(site, "y", 64)+2,
                        Engine.num(site, "z", 0), Engine.str(site, "id", ""), selfTeam,
                        "AIR".equals(kind)));
            }
        }

        // TEAMMATE — living squadmates, and DOWNED — revivable ones, both from the mate list the map uses.
        // A downed teammate is a deploy consideration (a safe spawn is one next to a teammate who is still
        // up), and drawing them the same as a healthy one hides exactly the difference that matters.
        //
        // The class and the squad flag come from the snapshot rather than being inferred here: the reference
        // pack has one glyph per class in a team and a squad variant, so both are data, and a client-side
        // guess ("same class as me" is the server's answer, not the client's) would be a second rule.
        if (state.has("mates") && state.get("mates").isJsonArray()) {
            for (var element : state.getAsJsonArray("mates")) {
                if (!element.isJsonObject()) continue;
                JsonObject mate = element.getAsJsonObject();
                if (mate.has("hostile") && mate.get("hostile").getAsBoolean()) continue;
                if (mate.has("alive") && !mate.get("alive").getAsBoolean()) continue;
                boolean downed = mate.has("downed") && mate.get("downed").getAsBoolean();
                String kit = Engine.str(mate, "kit", "");
                boolean squad = mate.has("squad") && mate.get("squad").getAsBoolean();
                ELEMENTS.add(new Element(downed ? "DOWNED" : "TEAMMATE",
                        Engine.num(mate, "x", 0), Engine.num(mate,"y",64)+2,
                        Engine.num(mate, "z", 0), Engine.str(mate, "name", null), selfTeam, false,
                        kit, squad, mate.has("yaw")?mate.get("yaw").getAsFloat():0f, 50f));
            }
        }

        if(state.has("vehicles"))for(var item:state.getAsJsonArray("vehicles")) {
            var v=item.getAsJsonObject();
            int team=Engine.num(v,"team",-1);
            if(team!=selfTeam)continue;
            // "载具 · 血 35% · 2/4 座": health and live-vs-capacity occupancy, so the deploy view tells the
            // player which of their team's vehicles needs repair or has a free seat without walking to it.
            StringBuilder label = new StringBuilder("载具");
            if (v.has("health")) label.append(" · 血 ").append(Math.round(v.get("health").getAsFloat() * 100)).append("%");
            if (v.has("seats") && v.has("occupants")) {
                label.append(" · ").append(v.get("occupants").getAsInt()).append("/")
                        .append(v.get("seats").getAsInt()).append(" 座");
            }
            ELEMENTS.add(new Element("VEHICLE",Engine.num(v,"x",0),Engine.num(v,"y",64)+2,Engine.num(v,"z",0),label.toString(),team,false));
        }
        // Supplies are distinguished by what they restore rather than drawn as one blob: an ammo pad and a
        // medical pad answer two different questions ("where do I re-arm" vs "where do I heal"), and
        // collapsing them into one glyph hides the answer until the player is close enough to read the label.
        PAD_READY.clear();
        if(Client.state.has("pads"))for(var item:Client.state.getAsJsonArray("pads")){
            if(!item.isJsonObject())continue;var row=item.getAsJsonObject();
            PAD_READY.put(Engine.str(row,"id",""),(long)Engine.num(row,"readyIn",0));
        }
        for(var supply:arena.supplyPoints()) {
            if(supply.team()>=0 && supply.team()!=selfTeam) continue;
            String kind = supply.health() ? "MEDICAL" : "AMMO";
            String label = (supply.health() ? "医疗" : "弹药") + " " + supply.id();
            ELEMENTS.add(new Element(kind, supply.x(), supply.y()+2, supply.z(), label, supply.team(), false));
        }

        // ENEMY_MARK — the last positions your team called out. These are *enemy contacts*, not beacons:
        // Spotting.mark records where an enemy was seen, and the team field is the marking team (always
        // yours here, because the snapshot is already filtered to your team) — never the marked target's
        // team. Drawing them as a friendly beacon inverted their meaning: a red enemy contact shown in
        // friendly blue. WorldMarkers already draws the same data as enemy markers; this list must agree.
        if (state.has("marks") && state.get("marks").isJsonArray()) {
            for (var element : state.getAsJsonArray("marks")) {
                if (!element.isJsonObject()) continue;
                JsonObject mark = element.getAsJsonObject();
                boolean friendly="MATE".equalsIgnoreCase(Engine.str(mark,"kind","ENEMY"));
                Element marked=new Element(friendly?"MATE_MARK":"ENEMY_MARK", mark.get("x").getAsDouble(), mark.get("y").getAsDouble(),
                        mark.get("z").getAsDouble(),
                        (friendly?"队友呼叫 · ":"敌军标记 · ")+Engine.str(mark,"name",""), friendly?selfTeam:-1, false);
                ELEMENTS.add(marked);markData.put(marked,mark);
            }
        }
    }

    /**
     * A cache key for the current view.
     *
     * <p>Rounded to a tenth of a block and a tenth of a degree: exact equality would rebuild on floating
     * point noise while the camera is nominally still, and coarser rounding would let a moving camera reuse
     * geometry that has drifted visibly.
     */
    private static String signature(Camera camera, JsonObject state) {
        var position = camera.getPosition();
        return new StringBuilder(48)
                .append(Math.round(position.x * 10)).append(',')
                .append(Math.round(position.y * 10)).append(',')
                .append(Math.round(position.z * 10)).append(',')
                .append(Math.round(camera.getYRot() * 10)).append(',')
                .append(Math.round(camera.getXRot() * 10)).append(',')
                .append(Engine.str(state, "arenaId", "")).append(',')
                .append(Engine.num(state, "stage", 0))
                .toString();
    }

    /** Marker half-size in pixels, from depth and kind. */
    private static int markerSize(String kind, double depth, boolean emphasised) {
        int base = switch (kind) {
            case "CAPTURE" -> 9;
            case "AMMO", "MEDICAL", "SPAWN" -> 7;
            case "TEAMMATE" -> 5;
            case "DOWNED" -> 5;
            case "VEHICLE" -> 6;
            case "ENEMY_MARK" -> 5;
            default -> 5;
        };
        // Shrink with depth but never below a legible minimum: from 200 blocks up, everything would
        // otherwise be a one-pixel dot and the overlay would carry no information at all.
        double scaled = base * 28.0 / Math.max(8.0, depth);
        int size = (int) Math.round(Math.max(base * 0.45, Math.min(base, scaled)));
        return emphasised ? size + 2 : size;
    }

    /** A kind-specific glyph. Shape carries the kind; colour carries the team. */
    private static void glyph(GuiGraphics graphics, String kind, int cx, int cy, int size, int color,
                              String kit, boolean squad,float yaw,float progress,int owner,int self,String label,boolean selected) {
        switch (kind) {
            case "CAPTURE" -> DeployIcon.capture(graphics,Minecraft.getInstance().font,label,cx,cy,7.5f,progress,(color>>>24)/255f,owner,self,selected,false);
            case "SPAWN" -> DeployIcon.spawn(graphics,Minecraft.getInstance().font,"▼",cx,cy,7.5f,(color>>>24)/255f,color,selected,false);
            // Reference-pack icons when available; the shape glyphs remain the fallback so the overlay
            // still works without gd656conquest installed.
            case "AMMO" -> {
                if (!AssetIcons.supply(graphics, "AMMO", cx, cy, size)) cross(graphics, cx, cy, size, color);
                padCountdown(graphics, label, cx, cy, size);
            }
            case "MEDICAL" -> {
                if (!AssetIcons.supply(graphics, "MEDICAL", cx, cy, size)) square(graphics, cx, cy, size, color);
                padCountdown(graphics, label, cx, cy, size);
            }
            // Class glyph, tinted for this mod's palette. The reference pack distinguishes a living
            // teammate from a downed one by shape (a person vs. a death mark) and by colour only for
            // team-vs-squad; the art is used exactly that way here. The dot and the hollow ring stay as the
            // fallback for a class with no glyph — an operator can name a class anything, and there is no
            // thirteenth icon.
            case "TEAMMATE" -> DeployIcon.teammate(graphics,Minecraft.getInstance().font,cx,cy,5.2f,yaw,(color>>>24)/255f,color,color,selected,false);
            case "DOWNED" -> {
                if (!MateMarker.draw(graphics, kit, squad, true, cx, cy, Math.max(11, size * 2))) {
                    ring(graphics, cx, cy, Math.max(2, size / 2), color);
                }
            }
            case "VEHICLE" -> DeployIcon.spawn(graphics,Minecraft.getInstance().font,"V",cx,cy,5.2f,(color>>>24)/255f,color,selected,false);
            // A hollow diamond for an enemy's last known position: distinct from the live-capture diamond
            // and from a friendly dot, and it cannot be mistaken for a beacon ring.
            case "ENEMY_MARK" -> TacticalColors.mark(graphics,cx,cy,size,"ENEMY",(color>>>24)/255f);
            case "MATE_MARK" -> TacticalColors.mark(graphics,cx,cy,size,"MATE",(color>>>24)/255f);
            default -> dot(graphics, cx, cy, Math.max(2, size / 2), color);
        }
    }

    private static void diamond(GuiGraphics g, int cx, int cy, int half, int color) {
        for (int dy = -half; dy <= half; dy++) {
            int run = half - Math.abs(dy);
            g.fill(cx - run, cy + dy, cx + run + 1, cy + dy + 1, color);
        }
    }

    private static void cross(GuiGraphics g, int cx, int cy, int half, int color) {
        g.fill(cx - half, cy, cx + half + 1, cy + 1, color);
        g.fill(cx, cy - half, cx + 1, cy + half + 1, color);
    }

    private static void square(GuiGraphics g, int cx, int cy, int half, int color) {
        int h = Math.max(1, half / 2);
        g.fill(cx - h, cy - h, cx + h + 1, cy + h + 1, color);
    }

    private static void dot(GuiGraphics g, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int run = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            g.fill(cx - run, cy + dy, cx + run + 1, cy + dy + 1, color);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int run = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            // Only the rim: two pixels at each end of the row.
            g.fill(cx - run, cy + dy, cx - run + 1, cy + dy + 1, color);
            g.fill(cx + run, cy + dy, cx + run + 1, cy + dy + 1, color);
        }
    }

    private static void disc(GuiGraphics g, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int run = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            g.fill(cx - run, cy + dy, cx + run + 1, cy + dy + 1, color);
        }
    }

    /** Drops cached geometry, e.g. on logout so the next server's deploy does not reuse a stale signature. */
    static void reset() {
        ELEMENTS.clear();
        builtFor = "";
        builtState=null;
    }
}
