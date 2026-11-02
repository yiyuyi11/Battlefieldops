package cn.bfops;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;

/** Denser nearby contours, entirely local and bounded to 120 particles per update. */
final class TacticalParticles {
    private static JsonObject source;
    private static Model.Arena arena;
    private static long lastTick=Long.MIN_VALUE;
    private static int phase;
    private record Edge(Model.Vec a,Model.Vec b,double t,double distance,double y,int color){}
    static void tick(){
        var mc=Minecraft.getInstance();var state=Client.state;
        if(mc.level==null||mc.player==null||!Client.tacticalVoiceAllowed()||!state.has("arena"))return;
        long now=mc.level.getGameTime();if(now==lastTick||now%4!=0)return;lastTick=now;
        JsonObject data=state.getAsJsonObject("arena");
        if(data!=source){source=data;arena=BattlefieldOps.GSON.fromJson(data,Model.Arena.class);}
        if(arena==null||!mc.level.dimension().location().toString().equals(arena.dimension))return;
        int team=Engine.num(state.getAsJsonObject("me"),"team",-1),stage=Engine.num(state,"stage",0);
        int[] budget={400};phase++;
        if(arena.boundaryParticles){
            List<Edge> edges=new ArrayList<>();edges(arena.boundary,edges,mc,mc.player.getY(),0x59bdd8);
            String mobility="I";var root=mc.player.getRootVehicle();
            if(root!=mc.player){
                String id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(root.getType()).toString();
                mobility=state.has("vehicleClasses")?Engine.str(state.getAsJsonObject("vehicleClasses"),id,"G"):"G";
            }
            Model.Zone zone=arena.zone(team,mobility);
            if(zone!=null)edges(zone.boundary,edges,mc,mc.player.getY(),0x59bdd8);
            if(arena.isStaged()&&(zone==null||mobility.equals("I")||!mobility.equals(zone.mobility)))
                for(var sector:arena.sectors)if(sector.index==stage)edges(sector.boundary,edges,mc,mc.player.getY(),0x59bdd8);
            draw(mc,edges,160,budget);
        }
        if(arena.objectiveParticles){
            List<Edge> objectives=new ArrayList<>();
            var flags=state.has("flags")?state.getAsJsonArray("flags"):new com.google.gson.JsonArray();
            for(int i=0;i<arena.points.size();i++){
                var point=arena.points.get(i);if(!CombatHud.active(arena,point,stage))continue;
                int owner=i<flags.size()?Engine.num(flags.get(i).getAsJsonObject(),"owner",-1):-1;
                edges(AreaShape.of(point).outline(),objectives,mc,point.y,owner<0?0x59c7d5:owner==team?0x5ba6ed:0xb56369);
            }
            draw(mc,objectives,160,budget);
            List<Edge> spawns=new ArrayList<>();
            for(var spawn:arena.spawns){
                if(spawn.team!=team)continue;
                if(arena.isStaged()&&spawn.sector!=stage
                        &&!(spawn.allows('G')&&arena.independentVehicleArea(team,"G"))
                        &&!(spawn.allows('A')&&arena.independentVehicleArea(team,"A")))continue;
                edges(AreaShape.of(spawn).outline(),spawns,mc,spawn.y,0x5ba6ed);
            }
            draw(mc,spawns,80,budget);
        }
    }
    private static void edges(List<Model.Vec> polygon,List<Edge> out,Minecraft mc,double y,int color){
        if(polygon==null||polygon.size()<3||Math.abs(y-mc.player.getY())>24)return;
        double x=mc.player.getX(),z=mc.player.getZ();
        for(int i=0;i<polygon.size();i++){
            var a=polygon.get(i);var b=polygon.get((i+1)%polygon.size());
            double dx=b.x()-a.x(),dz=b.z()-a.z(),l=dx*dx+dz*dz;if(l<.001)continue;
            double t=Math.max(0,Math.min(1,((x-a.x())*dx+(z-a.z())*dz)/l));
            double distance=Math.hypot(a.x()+t*dx-x,a.z()+t*dz-z);
            if(distance<=96)out.add(new Edge(a,b,t,distance,y,color));
        }
    }
    private static void draw(Minecraft mc,List<Edge> edges,int limit,int[] budget){
        edges.sort(Comparator.comparingDouble(Edge::distance));
        int start=budget[0];var used=new HashSet<Long>();
        for(int pass=0;pass<17;pass++){
            int j=pass==0?0:pass%2==1?(pass+1)/2:-pass/2;
            for(Edge edge:edges){
                if(start-budget[0]>=limit||budget[0]<=0)return;
                double dx=edge.b.x()-edge.a.x(),dz=edge.b.z()-edge.a.z(),length=Math.hypot(dx,dz);
                double t=edge.t+(j*1.15+(phase%4)*.22)/length;if(t<0||t>1)continue;
                double x=edge.a.x()+dx*t,z=edge.a.z()+dz*t;
                long cell=((long)Math.floor(x*1.5)<<32)^((long)Math.floor(z*1.5)&0xffffffffL);
                if(used.add(cell))emit(mc,x,edge.y,z,edge.color,budget,edges.size()<12&&(j+phase)%5==0&&start-budget[0]+1<limit);
            }
        }
    }
    private static void emit(Minecraft mc,double x,double fallbackY,double z,int color,int[] budget,boolean upper){
        if(budget[0]<=0||Math.hypot(x-mc.player.getX(),z-mc.player.getZ())>96)return;
        var pos=BlockPos.containing(x,fallbackY,z);if(!mc.level.hasChunkAt(pos))return;
        double y=mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ());
        if(Math.abs(y-fallbackY)>12)y=fallbackY;
        var dust=new DustParticleOptions(new org.joml.Vector3f(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f),1.6f);
        mc.level.addParticle(dust,x,y+.18,z,0,.005,0);budget[0]--;
        if(upper&&budget[0]>0){mc.level.addParticle(dust,x,y+.8,z,0,.008,0);budget[0]--;}
    }
    private TacticalParticles(){}
}
