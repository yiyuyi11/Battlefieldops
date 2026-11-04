package cn.bfops;

import java.util.*;
import java.lang.reflect.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.common.MinecraftForge;

/** Source-event adapters for real repair gains and mine provenance, with bounded bookkeeping. */
final class FinalHonours {
    private record Repair(String map,UUID vehicle,float health,long at){}
    private record Mine(UUID owner,String map,Vec3 at,long time){}
    private final Map<UUID,Repair> repairs=new HashMap<>();
    private final Map<UUID,Long> cooldowns=new HashMap<>();
    private final Deque<Mine> mines=new ArrayDeque<>();
    private final Engine engine;private final MinecraftServer server;
    FinalHonours(Engine engine,MinecraftServer server){this.engine=engine;this.server=server;}
    void repair(ServerPlayer p,boolean before){
        if(!before){
            Repair r=repairs.remove(p.getUUID());if(r==null||System.currentTimeMillis()-r.at>500)return;
            Entity target=p.serverLevel().getEntity(r.vehicle);
            if(target!=null&&VehicleKillCompat.health(target)>r.health+.001f&&System.currentTimeMillis()-cooldowns.getOrDefault(p.getUUID(),0L)>=2000){
                if(engine.repairAward(p,target,r.map)){cooldowns.put(p.getUUID(),System.currentTimeMillis());}
            }return;
        }
        repairs.remove(p.getUUID());var m=engine.members.get(p.getUUID());if(!engine.supportActive(m)||!m.alive||m.downed||p.isShiftKeyDown())return;
        Vec3 eye=p.getEyePosition(),end=eye.add(p.getLookAngle().scale(5));
        var result=ProjectileUtil.getEntityHitResult(p,eye,end,p.getBoundingBox().expandTowards(p.getLookAngle().scale(5)).inflate(1),e->Float.isFinite(VehicleKillCompat.health(e)),25);
        if(result==null)return;Entity hull=result.getEntity();
        if(!engine.friendlyHull(p,hull))return;
        var obstruction=p.serverLevel().clip(new net.minecraft.world.level.ClipContext(eye,result.getLocation(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
        if(obstruction.getType()==HitResult.Type.BLOCK&&obstruction.getLocation().distanceToSqr(eye)+.01<result.getLocation().distanceToSqr(eye))return;
        repairs.put(p.getUUID(),new Repair(m.map,hull.getUUID(),VehicleKillCompat.health(hull),System.currentTimeMillis()));
    }
    void mineLeft(Entity entity){
        if(entity.level().isClientSide()||!BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("superbwarfare:tm_62")||entity.getRemovalReason()==null||!entity.getRemovalReason().shouldDestroy())return;
        try{Object owner=entity.getClass().getMethod("getOwnerUUID").invoke(entity);if(owner instanceof UUID id){var m=engine.members.get(id);if(engine.supportActive(m)){mines.addLast(new Mine(id,m.map,entity.position(),System.currentTimeMillis()));while(mines.size()>512)mines.remove(0);}}}catch(ReflectiveOperationException ignored){}
    }
    boolean mineKill(ServerPlayer p,Entity target){
        long now=System.currentTimeMillis();while(!mines.isEmpty()&&now-mines.peekFirst().time>3000)mines.remove(0);
        var m=engine.members.get(p.getUUID());if(m==null)return false;
        try{
            var source=(net.minecraft.world.damagesource.DamageSource)target.getClass().getMethod("getLastDamageSource").invoke(target);
            if(source==null||!source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION))return false;
            return mines.stream().anyMatch(mine->mine.owner.equals(p.getUUID())&&mine.map.equals(m.map)&&mine.at.distanceToSqr(target.position())<=144);
        }catch(ReflectiveOperationException ignored){return false;}
    }
    void leave(UUID id){repairs.remove(id);cooldowns.remove(id);mines.removeIf(m->m.owner.equals(id));}
    @SuppressWarnings({"unchecked","rawtypes"})
    static void register(){for(String phase:new String[]{"Pre","Post"})try{
        Class<? extends Event> type=(Class<? extends Event>)Class.forName("com.atsuishio.superbwarfare.api.event.ShootEvent$"+phase);
        Method shooter=type.getMethod("getShooter"),data=type.getMethod("getData");
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,false,(Class)type,(java.util.function.Consumer<Event>)event->{try{
            if(BattlefieldOps.engine==null||!(shooter.invoke(event) instanceof ServerPlayer p))return;
            if(phase.equals("Pre")&&BattlefieldOps.engine.isDowned(p)){
                if(event.isCancelable())event.setCanceled(true);
                return;
            }
            Object gun=data.invoke(event);Object item=gun.getClass().getMethod("item").invoke(gun);boolean tool=false;
            for(Class<?> c=item.getClass();c!=null;c=c.getSuperclass())if(c.getName().equals("com.atsuishio.superbwarfare.item.gun.special.RepairToolItem")){tool=true;break;}
            if(tool)BattlefieldOps.engine.finalHonours.repair(p,phase.equals("Pre"));
        }catch(ReflectiveOperationException ex){BattlefieldOps.LOG.debug("Cannot read SBW repair event",ex);}});
    }catch(ClassNotFoundException ignored){}catch(ReflectiveOperationException ex){BattlefieldOps.LOG.warn("SBW repair honour adapter unavailable",ex);}}
}
