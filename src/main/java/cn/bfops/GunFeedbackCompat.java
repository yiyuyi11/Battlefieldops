package cn.bfops;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import java.lang.reflect.Method;

/** Optional gun API integration, without linking TaCZ classes on either physical side. */
final class GunFeedbackCompat {
    private static Class<?> gunType;
    private static boolean resolved;
    static boolean heldGun(ItemStack stack) {
        if(!resolved) {
            resolved=true;
            try { gunType=Class.forName("com.tacz.guns.api.item.IGun"); }
            catch(ClassNotFoundException | LinkageError ignored) { }
        }
        return gunType!=null && !stack.isEmpty() && gunType.isInstance(stack.getItem());
    }
    static boolean nativeMarker(DamageSource source) {
        return source.typeHolder().unwrapKey().map(k->k.location().getNamespace().equals("tacz")).orElse(false);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    static void register() {
        registerReload();
        try {
            Class<? extends Event> type=(Class<? extends Event>)Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre");
            Method victim=type.getMethod("getHurtEntity"),attacker=type.getMethod("getAttacker"),head=type.getMethod("isHeadShot"),gunId=type.getMethod("getGunId"),bullet=type.getMethod("getBullet");
            MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,(Class)type,(java.util.function.Consumer)event->{
                try {
                    if(BattlefieldOps.engine!=null && attacker.invoke(event) instanceof ServerPlayer player
                            && victim.invoke(event) instanceof Entity raw && !raw.level().isClientSide()) {
                        Entity target=raw instanceof net.minecraftforge.entity.PartEntity<?> part?part.getParent():raw;
                        BattlefieldOps.engine.atmosphereHit((Entity)bullet.invoke(event));
                        BattlefieldOps.engine.gunEvidence(player,target,gunId.invoke(event).toString());
                        BattlefieldOps.engine.cosmeticKills.gunHit(target,player,(Boolean)head.invoke(event));
                    }
                } catch(ReflectiveOperationException ex) { BattlefieldOps.LOG.debug("Cannot read TaCZ hit",ex); }
            });
        } catch(ClassNotFoundException ignored) { }
        catch(ReflectiveOperationException | LinkageError ex) { BattlefieldOps.LOG.warn("TaCZ precise headshot bridge unavailable",ex); }
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void registerReload(){
        try{
            Class<? extends Event> type=(Class<? extends Event>)Class.forName("com.tacz.guns.api.event.common.GunReloadEvent");
            Method entity=type.getMethod("getEntity");
            MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,(Class)type,(java.util.function.Consumer)event->{try{if(BattlefieldOps.engine!=null&&entity.invoke(event) instanceof ServerPlayer p)BattlefieldOps.engine.gunReload(p);}catch(ReflectiveOperationException ex){BattlefieldOps.LOG.debug("Cannot read reload event",ex);}});
        }catch(ReflectiveOperationException|LinkageError ex){BattlefieldOps.LOG.debug("TaCZ reload bridge unavailable",ex);}
    }
    static boolean sniperAiming(ServerPlayer player){
        try{
            var stack=player.getMainHandItem();if(!heldGun(stack))return false;
            var id=stack.getItem().getClass().getMethod("getGunId",ItemStack.class).invoke(stack.getItem(),stack);
            Object value=Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getCommonGunIndex",net.minecraft.resources.ResourceLocation.class).invoke(null,id);
            if(!(value instanceof java.util.Optional<?> index)||index.isEmpty()||!"sniper".equals(index.get().getClass().getMethod("getType").invoke(index.get())))return false;
            Class<?> operator=Class.forName("com.tacz.guns.api.entity.IGunOperator");Object op=operator.getMethod("fromLivingEntity",net.minecraft.world.entity.LivingEntity.class).invoke(null,player);
            return Boolean.TRUE.equals(operator.getMethod("getSynIsAiming").invoke(op));
        }catch(ReflectiveOperationException|LinkageError ex){return false;}
    }

}
