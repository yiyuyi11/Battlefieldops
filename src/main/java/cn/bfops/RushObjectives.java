package cn.bfops;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;

/** Bomb objectives for the independent RUSH mode. All progress belongs to the server. */
final class RushObjectives {
    private static final class Charge {long armed=-1,lease;UUID planter,operator;int progress,team=-1;}
    private static final class Room {int stage;final Map<Integer,Charge> charges=new HashMap<>();}
    private final Map<String,Room> rooms=new HashMap<>();
    private final Engine engine;private final MinecraftServer server;
    RushObjectives(Engine engine,MinecraftServer server){this.engine=engine;this.server=server;}
    private boolean valid(ServerPlayer p,Engine.Member m,Model.Arena map,Model.Point point){
        if(p==null||!engine.supportActive(m)||!m.alive||m.downed||!p.getMainHandItem().isEmpty()||p.isPassenger()||p.isSpectator()||!p.level().dimension().location().toString().equals(map.dimension))return false;
        Vec3 target=new Vec3(point.x,point.y+1,point.z),eye=p.getEyePosition();
        if(eye.distanceToSqr(target)>25||p.getLookAngle().dot(target.subtract(eye).normalize())<.7)return false;
        var ray=p.serverLevel().clip(new ClipContext(eye,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        return ray.getType()!=HitResult.Type.BLOCK||ray.getLocation().distanceToSqr(target)<1;
    }
    void interact(ServerPlayer p,long tick){
        Engine.Member m=engine.members.get(p.getUUID());if(m==null)return;Rules rules=engine.rushRules(m.map);if(rules==null)return;
        Room room=rooms.computeIfAbsent(m.map,k->new Room());
        if(room.stage!=rules.stage){room.stage=rules.stage;room.charges.clear();}
        for(int i=0;i<rules.map.points.size();i++){
            if(!rules.active(i)||rules.flags.get(i).owner==0||!valid(p,m,rules.map,rules.map.points.get(i)))continue;
            Charge charge=room.charges.computeIfAbsent(i,k->new Charge());boolean defuse=charge.armed>=0;
            if(m.team!=(defuse?1:0))return;
            if(charge.operator!=null&&!charge.operator.equals(p.getUUID())&&tick<=charge.lease)return;
            if(!p.getUUID().equals(charge.operator)||tick>charge.lease)charge.progress=0;
            charge.operator=p.getUUID();charge.team=m.team;charge.lease=tick+8;return;
        }
    }
    void tick(long tick){
        for(var entry:new ArrayList<>(rooms.entrySet())){
            Rules rules=engine.rushRules(entry.getKey());if(rules==null){rooms.remove(entry.getKey());continue;}
            Room room=entry.getValue();if(room.stage!=rules.stage){room.stage=rules.stage;room.charges.clear();}
            rules.rushArmed=false;
            for(var e:room.charges.entrySet()){
                int index=e.getKey();Charge c=e.getValue();if(!rules.active(index)||rules.flags.get(index).owner==0)continue;
                if(c.operator!=null){ServerPlayer p=server.getPlayerList().getPlayer(c.operator);Engine.Member m=p==null?null:engine.members.get(c.operator);
                    if(tick>c.lease||!valid(p,m,rules.map,rules.map.points.get(index))||!m.map.equals(entry.getKey())||m.team!=c.team){c.operator=null;c.progress=0;}
                    else {
                        c.progress++;boolean defuse=c.armed>=0;int duration=(defuse?rules.map.rushDefuseSeconds:rules.map.rushPlantSeconds)*20;
                        if(c.progress>=duration){
                            if(defuse){c.armed=-1;c.planter=null;engine.rushAward(p,m,"bomb_technician");notice(entry.getKey(),"炸点 "+rules.map.points.get(index).id+" 已拆除");}
                            else {c.armed=tick;c.planter=p.getUUID();engine.rushPlant(p,m);notice(entry.getKey(),"炸点 "+rules.map.points.get(index).id+" 已启动");}
                            c.operator=null;c.progress=0;
                        }
                    }
                }
                if(c.armed>=0&&tick-c.armed>=rules.map.rushFuseSeconds*20L){
                    Rules.Flag flag=rules.flags.get(index);flag.owner=0;flag.progress=100;flag.driving=-1;flag.contested=false;
                    ServerPlayer p=c.planter==null?null:server.getPlayerList().getPlayer(c.planter);Engine.Member m=p==null?null:engine.members.get(p.getUUID());
                    if(m!=null&&m.map.equals(entry.getKey())&&m.team==0)engine.rushAward(p,m,"detonator");
                    c.armed=-1;c.operator=null;c.progress=0;notice(entry.getKey(),"炸点 "+rules.map.points.get(index).id+" 已摧毁");
                }
                if(c.armed>=0)rules.rushArmed=true;
            }
        }
    }
    private void notice(String map,String text){for(ServerPlayer p:server.getPlayerList().getPlayers()){var m=engine.members.get(p.getUUID());if(m!=null&&m.map.equals(map))p.displayClientMessage(net.minecraft.network.chat.Component.literal(text),true);}}
    List<Map<String,Object>> snapshot(String map,long tick,int viewerTeam){
        var room=rooms.get(map);if(room==null)return List.of();Rules rules=engine.rushRules(map);if(rules==null)return List.of();
        List<Map<String,Object>> list=new ArrayList<>();for(var e:room.charges.entrySet()){
            if(!rules.active(e.getKey())||rules.flags.get(e.getKey()).owner==0)continue;
            var c=e.getValue();list.add(Map.of("point",e.getKey(),"armed",c.armed>=0,"remaining",c.armed<0?0:Math.max(0,(rules.map.rushFuseSeconds*20-(tick-c.armed)+19)/20),"progress",c.team==viewerTeam?c.progress:0,"duration",(c.armed>=0?rules.map.rushDefuseSeconds:rules.map.rushPlantSeconds)*20,"team",c.team));
        }return list;
    }
    void close(String map){rooms.remove(map);}
}
