from pathlib import Path
r=Path('src/main/java/cn/bfops')
p=r/'Client.java';s=p.read_text('utf-8');s=s.replace('static void send(JsonObject request) {','''static boolean serverSupported() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return false;
        var listener = (net.neoforged.neoforge.common.extensions.ICommonPacketListener)(Object)connection;
        return listener.hasChannel(Network.Request.TYPE) && listener.hasChannel(Network.State.TYPE);
    }

    static void send(JsonObject request) {
        if (!serverSupported()) {
            if (Engine.str(request,"op", "").equals("open") && Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.displayClientMessage(Component.literal("此服务器未启用战地风云，普通游戏不受影响"), true);
            return;
        }''');s=s.replace('JsonObject me = state.has("me") ? state.getAsJsonObject("me") : null;','''if (!serverSupported() && !state.entrySet().isEmpty()) {
                    state = new JsonObject();
                    awards.clear();
                    forgetTerrain();
                }
                JsonObject me = state.has("me") ? state.getAsJsonObject("me") : null;''')
s=s.replace('CombatHud.draw(event.getGuiGraphics(), hudTerrain);','if (!VehicleUi.active()) CombatHud.draw(event.getGuiGraphics(), hudTerrain);')
a=s.index('        private static void drawAwardFeed(');b=s.index('        /** Colour for an award glyph',a)
s=s[:a]+'''        private static void drawAwardFeed(GuiGraphics graphics) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
            long now = System.currentTimeMillis();
            boolean vehicle = VehicleUi.active();
            if (!vehicle && now - hitMarkerAt < HIT_MARKER_MS) {
                int cx = graphics.guiWidth()/2, cy = graphics.guiHeight()/2, size = 5;
                int color = 0xffd9f4f6;
                Ui.line(graphics,cx-size,cy-size,cx-2,cy-2,color,1);
                Ui.line(graphics,cx+size,cy-size,cx+2,cy-2,color,1);
                Ui.line(graphics,cx-size,cy+size,cx-2,cy+2,color,1);
                Ui.line(graphics,cx+size,cy+size,cx+2,cy+2,color,1);
            }
            AwardHud.draw(graphics, now, vehicle, vehicle && VehicleUi.scoped());
        }

''' + s[b:];p.write_text(s,'utf-8')
(r/'VehicleUi.java').write_text('''package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import java.lang.reflect.Field;

/** Optional SBW bridge: no SBW classes in descriptors or class initializers. */
final class VehicleUi {
    private static boolean resolved;
    private static Field zoom;
    private static boolean vehicle(Entity entity) {
        if (entity == null) return false;
        for (Class<?> c = entity.getClass(); c != null; c = c.getSuperclass())
            if (c.getName().equals("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity")) return true;
        return false;
    }
    static boolean active() {
        var mc = Minecraft.getInstance();
        return mc.player != null && (vehicle(mc.player.getVehicle()) || vehicle(mc.player.getRootVehicle())
                || vehicle(mc.getCameraEntity()));
    }
    static boolean scoped() {
        if (!resolved) {
            resolved = true;
            try { zoom = Class.forName("com.atsuishio.superbwarfare.event.ClientEventHandler").getField("zoomVehicle"); }
            catch (ReflectiveOperationException | LinkageError ignored) { }
        }
        if (zoom != null) try { return zoom.getBoolean(null); }
        catch (ReflectiveOperationException ignored) { }
        // Unknown optional versions: first-person vehicle view is treated conservatively.
        return Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }
}
''','utf-8')
(r/'AwardLayout.java').write_text('''package cn.bfops;

import java.util.List;

/** Conservative GUI-pixel exclusion zones; never draw when no whole card fits. */
final class AwardLayout {
    record Rect(int x, int y, int w, int h) {
        boolean overlaps(Rect b) { return x < b.x+b.w && x+w > b.x && y < b.y+b.h && y+h > b.y; }
        boolean fits(int width,int height) { return x>=8 && y>=8 && x+w<=width-8 && y+h<=height-8; }
    }
    static List<Rect> vehicleZones(int w,int h) {
        return List.of(new Rect(0,0,w,42), new Rect(0,Math.max(0,h-110),w,110),
                new Rect(w/2-156,h/2-90,312,180));
    }
    static Rect place(int w,int h,boolean vehicle,int rows,List<Rect> extra) {
        int width = vehicle ? 148 : Math.min(206,w-16), height = rows*34;
        if (width<140 || rows<1) return null;
        var zones = new java.util.ArrayList<>(extra);
        if (vehicle) zones.addAll(vehicleZones(w,h));
        else {
            zones.add(new Rect(0,h-82,w,82)); // objective / hotbar
            zones.add(new Rect(w/2-32,h/2-24,64,48)); // crosshair
        }
        // Prefer below the reticle on foot, right upper corner in vehicles.
        if (!vehicle) {
            Rect preferred = new Rect((w-width)/2,h/2+32,width,height);
            if (clear(preferred,w,h,zones)) return preferred;
        }
        for (int y=46;y+height<=h-8;y+=4) {
            for (int x : new int[]{w-width-8,8}) {
                Rect candidate = new Rect(x,y,width,height);
                if (clear(candidate,w,h,zones)) return candidate;
            }
        }
        return null;
    }
    static boolean clear(Rect rect,int w,int h,List<Rect> zones) {
        return rect.fits(w,h) && zones.stream().noneMatch(rect::overlaps);
    }
}
''','utf-8')
(r/'AwardHud.java').write_text('''package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.List;

/** Original code-drawn tactical cards: no copied game assets. */
final class AwardHud {
    private static long lastFrame;
    static void draw(GuiGraphics g,long now,boolean vehicle,boolean scoped) {
        var mc = Minecraft.getInstance();
        int w=g.guiWidth(),h=g.guiHeight();
        long elapsed=lastFrame==0?0:Math.max(0,Math.min(1000,now-lastFrame));
        lastFrame=now;
        List<AwardLayout.Rect> exclusions=new ArrayList<>();
        if (!vehicle) {
            int size=CombatHud.mapSize(w,h);
            String corner=ClientPrefs.corner(Client.serverMinimapCorner());
            int[] pos=Briefing.minimapOrigin(corner,w,h,size);
            if(!ClientPrefs.minimapHidden()) exclusions.add(new AwardLayout.Rect(pos[0]-2,pos[1]-2,size+4,size+20));
            exclusions.add(new AwardLayout.Rect((w-260)/2,0,260,94));
        }
        int rows=Math.min(vehicle?2:3,Client.awards.size());
        AwardLayout.Rect rect=null;
        while(rows>0 && rect==null) { rect=AwardLayout.place(w,h,vehicle,rows,exclusions); if(rect==null)rows--; }
        if(scoped || rect==null) {
            // Defer up to the existing five-entry cap; no kill is rendered across a vehicle optic.
            var held=new ArrayList<Feed.Popup>();
            for(var p:Client.awards) held.add(new Feed.Popup(p.label(),p.points(),p.bornAt()+Math.min(elapsed,Math.max(0,now-p.bornAt())),p.detail(),p.icon()));
            Client.awards=held;
            return;
        }
        Client.awards=Feed.prune(Client.awards,now);
        for(int i=0;i<Math.min(rows,Client.awards.size());i++) {
            var p=Client.awards.get(i);
            double alpha=Feed.alpha(p,now);
            int x=rect.x(),y=rect.y()+i*34,cw=rect.w();
            int accent=argb("headshot".equals(p.icon())?0xffed5969:0xff65dbe8,alpha);
            int skull=argb("headshot".equals(p.icon())?0xffff5c68:0xffffffff,alpha);
            int edge=argb(0xff3f5964,alpha),ink=argb(0xffdce8ec,alpha);
            g.fill(x,y,x+cw,y+30,argb(0xff09141c,alpha*.86));
            // Open corners and a short accent rail, instead of a heavy opaque panel.
            g.fill(x,y,x+12,y+1,accent); g.fill(x,y,x+1,y+8,accent);
            g.fill(x+cw-12,y+29,x+cw,y+30,edge);g.fill(x+cw-1,y+22,x+cw,y+30,edge);
            FeedIcons.draw(g,p.icon(),x+6,y+7,skull);
            int textX=x+24;
            String points=p.points()>0?"+"+p.points():"";
            int pointsX=x+cw-7-mc.font.width(points);
            g.drawString(mc.font,points,pointsX,y+5,ink,false);
            int labelRoom=Math.max(16,pointsX-textX-8);
            String label=Ui.clip(mc.font,p.label(),labelRoom);
            int labelW=mc.font.width(label)+6;
            g.fill(textX-2,y+3,textX+labelW,y+15,argb(0xff203640,alpha));
            g.drawString(mc.font,label,textX+1,y+5,accent,false);
            g.drawString(mc.font,Ui.clip(mc.font,p.detailText(),cw-32),textX,y+18,ink,false);
            double entry=Math.min(1,Math.max(0,(now-p.bornAt())/160.0));
            g.fill(x,y+29,x+(int)(cw*entry*(1-Feed.progress(p,now))),y+30,accent);
        }
    }
    private static int argb(int color,double alpha) { return ((int)(255*Math.max(0,Math.min(1,alpha)))<<24)|(color&0xffffff); }
}
''','utf-8')
# Sharper, larger original skull mask; other award glyphs retain their identities.
p=r/'FeedIcons.java';s=p.read_text('utf-8');a=s.index('    private static final String[] SKULL = {');b=s.index('    };',a)+6;s=s[:a]+'''    private static final String[] SKULL = {
            "...#####...", "..#######..", ".#########.", ".#########.",
            "##..###..##", "##..###..##", ".####.####.", "..##...##..",
            "..#######..", "...#.#.#...", "...#.#.#..."
    };'''+s[b:];p.write_text(s,'utf-8')
# Package version.
for f in ['build.py','src/main/resources/META-INF/neoforge.mods.toml']:
 p=Path(f);p.write_text(p.read_text('utf-8').replace('0.6.1','0.6.2'),'utf-8')
