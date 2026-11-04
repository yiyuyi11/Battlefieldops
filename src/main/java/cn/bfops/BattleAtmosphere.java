package cn.bfops;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;

/** Actual swept near misses, bounded per tick; no client-provided suppression claims. */
final class BattleAtmosphere {
    private static final class Trail {Vec3 previous;boolean hit,credited;final Set<UUID> heard=new HashSet<>();Trail(Entity e){previous=e.position().subtract(e.getDeltaMovement());}}
    private final Map<Entity,Trail> trails=new WeakHashMap<>();
    private static final ClassValue<Optional<java.lang.reflect.Method>> GUN=new ClassValue<>(){protected Optional<java.lang.reflect.Method> computeValue(Class<?> type){for(String name:new String[]{"getGunId","getGunItemId"})try{return Optional.of(type.getMethod(name));}catch(NoSuchMethodException ignored){}return Optional.empty();}};
    private final Map<UUID,Long> sounds=new HashMap<>();
    private long tick=-1;private int work;
    private final Engine engine;
    BattleAtmosphere(Engine engine){this.engine=engine;}
    void hit(Entity bullet){if(bullet!=null)trails.computeIfAbsent(bullet,Trail::new).hit=true;}
    void tick(Entity raw,long now){
        if(raw.level().isClientSide()||!(raw instanceof Projectile bullet))return;
        String name=raw.getClass().getName();if(!name.startsWith("com.tacz.guns.")&&!name.startsWith("com.atsuishio.superbwarfare."))return;
        Entity owner=bullet.getOwner();ServerPlayer shooter=owner instanceof ServerPlayer p?p:owner!=null&&owner.getControllingPassenger() instanceof ServerPlayer p?p:null;
        if(shooter==null)return;Engine.Member member=engine.members.get(shooter.getUUID());if(!engine.supportActive(member)||!member.alive||member.downed)return;
        Trail t=trails.computeIfAbsent(raw,Trail::new);Vec3 start=t.previous,end=raw.position();t.previous=end;
        if(t.hit||start.distanceToSqr(end)<.0001||!Double.isFinite(start.distanceToSqr(end))||start.distanceToSqr(end)>65536)return;
        if(tick!=now){tick=now;work=0;}if(++work>256)return;
        String category="rifle";
        try{var method=GUN.get(raw.getClass());if(method.isPresent()){Object id=method.get().invoke(raw);if(id!=null)category=engine.gunCategory(id.toString());}}catch(ReflectiveOperationException ignored){}
        Vec3 line=end.subtract(start);double length=line.lengthSqr();
        for(ServerPlayer target:shooter.serverLevel().getEntitiesOfClass(ServerPlayer.class,new AABB(start,end).inflate(4))){
            Engine.Member other=engine.members.get(target.getUUID());
            if(target==shooter||!engine.supportActive(other)||!other.map.equals(member.map)||other.team==member.team||!other.alive||other.downed||target.isSpectator()||t.heard.contains(target.getUUID()))continue;
            Vec3 eye=target.getEyePosition();double at=cn.bfops.Java17.clamp(eye.subtract(start).dot(line)/length,0,1);Vec3 near=start.add(line.scale(at));
            if(near.distanceToSqr(eye)>16||!target.getBoundingBox().inflate(2).clip(start,end).isPresent())continue;
            var obstruction=target.serverLevel().clip(new ClipContext(start,near,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,raw));
            if(obstruction.getType()==HitResult.Type.BLOCK&&obstruction.getLocation().distanceToSqr(start)+.01<near.distanceToSqr(start))continue;
            t.heard.add(target.getUUID());
            long previous=sounds.getOrDefault(target.getUUID(),-100L);
            if(now-previous>=2){sounds.put(target.getUUID(),now);Network.send(target,Map.of("type","nearMiss","category",category,"x",near.x,"y",near.y,"z",near.z));}
            if(!t.credited){engine.suppression(shooter,target);t.credited=true;}
        }
    }
    void leave(UUID id){sounds.remove(id);}
}
