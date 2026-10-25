from pathlib import Path
r=Path('src/main/java/cn/bfops')
(r/'CosmeticKills.java').write_text('''package cn.bfops;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import java.util.*;

/** Animation-only notifications. Deliberately has no access to Engine.Member or match rules. */
final class CosmeticKills {
    private record Pending(LivingEntity victim, ServerPlayer killer, boolean headshot) {}
    private record GunHit(UUID shooter, long tick, boolean headshot) {}
    private final Map<UUID,Pending> pending = new HashMap<>();
    private final Map<UUID,GunHit> gunHits = new HashMap<>();
    private final Set<Entity> reported = Collections.newSetFromMap(new WeakHashMap<>());
    private long tick;

    void gunHit(Entity victim, ServerPlayer shooter, boolean headshot) {
        gunHits.put(victim.getUUID(),new GunHit(shooter.getUUID(),tick,headshot));
    }
    boolean headshot(Entity victim,ServerPlayer killer,boolean fallback) {
        GunHit hit=gunHits.get(victim.getUUID());
        return hit!=null && killer!=null && hit.shooter.equals(killer.getUUID()) && tick-hit.tick<=2
                ? hit.headshot : fallback;
    }
    void queue(LivingEntity victim,DamageSource source) {
        if(victim instanceof Player || victim.level().isClientSide())return;
        ServerPlayer killer=source.getEntity() instanceof ServerPlayer p?p:
                victim.getKillCredit() instanceof ServerPlayer p?p:null;
        if(killer==null)return;
        pending.put(victim.getUUID(),new Pending(victim,killer,headshot(victim,killer,false)));
    }
    void tick() {
        tick++;
        for(Pending p:pending.values()) {
            // Totems / canceled deaths / mod resurrection are not a kill notification.
            if(p.victim.getHealth()<=0) report(p.victim,p.killer,p.headshot,"击杀");
        }
        pending.clear();
        gunHits.entrySet().removeIf(e->tick-e.getValue().tick>2);
    }
    void report(Entity victim,ServerPlayer killer,boolean headshot,String label) {
        if(victim instanceof Player || killer==null || !Network.supported(killer) || !reported.add(victim))return;
        Network.send(killer,Map.of("type","bonus","label",headshot?"爆头":label,"points",0,
                "detail",victim.getDisplayName().getString(),"icon",headshot?"headshot":"kill","cosmetic",true));
    }
}
''','utf-8')
(r/'GunFeedbackCompat.java').write_text('''package cn.bfops;

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
''','utf-8')
(r/'VehicleKillCompat.java').write_text('''package cn.bfops;

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
    static void check(Entity entity) {
        if(entity.level().isClientSide() || BattlefieldOps.engine==null)return;
        var access=ACCESS.get(entity.getClass());
        if(access.isEmpty())return;
        try {
            var a=access.get();
            if(((Number)a.health.invoke(entity)).floatValue()<=0 && a.attacker.invoke(entity) instanceof ServerPlayer player)
                BattlefieldOps.engine.cosmeticKills.report(entity,player,false,"摧毁载具");
        } catch(ReflectiveOperationException ex) { BattlefieldOps.LOG.debug("Cannot read vehicle kill",ex); }
    }
}
''','utf-8')
p=r/'BattlefieldOps.java';s=p.read_text('utf-8').replace('bus.addListener(Network::register);','bus.addListener(Network::register);\n        GunFeedbackCompat.register();\n        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (LivingDeathEvent e)->{if(engine!=null&&!e.isCanceled())engine.cosmeticKills.queue(e.getEntity(),e.getSource());});\n        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.EntityTickEvent.Post e)->VehicleKillCompat.check(e.getEntity()));\n        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e)->VehicleKillCompat.check(e.getEntity()));');s=s.replace('engine.hitNotice(attacker,p,e.getAmount(),hitY);','engine.hitNotice(attacker,p,e.getAmount(),hitY,GunFeedbackCompat.nativeMarker(e.getSource()));');p.write_text(s,'utf-8')
p=r/'Engine.java';s=p.read_text('utf-8');s=s.replace('public void tick() {','final CosmeticKills cosmeticKills = new CosmeticKills();\n\n    public void tick() {\n        cosmeticKills.tick();');s=s.replace('float damage, double hitY) {','float damage, double hitY, boolean nativeMarker) {');s=s.replace('Map.of("type", "hit", "damage", Math.round(damage))','Map.of("type", "hit", "damage", Math.round(damage), "nativeMarker", nativeMarker)');s=s.replace('boolean headshot = Awards.headshot(Awards.lastHitY(match.hits.get(victim.getUUID())),\n                    victim.getY(), victim.getY() + victim.getBbHeight());','boolean headshot = cosmeticKills.headshot(victim, killer, Awards.headshot(Awards.lastHitY(match.hits.get(victim.getUUID())),\n                    victim.getY(), victim.getY() + victim.getBbHeight()));');p.write_text(s,'utf-8')
p=r/'Client.java';s=p.read_text('utf-8');s=s.replace('hitMarkerAt = System.currentTimeMillis();','hitMarkerAt = payload.has("nativeMarker") && payload.get("nativeMarker").getAsBoolean() ? 0 : System.currentTimeMillis();');s=s.replace('if (!vehicle && now - hitMarkerAt < HIT_MARKER_MS)', 'if (!vehicle && !GunFeedbackCompat.heldGun(mc.player.getMainHandItem()) && now - hitMarkerAt < HIT_MARKER_MS)');a=s.index('            if (mc.player == null',s.index('public static void hud(RenderGuiEvent.Post event)'));b=s.index('            } catch (RuntimeException | LinkageError ex)',a);s=s[:a]+'''            if (mc.player == null || mc.screen != null || mc.options.hideGui || SCORES.isDown()) return;
            boolean inMatch=state.has("inMatch") && state.get("inMatch").getAsBoolean() && state.has("me") && state.has("arena");
            try {
                drawAwardFeed(event.getGuiGraphics());
                if (!inMatch) return;
                JsonObject me=state.getAsJsonObject("me");
                if (!Engine.str(me,"map", "").equals(Engine.str(state,"selected",""))) return;
                if (hudTerrain == null) hudTerrain = new TerrainPreview();
                if (!(VehicleUi.active() && VehicleUi.scoped())) CombatHud.draw(event.getGuiGraphics(), hudTerrain);
''' +s[b:];p.write_text(s,'utf-8')
p=r/'AwardHud.java';s=p.read_text('utf-8');s=s.replace('        {\n            int size=CombatHud.mapSize(w,h);','        if(Client.state.has("inMatch") && Client.state.get("inMatch").getAsBoolean()) {\n            int size=CombatHud.mapSize(w,h);');p.write_text(s,'utf-8')
for f in ['build.py','src/main/resources/META-INF/neoforge.mods.toml']:
 p=Path(f);p.write_text(p.read_text('utf-8').replace('0.6.2','0.6.3'),'utf-8')
s=Path('install-0.6.2.ps1').read_text('utf-8-sig').replace('0.6.2','0.6.3');s=s.replace("'battlefield-ops-0.6.1-neoforge-1.21.1.jar',", "'battlefield-ops-0.6.1-neoforge-1.21.1.jar','battlefield-ops-0.6.2-neoforge-1.21.1.jar',");Path('install-0.6.3.ps1').write_text(s,'utf-8-sig')
