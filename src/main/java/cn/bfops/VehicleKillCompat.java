package cn.bfops;

import java.lang.reflect.Method;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;

/** Recognizes SBW destroyed wrecks without treating chunk unload or room cleanup as kills. */
final class VehicleKillCompat {
    private record Access(Method health, Method attacker, Method damageStamp) {}
    private static final ClassValue<java.util.Optional<Access>> ACCESS=new ClassValue<>() {
        protected java.util.Optional<Access> computeValue(Class<?> type) {
            for(Class<?> c=type;c!=null;c=c.getSuperclass()) {
                if(!c.getName().equals("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity"))continue;
                try {return java.util.Optional.of(new Access(type.getMethod("getHealth"),type.getMethod("getLastAttacker"),type.getMethod("getLastDamageStamp")));}
                catch(ReflectiveOperationException ignored){break;}
            }
            return java.util.Optional.empty();
        }
    };
    private static final java.util.Map<Entity,Boolean> aliveSeen = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final java.util.Map<Entity,Float> healthSeen=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    static float health(Entity e){var access=ACCESS.get(e.getClass());try{return access.isPresent()?((Number)access.get().health.invoke(e)).floatValue():Float.NaN;}catch(ReflectiveOperationException ex){return Float.NaN;}}
    static ServerPlayer attacker(net.minecraft.world.damagesource.DamageSource source){
        if(source.getEntity() instanceof ServerPlayer p)return p;
        for(Entity e:new Entity[]{source.getEntity(),source.getDirectEntity()})if(e!=null&&ACCESS.get(e.getClass()).isPresent()){
            if(e.getControllingPassenger() instanceof ServerPlayer p)return p;
            if(e.getFirstPassenger() instanceof ServerPlayer p)try{if(((Number)e.getClass().getMethod("getSeatIndex",Entity.class).invoke(e,p)).intValue()==0)return p;}catch(ReflectiveOperationException ignored){}
        }
        return null;
    }
    static Entity collisionVehicle(net.minecraft.world.damagesource.DamageSource source){
        for(Entity e:new Entity[]{source.getDirectEntity(),source.getEntity()})if(e!=null&&ACCESS.get(e.getClass()).isPresent())return e;
        return null;
    }
    static void check(Entity entity) {
        if(entity.level().isClientSide() || BattlefieldOps.engine==null)return;
        var access=ACCESS.get(entity.getClass());
        if(access.isEmpty())return;
        try {
            var a=access.get();
            float health=((Number)a.health.invoke(entity)).floatValue();boolean alive=health>0;
            Float before=healthSeen.put(entity,health);
            if(before!=null&&health<before&&entity.level().getGameTime()-((Number)a.damageStamp.invoke(entity)).longValue()>=0&&entity.level().getGameTime()-((Number)a.damageStamp.invoke(entity)).longValue()<=2&&a.attacker.invoke(entity) instanceof ServerPlayer player)BattlefieldOps.engine.vehicleDamage(player,entity,before-Math.max(0,health));
            Boolean previous=aliveSeen.put(entity,alive);
            if(!alive && Boolean.TRUE.equals(previous) && a.attacker.invoke(entity) instanceof ServerPlayer player){
                BattlefieldOps.engine.cosmeticKills.report(entity,player,false,"摧毁载具");
                BattlefieldOps.engine.vehicleDestroyed(player,entity);
            }
        } catch(ReflectiveOperationException ex) { BattlefieldOps.LOG.debug("Cannot read vehicle kill",ex); }
    }
}
