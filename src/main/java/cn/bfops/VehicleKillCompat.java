package cn.bfops;

import java.lang.reflect.Method;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;

/** Recognizes SBW destroyed wrecks without treating chunk unload or room cleanup as kills. */
final class VehicleKillCompat {
    private record Access(Method health, Method attacker) {}
    private static final ClassValue<java.util.Optional<Access>> ACCESS=new ClassValue<>() {
        protected java.util.Optional<Access> computeValue(Class<?> type) {
            for(Class<?> c=type;c!=null;c=c.getSuperclass()) {
                if(!c.getName().equals("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity"))continue;
                try {return java.util.Optional.of(new Access(type.getMethod("getHealth"),type.getMethod("getLastAttacker")));}
                catch(ReflectiveOperationException ignored){break;}
            }
            return java.util.Optional.empty();
        }
    };
    private static final java.util.Map<Entity,Boolean> aliveSeen = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    static void check(Entity entity) {
        if(entity.level().isClientSide() || BattlefieldOps.engine==null)return;
        var access=ACCESS.get(entity.getClass());
        if(access.isEmpty())return;
        try {
            var a=access.get();
            boolean alive=((Number)a.health.invoke(entity)).floatValue()>0;
            Boolean previous=aliveSeen.put(entity,alive);
            if(!alive && Boolean.TRUE.equals(previous) && a.attacker.invoke(entity) instanceof ServerPlayer player)
                BattlefieldOps.engine.cosmeticKills.report(entity,player,false,"摧毁载具");
        } catch(ReflectiveOperationException ex) { BattlefieldOps.LOG.debug("Cannot read vehicle kill",ex); }
    }
}
