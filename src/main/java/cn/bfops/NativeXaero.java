package cn.bfops;

import java.lang.reflect.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import cn.bfops.conquest.client.marker.TacticalMarkerRenderer.MarkerVisualType;

/** Native terrain stays in Xaero's renderer; room-authored overlays use its exact map transform. */
public final class NativeXaero {
    private static Boolean available;
    private static boolean failed;
    private static Field renderPos,mapDimension;
    private static long drawnAt;
    private static JsonElement arenaSource;
    private static Model.Arena arena;
    private static List<List<Model.Vec>> pointShapes=List.of(),spawnShapes=List.of();
    public static boolean available(){
        if(!cn.bfops.compat.BattlefieldMixinPlugin.xaeroCompatible())return false;
        if(available==null)try{
            Class<?> handler=Class.forName("xaero.hud.minimap.element.render.over.MinimapElementOverMapRendererHandler",false,NativeXaero.class.getClassLoader());
            for(String field:List.of("ps","pc","zoom","halfViewW","halfViewH","circle","optionalScale"))handler.getDeclaredField(field);
            Class<?> info=Class.forName("xaero.hud.minimap.element.render.MinimapElementRenderInfo",false,NativeXaero.class.getClassLoader());
            renderPos=info.getField("renderPos");mapDimension=info.getField("mapDimension");available=true;
        }catch(ReflectiveOperationException|LinkageError ex){available=false;}
        return available&&!failed;
    }
    public static boolean room(){return Client.serverSupported()&&Client.state.has("inMatch")&&Client.state.get("inMatch").getAsBoolean();}
    public static boolean present(){return net.neoforged.fml.ModList.get().isLoaded("xaerominimap");}
    public static boolean enabled(){
        var s=Client.state;if(!s.has("hud"))return true;var h=s.getAsJsonObject("hud");
        if(h.has("battleMapEnabled"))return h.get("battleMapEnabled").getAsBoolean();
        return !h.has("minimap")||h.get("minimap").getAsBoolean()||h.has("xaero")&&h.get("xaero").getAsBoolean();
    }
    public static boolean show(){
        var mc=Minecraft.getInstance();var s=Client.state;
        return room()&&mc.player!=null&&mc.screen==null&&!mc.options.hideGui&&!EntryScene.active()&&!ClientPrefs.minimapHidden()
            &&s.has("me")&&s.getAsJsonObject("me").has("alive")&&s.getAsJsonObject("me").get("alive").getAsBoolean()
            &&enabled();
    }
    public static boolean recent(){return available()&&System.nanoTime()-drawnAt<300_000_000L;}
    public static boolean hideNativeInfo(){return available()&&room()&&(ClientPrefs.minimap().squad||ClientPrefs.minimap().coordinates);}
    public static void reset(){drawnAt=0;arenaSource=null;arena=null;}
    public static void draw(GuiGraphics g,Object info,double ps,double pc,double zoom,int halfW,int halfH,boolean circle,float scale){
        if(!available()||!show()||!Double.isFinite(zoom)||zoom<=0||scale<=0||!Client.state.has("arena"))return;
        try{
            var mc=Minecraft.getInstance();if(!mc.level.dimension().equals(mapDimension.get(info)))return;
            Vec3 center=(Vec3)renderPos.get(info);var state=Client.state;
            if(arena!=FactionArt.arena()){
                arenaSource=state.get("arena");arena=FactionArt.arena();
                pointShapes=arena.points.stream().map(v->AreaShape.of(v).outline()).toList();spawnShapes=arena.spawns.stream().map(v->AreaShape.of(v).outline()).toList();
            }
            var me=state.getAsJsonObject("me");int team=Engine.num(me,"team",-1),stage=Engine.num(state,"stage",0);
            float unit=Math.max(.1f,scale);double hw=halfW/unit,hh=halfH/unit;
            Projection p=new Projection(center,ps,pc,zoom/unit,hw-3,hh-3,circle);
            g.flush();g.pose().pushPose();
            try{
                g.pose().scale(unit,unit,1);
                g.pose().translate(0,0,200);
                var options=ClientPrefs.minimap();
                if(options.grid){
                    double reach=Math.hypot(p.hw,p.hh)/p.zoom,step=Layout.gridStep(reach*2,(int)(p.hw*2));
                    if(step>0){
                        for(double x=Math.ceil((center.x-reach)/step)*step;x<center.x+reach;x+=step)boundary(g,p,List.of(new Model.Vec(x,center.z-reach),new Model.Vec(x,center.z+reach)),0x303d687b);
                        for(double z=Math.ceil((center.z-reach)/step)*step;z<center.z+reach;z+=step)boundary(g,p,List.of(new Model.Vec(center.x-reach,z),new Model.Vec(center.x+reach,z)),0x303d687b);
                    }
                }
                String mobility="I";var root=mc.player.getRootVehicle();
                if(root!=mc.player){String id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(root.getType()).toString();mobility=state.has("vehicleClasses")?Engine.str(state.getAsJsonObject("vehicleClasses"),id,"G"):"G";}
                var activeZone=arena.zone(team,mobility);List<Model.Vec> legal=activeZone==null?arena.boundary:activeZone.boundary;
                if(arena.isStaged()&&(activeZone==null||mobility.equals("I")||!mobility.equals(activeZone.mobility)))for(var sector:arena.sectors)if(sector.index==stage&&sector.boundary!=null&&sector.boundary.size()>=3)legal=sector.boundary;
                outside(g,p,legal);
                boundary(g,p,arena.boundary,0xb06bcbd7);
                if(arena.sectors!=null)for(var sector:arena.sectors)if(sector.index==stage)boundary(g,p,sector.boundary,0xd06bcbd7);
                if(arena.zones!=null)for(var zone:arena.zones)if(zone.active()&&zone.team==team)boundary(g,p,zone.boundary,0xb05f9dd4);
                if(options.objectives){
                    var flags=MapIcons.array(state,"flags");
                    for(int i=0;i<arena.points.size();i++){
                        var point=arena.points.get(i);if(!CombatHud.active(arena,point,stage))continue;
                        boundary(g,p,pointShapes.get(i),0x88789dba);
                        int[] at=p.at(point.x,point.z);if(at==null)continue;
                        var flag=i<flags.size()?flags.get(i).getAsJsonObject():new JsonObject();
                        MapIcons.capture(g,mc.font,point.id,at[0],at[1],flag.has("progress")?flag.get("progress").getAsFloat():50,Engine.num(flag,"owner",-1),team,false,0);
                    }
                    for(int i=0;i<arena.spawns.size();i++){var spawn=arena.spawns.get(i);if(spawn.team==team&&(!arena.isStaged()||spawn.sector==stage))boundary(g,p,spawnShapes.get(i),0x805f9dd4);}
                    for(var e:MapIcons.array(state,"deploySites")){
                        var site=e.getAsJsonObject();String kind=Engine.str(site,"kind","BASE");
                        if(!List.of("BASE","AIR","BEACON").contains(kind))continue;
                        int[] at=p.at(site.get("x").getAsDouble(),site.get("z").getAsDouble());if(at!=null)MapIcons.site(g,mc.font,kind,at[0],at[1],false,0);
                    }
                }
                if(options.support)for(var pad:arena.supplyPoints())if(pad.team()<0||pad.team()==team){
                    int[] at=p.at(pad.x(),pad.z());if(at==null)continue;
                    MapIcons.marker(g,at[0],at[1],0,pad.health()?MarkerVisualType.MEDICAL_BOX:MarkerVisualType.AMMO_BOX,"",1);
                }
                MapIcons.contacts(g,mc.font,state,p::at,0,options,(float)Math.toDegrees(Math.atan2(pc,ps)));
                // The self arrow follows the same native rotation; the stock entity radar is suppressed.
                double angle=Math.toRadians(mc.player.getYRot());
                double dx=-Math.sin(angle),dz=Math.cos(angle);
                double ax=(ps*dx-pc*dz),ay=(pc*dx+ps*dz);
                if(options.viewCone)for(double offset:new double[]{-.52,.52}){
                    double vx=dx*Math.cos(offset)-dz*Math.sin(offset),vz=dx*Math.sin(offset)+dz*Math.cos(offset);
                    Ui.line(g,0,0,(int)Math.round((ps*vx-pc*vz)*22),(int)Math.round((pc*vx+ps*vz)*22),0x885f9dd4,1);
                }
                Bf1Players.draw(g,0,0,(float)Math.toDegrees(Math.atan2(ay,ax))-90,true,false,1,PlayerMarkerSize.self(ClientPrefs.selfArrowSize()));
                int y=(int)Math.round(hh)+5,x=-(int)Math.round(hw),width=(int)Math.round(hw*2);
                if(options.coordinates){g.drawString(mc.font,"X "+mc.player.getBlockX()+"  Z "+mc.player.getBlockZ(),x,y,Ui.ACCENT,true);y+=12;}
                if(options.squad){
                    var roster=SquadRoster.rows(state);
                    boolean riding=mc.player.getRootVehicle()!=mc.player;
                    if(riding)SquadRoster.drawAt(g,mc.font,roster,-(int)Math.round(hw),(int)Math.round(hh)+6,Engine.num(me,"squad",0));
                    else SquadRoster.drawAt(g,mc.font,roster,(int)Math.round(hw)+6,2,Engine.num(me,"squad",0));
                }
                drawnAt=System.nanoTime();
            }finally{g.flush();g.pose().popPose();}
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ex){
            failed=true;BattlefieldOps.LOG.warn("Native Xaero overlay unavailable; using battlefield fallback minimap",ex);
        }
    }
    private record Projection(Vec3 center,double ps,double pc,double zoom,double hw,double hh,boolean circle){
        double[] raw(double x,double z){double dx=x-center.x,dz=z-center.z;return new double[]{(ps*dx-pc*dz)*zoom,(pc*dx+ps*dz)*zoom};}
        boolean inside(double x,double y,double margin){double a=Math.max(1,hw-margin),b=Math.max(1,hh-margin);return Math.abs(x)<=a&&Math.abs(y)<=b&&(!circle||x*x+y*y<=Math.min(a,b)*Math.min(a,b));}
        int[] at(Double x,Double z){double[] at=raw(x,z);return inside(at[0],at[1],8)?new int[]{(int)Math.round(at[0]),(int)Math.round(at[1])}:null;}
    }
    private static void boundary(GuiGraphics g,Projection p,List<Model.Vec> polygon,int color){
        if(polygon==null||polygon.size()<2)return;
        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minY=minX,maxY=maxX;
        for(var v:polygon){double dx=v.x()-p.center.x,dz=v.z()-p.center.z,x=(p.ps*dx-p.pc*dz)*p.zoom,y=(p.pc*dx+p.ps*dz)*p.zoom;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        if(minX>p.hw||maxX< -p.hw||minY>p.hh||maxY< -p.hh)return;
        var lines=new ArrayList<int[]>();
        for(int i=0;i<polygon.size();i++){
            var a=polygon.get(i);var b=polygon.get((i+1)%polygon.size());double[] aa=p.raw(a.x(),a.z()),bb=p.raw(b.x(),b.z());
            double x=aa[0],y=aa[1],dx=bb[0]-x,dy=bb[1]-y,lo=0,hi=1;
            double[] pp={-dx,dx,-dy,dy},qq={x+p.hw,p.hw-x,y+p.hh,p.hh-y};boolean reject=false;
            for(int k=0;k<4;k++){if(Math.abs(pp[k])<1e-9){if(qq[k]<0){reject=true;break;}}else{double t=qq[k]/pp[k];if(pp[k]<0)lo=Math.max(lo,t);else hi=Math.min(hi,t);}}
            if(reject||hi<lo)continue;
            if(p.circle){double radius=Math.min(p.hw,p.hh),v=dx*dx+dy*dy;if(v<1e-9)continue;
                double bq=2*(x*dx+y*dy),cq=x*x+y*y-radius*radius,disc=bq*bq-4*v*cq;if(disc<0)continue;
                double root=Math.sqrt(disc);lo=Math.max(lo,(-bq-root)/(2*v));hi=Math.min(hi,(-bq+root)/(2*v));if(hi<lo)continue;
            }
            lines.add(new int[]{(int)Math.round(x+lo*dx),(int)Math.round(y+lo*dy),(int)Math.round(x+hi*dx),(int)Math.round(y+hi*dy)});
        }
        BatchLine.polyline(g,lines,1,color);
    }
    private static void outside(GuiGraphics g,Projection p,List<Model.Vec> polygon){
        if(polygon==null||polygon.size()<3)return;
        double[][] vertices=new double[polygon.size()][];for(int i=0;i<vertices.length;i++){var v=polygon.get(i);vertices[i]=p.raw(v.x(),v.z());}
        double[] crossings=new double[vertices.length];
        for(int y=(int)Math.ceil(-p.hh);y<p.hh;y+=3){
            int bottom=(int)Math.min(p.hh,y+3);double limit=p.hw;
            if(p.circle){double r=Math.min(p.hw,p.hh),edge=Math.max(Math.abs(y),Math.abs(bottom));if(edge>=r)continue;limit=Math.min(limit,Math.sqrt(r*r-edge*edge));}
            int left=(int)Math.ceil(-limit),right=(int)Math.floor(limit),n=0;double sample=(y+bottom)/2.0;
            for(int i=0,j=vertices.length-1;i<vertices.length;j=i++){
                var a=vertices[j];var b=vertices[i];if((a[1]>sample)!=(b[1]>sample))crossings[n++]=a[0]+(sample-a[1])*(b[0]-a[0])/(b[1]-a[1]);
            }
            Arrays.sort(crossings,0,n);int cursor=left;
            for(int i=0;i+1<n;i+=2){int start=Math.clamp((int)Math.floor(crossings[i]),left,right),end=Math.clamp((int)Math.ceil(crossings[i+1]),left,right);if(start>cursor)g.fill(cursor,y,start,bottom,0x44000000);cursor=Math.max(cursor,end);}
            if(cursor<right)g.fill(cursor,y,right,bottom,0x44000000);
        }
    }
    static boolean zoom(boolean in){
        if(!available())return false;
        try{
            Object manager=XaeroGate.configManager();if(manager==null)return false;
            Class<?> optionClass=Class.forName("xaero.lib.common.config.option.ConfigOption"),config=Class.forName("xaero.lib.common.config.Config");
            Object profile=manager.getClass().getMethod("getCurrentProfile").invoke(manager);
            Object option=Class.forName("xaero.hud.minimap.common.config.option.MinimapProfiledConfigOptions").getField("ZOOM").get(null);
            Object value=config.getMethod("get",optionClass).invoke(profile,option);
            if(!(value instanceof Integer level))return false;
            config.getMethod("set",optionClass,Object.class).invoke(profile,option,Math.clamp(level+(in?1:-1),1,6));return true;
        }catch(ReflectiveOperationException|RuntimeException ex){BattlefieldOps.LOG.debug("Xaero zoom control unavailable",ex);return false;}
    }
    static void settings(net.minecraft.client.gui.screens.Screen parent){
        try{Minecraft.getInstance().setScreen((net.minecraft.client.gui.screens.Screen)Class.forName("xaero.common.gui.GuiMinimapMain").getConstructor(net.minecraft.client.gui.screens.Screen.class).newInstance(parent));}
        catch(ReflectiveOperationException|RuntimeException ex){BattlefieldOps.LOG.warn("Cannot open Xaero settings",ex);}
    }
    private NativeXaero(){}
}
