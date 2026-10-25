package cn.bfops;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.Event;
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
        try {
            Class<? extends Event> type=(Class<? extends Event>)Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre");
            Method victim=type.getMethod("getHurtEntity"),attacker=type.getMethod("getAttacker"),head=type.getMethod("isHeadShot");
            NeoForge.EVENT_BUS.addListener((Class)type,(java.util.function.Consumer<Event>)event->{
                try {
                    if(BattlefieldOps.engine!=null && attacker.invoke(event) instanceof ServerPlayer player
                            && victim.invoke(event) instanceof Entity target && !target.level().isClientSide())
                        BattlefieldOps.engine.cosmeticKills.gunHit(target,player,(Boolean)head.invoke(event));
                } catch(ReflectiveOperationException ex) { BattlefieldOps.LOG.debug("Cannot read TaCZ hit",ex); }
            });
        } catch(ClassNotFoundException ignored) { }
        catch(ReflectiveOperationException | LinkageError ex) { BattlefieldOps.LOG.warn("TaCZ precise headshot bridge unavailable",ex); }
    }
}
