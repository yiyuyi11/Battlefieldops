package cn.bfops;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.network.chat.Component;

final class ConquestSupport {
    private static final class Device {
        final SupportProp entity;final UUID owner;final String map;final int team;final SupportRules.Kind kind;
final long expires;final SupportRules.Budget budget;
        final Map<UUID,Long> scanAwards=new HashMap<>();
        Device(SupportProp entity,ServerPlayer owner,Engine.Member m,SupportRules.Kind kind,long tick,int uses,long cooldown){this.entity=entity;this.owner=owner.getUUID();map=m.map;team=m.team;this.kind=kind;expires=tick+SupportRules.BOX_LIFE;this.budget=new SupportRules.Budget(uses,cooldown);}
    }
    private final Engine engine;private final MinecraftServer server;
    private final Map<UUID,Device> devices=new LinkedHashMap<>();
    private final Map<UUID,EnumMap<SupportRules.Kind,Long>> cooldowns=new HashMap<>();
    private record HealLease(int amplifier,long until){}
    private final Map<UUID,HealLease> healing=new HashMap<>();
    /** Players whose delayed heal is already ticking down, so a box cannot queue two at once. */
    private final java.util.Set<UUID> pendingHeals=new java.util.HashSet<>();
    /** "box|player" -> tick the current unit started accumulating. */
    /** "box|player" -> {accumulated ticks, last tick seen inside range}: leaving the area pauses it. */
    private final Map<String,long[]> refillClock=new HashMap<>();

    private long tick;
    ConquestSupport(Engine engine,MinecraftServer server){this.engine=engine;this.server=server;}
    boolean use(ServerPlayer giver,InteractionHand hand,ServerPlayer target){
        ItemStack held=giver.getItemInHand(hand);var kind=ConquestEquipment.kind(held);if(kind==null)return false;
        try{
            Engine.Member m=engine.members.get(giver.getUUID());Model.require(engine.supportActive(m)&&m.alive&&!m.downed,"请在对局中存活时使用支援装备");
            var cd=cooldowns.computeIfAbsent(giver.getUUID(),id->new EnumMap<>(SupportRules.Kind.class));
                long cooldownTicks=engine.refillCooldownTicks(m.map,kind);
                if(cooldownTicks<=0){} else if(cd.getOrDefault(kind,0L)-tick>=cooldownTicks-1)return true;
            Model.require(tick>=cd.getOrDefault(kind,0L),"装备冷却中，剩余 "+((cd.getOrDefault(kind,0L)-tick+19)/20)+" 秒");
            if(kind==SupportRules.Kind.RADIO_BEACON){engine.supportPlaceBeacon(giver);}
            else if(kind==SupportRules.Kind.AMMO_BAG||kind==SupportRules.Kind.MEDIC_BAG){
                ServerPlayer receiver=target==null?giver:target;Engine.Member patient=engine.members.get(receiver.getUUID());
                Model.require(engine.supportActive(patient)&&patient.alive&&!patient.downed&&SupportRules.sameRoomTeam(m.map,m.team,patient.map,patient.team),"只能补给本局存活队友");
                Model.require(giver.level()==receiver.level()&&giver.distanceToSqr(receiver)<=12.25&&giver.hasLineOfSight(receiver),"目标不在可交互范围");
                boolean changed=kind==SupportRules.Kind.AMMO_BAG?engine.supportAmmoBag(receiver)>0:heal(receiver,80,4);
                Model.require(changed,"目标已补给完毕，未消耗装备");
                engine.supportCredit(giver,receiver,kind==SupportRules.Kind.AMMO_BAG?"弹药补给":"医疗补给");
            }else place(giver,m,kind);
            if(!giver.isCreative()&&kind!=SupportRules.Kind.RADIO_BEACON){
            int ownedBefore=countByItem(giver,kind);
            held.shrink(1);
            // The consumed stack reference can go stale (the item may have been moved mid-use), so verify the
            // inventory really lost one and take it explicitly if it did not.
            if(!giver.isCreative()&&countByItem(giver,kind)>=ownedBefore){
              takeOne(giver,kind);
            }
            }
            if(kind!=SupportRules.Kind.RADIO_BEACON)engine.refills.used(giver,kind,tick);
            if(cooldownTicks>0)cd.put(kind,tick+cooldownTicks);giver.getInventory().setChanged();
            engine.supportRefresh(giver);
            if(kind==SupportRules.Kind.MEDIC_BAG||kind==SupportRules.Kind.MED_BOX)
                cn.bfops.voice.server.audio.FrontVoiceCueEmitter.emitCue(giver,cn.bfops.voice.common.audio.VoiceCue.THROW_MEDKIT);
            else if(kind==SupportRules.Kind.AMMO_BAG||kind==SupportRules.Kind.AMMO_BOX)
                cn.bfops.voice.server.audio.FrontVoiceCueEmitter.emitCue(giver,cn.bfops.voice.common.audio.VoiceCue.THROW_AMMO);
            cue(giver,kind==SupportRules.Kind.GROUND_SENSOR?"sensor_fire":kind==SupportRules.Kind.RADIO_BEACON?"beacon_deploy":"ammo_throw");
            giver.displayClientMessage(Component.literal("§b已使用 "+kind.label),true);
        }catch(Exception ex){giver.displayClientMessage(Component.literal("§c"+(ex.getMessage()==null?"装备使用失败":ex.getMessage())),true);}
        return true;
    }
    private void place(ServerPlayer player,Engine.Member m,SupportRules.Kind kind){
        Model.require(!player.isPassenger(),"请下车后放置装备");
        Vec3 eye=player.getEyePosition(),end=eye.add(player.getLookAngle().scale(4));
        BlockHitResult hit=player.level().clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        Model.require(hit.getType()==HitResult.Type.BLOCK&&hit.getDirection()==net.minecraft.core.Direction.UP,"请指向四格内可放置的地面");
        Vec3 pos=hit.getLocation().add(0,.02,0);var world=player.serverLevel();
        Model.require(engine.supportPosition(m,pos)&&world.getFluidState(net.minecraft.core.BlockPos.containing(pos)).isEmpty(),"放置点不在有效战区或位于液体中");
        Model.require(world.noCollision(player,new AABB(pos.x-.25,pos.y,pos.z-.25,pos.x+.25,pos.y+.7,pos.z+.25)),"放置位置被占用");
        var prop=new SupportProp(world,pos.x,pos.y,pos.z,this);prop.setNoGravity(true);prop.setInvisible(true);prop.setYRot(player.getYRot());prop.setCustomNameVisible(true);prop.addTag("bfops_support_prop");
        prop.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,ConquestEquipment.stack(kind.key(),1));
        int uses=usesFor(m);int cooldown=0;   // pacing comes from the map interval, not a hidden cooldown
        var device=new Device(prop,player,m,kind,tick,uses,cooldown);devices.put(prop.getUUID(),device);
        if(!world.addFreshEntity(prop)){devices.remove(prop.getUUID());throw new IllegalArgumentException("装备实体生成失败");}
        for(var old:new ArrayList<>(devices.values()))if(old!=device&&old.owner.equals(player.getUUID())&&old.kind==kind)remove(old);
        refreshName(device);
    }
    /** Map-configured amount of times one support box may serve; falls back to the built-in default. */
    private int usesFor(Engine.Member member) {
        Model.Arena arena = engine.supportMap(member);
        int configured = arena == null ? 0 : arena.supportBoxUses;
        return configured > 0 ? configured : SupportRules.BOX_USES;
    }

    /**
     * The medical bag restores health at once, but a hit lands first: taking damage in the last second
     * interrupts it, so it cannot be used as a free heal in the middle of a firefight.
     */
    private boolean healInstant(ServerPlayer player) {
        if (engine.hurtRecently(player.getUUID(), 20)) return false;
        if (player.getHealth() >= player.getMaxHealth()) return false;
        player.setHealth(player.getMaxHealth());
        return true;
    }

    /** How many of this gadget the player holds, matched by registered id. */
    private int countByItem(ServerPlayer player, SupportRules.Kind kind) {
        String wanted = kind.key();
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(wanted)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Removes exactly one of the gadget, wherever it sits. */
    private void takeOne(ServerPlayer player, SupportRules.Kind kind) {
        String wanted = kind.key();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(wanted)) continue;
            stack.shrink(1);
            if (stack.isEmpty()) player.getInventory().setItem(slot, ItemStack.EMPTY);
            return;
        }
    }


    private boolean heal(ServerPlayer player, int duration, int amplifier) {
        if (player.getHealth() >= player.getMaxHealth()) return false;
        if (player.hasEffect(MobEffects.REGENERATION)) return false;
        if (engine.hurtRecently(player.getUUID(), 20)) {
            if (pendingHeals.contains(player.getUUID())) return false;
            // Hit in the last second: the support still works, but it spins up for half a second first
            // instead of healing instantly in the middle of a firefight.
            var server = player.getServer();
            if (server == null) return false;
            pendingHeals.add(player.getUUID());
            server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 10, () -> {
                pendingHeals.remove(player.getUUID());
                if (player.isAlive() && player.getHealth() < player.getMaxHealth()
                        && !player.hasEffect(MobEffects.REGENERATION)) {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, amplifier, false, true, true));
                    healing.put(player.getUUID(), new HealLease(amplifier, tick + duration + 10));
                }
            }));
            return true;
        }
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, amplifier, false, true, true));
        healing.put(player.getUUID(), new HealLease(amplifier, tick + duration));
        return true;
    }
    void down(ServerPlayer player){HealLease owned=healing.remove(player.getUUID());var effect=player.getEffect(MobEffects.REGENERATION);if(owned!=null&&tick<owned.until&&effect!=null&&effect.getAmplifier()==owned.amplifier)player.removeEffect(MobEffects.REGENERATION);}
    /** Ticks between two refill units for the map owning {@code mapId}; 0 means "give everything at once". */
    private int refillPeriod(String mapId){
        return engine.refillPeriodTicks(mapId);
    }

    /**
     * How far the nearest ammunition box has got with this player, 0..1, or 0 when none is in range.
     * Read by the HUD once a second through the state snapshot.
     */
    
    /** Drops progress rows whose box no longer exists, so the HUD bar cannot stay half filled. */
    private void pruneRefillProgress() {
        if (refillClock.isEmpty()) return;
        refillClock.keySet().removeIf(key -> {
            int split = key.indexOf('|');
            if (split <= 0) return true;
            try {
                return !devices.containsKey(UUID.fromString(key.substring(0, split)));
            } catch (IllegalArgumentException malformed) {
                return true;
            }
        });
    }

    /** Seconds until the nearest ammo box can hand out the next unit; 0 when it is ready. */
    double refillWait(ServerPlayer player) {
        Engine.Member member = engine.members.get(player.getUUID());
        if (member == null || !member.alive || member.downed) return 0;
        double best = 0;
        for (Device d : devices.values()) {
            if (d.kind != SupportRules.Kind.AMMO_BOX || !d.entity.isAlive() || d.entity.isRemoved()) continue;
            if (!SupportRules.sameRoomTeam(d.map, d.team, member.map, member.team)) continue;
            if (player.level() != d.entity.level()) continue;
            if (player.distanceToSqr(d.entity) > SupportRules.BOX_RADIUS * SupportRules.BOX_RADIUS) continue;
            int period = refillPeriod(d.map);
            if (period <= 0) continue;
            long[] clock = refillClock.get(d.entity.getUUID() + "|" + player.getUUID());
            long accumulated = clock == null ? 0 : clock[0];
            double left = (period - accumulated) / 20.0;
            if (best == 0 || left < best) best = left;
        }
        return Math.max(0, best);
    }


double refillProgress(ServerPlayer player){
        Engine.Member member = engine.members.get(player.getUUID());
        if (member == null || !member.alive || member.downed) return 0;
        double best=0;
        for(Device d:devices.values()){
            if(d.kind!=SupportRules.Kind.AMMO_BOX||!d.entity.isAlive()||d.entity.isRemoved())continue;
            if (!SupportRules.sameRoomTeam(d.map, d.team, member.map, member.team)) continue;
            if(player.level()!=d.entity.level()||player.distanceToSqr(d.entity)>SupportRules.BOX_RADIUS*SupportRules.BOX_RADIUS)continue;
            int period=refillPeriod(d.map);
            if(period<=0)continue;
            long[] clock=refillClock.get(d.entity.getUUID()+"|"+player.getUUID());
            if(clock==null)continue;
            long progress=clock[0];
            if(tick-clock[1]<=40)progress+=Math.max(0,tick-clock[1]);
            if(progress>=period)progress=period-1;   // never show a full bar: a full bar means nothing is left to do
            best=Math.max(best,Math.min(1.0,progress/(double)period));
        }
        return best;
    }

    void tick(long now){
        pruneRefillProgress();
        tick=now;if(now%20!=0)return;
        healing.entrySet().removeIf(e->now>=e.getValue().until);
        for(Device d:new ArrayList<>(devices.values())){
            Engine.Member owner=engine.members.get(d.owner);
            if(!engine.supportActive(owner)||!SupportRules.sameRoomTeam(d.map,d.team,owner.map,owner.team)||now>=d.expires||!d.entity.isAlive()||d.entity.isRemoved()||d.budget.remaining<=0){remove(d);continue;}
            if(d.kind==SupportRules.Kind.GROUND_SENSOR){if(now%SupportRules.SENSOR_INTERVAL==0)scan(d);continue;}
            for(ServerPlayer player:server.getPlayerList().getPlayers()){
                Engine.Member m=engine.members.get(player.getUUID());
                if(m==null||!m.alive||m.downed||!SupportRules.sameRoomTeam(d.map,d.team,m.map,m.team)||player.level()!=d.entity.level()||player.distanceToSqr(d.entity)>SupportRules.BOX_RADIUS*SupportRules.BOX_RADIUS||!d.budget.ready(player.getUUID(),now))continue;
                try{
                    boolean changed;
                if(d.kind==SupportRules.Kind.MED_BOX){
                    // Heal over time rather than filling the bar: 5 s of Regeneration I per use, shared uses only.
      changed=heal(player,80,4);   // 4 s of Regeneration III: roughly three seconds to full
                }else{
                    // Ammunition is handed out one unit at a time, so a rocket or grenade costs the same as a
                    // magazine rather than everything arriving the instant you stand next to the box.
                    int period=refillPeriod(d.map);
                    String key=d.entity.getUUID()+"|"+player.getUUID();
                long[] clock=refillClock.computeIfAbsent(key,k2->new long[]{0,now});
                // Leaving and coming back resumes the bar rather than restarting it: only time actually
                // spent inside the radius is accumulated, any longer gap counts as "was away".
                if(now-clock[1]<=20)clock[0]+=now-clock[1];
                clock[1]=now;
                if(period>0&&clock[0]<period)continue;
                changed=engine.supportAmmo(player)>0;   // the box refills everything at once when its interval elapses
                // Nothing left to hand out: reset the clock instead of parking it at "full", which used to
                // leave the HUD bar pinned and the loop retrying every tick.
                clock[0]=0;
                }
                    if(d.budget.consume(player.getUUID(),now,changed)){
                        ServerPlayer helper=server.getPlayerList().getPlayer(d.owner);if(helper!=null)engine.supportCredit(helper,player,d.kind==SupportRules.Kind.MED_BOX?"医疗补给":"弹药补给");
                        cue(player,d.kind==SupportRules.Kind.MED_BOX?"heal":"ammo_pickup");
                        player.displayClientMessage(Component.literal("§b"+(d.kind==SupportRules.Kind.MED_BOX?"医疗箱":"弹药箱")+"已为你补给"),true);
                        refreshName(d);if(d.budget.remaining<=0)break;
                    }
                }catch(ReflectiveOperationException ex){BattlefieldOps.LOG.debug("Support ammunition bridge unavailable",ex);}
            }
            if(d.budget.remaining<=0)remove(d);
        }
    }
    private void scan(Device d){
        boolean detected=false;
        for(ServerPlayer enemy:server.getPlayerList().getPlayers()){
            Engine.Member m=engine.members.get(enemy.getUUID());
            if(m==null||!m.alive||enemy.isSpectator()||m.team<0||m.team==d.team||!d.map.equals(m.map)||enemy.level()!=d.entity.level())continue;
            if(SupportRules.sensorContact(d.map,d.team,m.map,m.team,m.alive,enemy.distanceToSqr(d.entity))){
                detected=true;
                engine.supportContact(d.owner,enemy);
                if(tick>=d.scanAwards.getOrDefault(enemy.getUUID(),0L)){
                    var owner=server.getPlayerList().getPlayer(d.owner);if(owner!=null)engine.supportCredit(owner,enemy,"地面侦测");
                    d.scanAwards.put(enemy.getUUID(),tick+200);
                }
            }
            if(enemy.distanceToSqr(d.entity)<=4)enemy.displayClientMessage(Component.literal("§c附近有敌方地面传感器"),true);
        }
        for(var player:server.getPlayerList().getPlayers())if(player.level()==d.entity.level()&&player.distanceToSqr(d.entity)<=100)cueAt(player,d.entity,detected?"sensor_activate":"sensor_beep");
    }
    private void cue(ServerPlayer player,String cue){Network.send(player,Map.of("type","supportSound","cue",cue));}
    private void cueAt(ServerPlayer player,Entity source,String cue){Network.send(player,Map.of("type","supportSound","cue",cue,"x",source.getX(),"y",source.getY(),"z",source.getZ()));}
    boolean damage(SupportProp prop,DamageSource source,float amount){
        Device d=devices.get(prop.getUUID());if(d==null||!Float.isFinite(amount)||amount<=0)return false;
        ServerPlayer actor=VehicleKillCompat.attacker(source);
        if(actor!=null){var m=engine.members.get(actor.getUUID());if(m==null||!m.alive||m.downed||!m.map.equals(d.map)||(m.team==d.team&&!d.owner.equals(actor.getUUID())))return false;}
        if(PropVitals.damage(prop,amount)){
            if(actor!=null)engine.supportGadgetDestroyed(actor,d.map,d.team,prop);
            remove(d);
        }
        return true;
    }
    List<Map<String,Object>> snapshot(Engine.Member viewer){
        List<Map<String,Object>> out=new ArrayList<>();if(viewer==null)return out;
        for(Device d:devices.values())if(SupportRules.sameRoomTeam(d.map,d.team,viewer.map,viewer.team))out.add(Map.of("id",d.entity.getUUID().toString(),"kind",d.kind.id,"x",d.entity.getX(),"y",d.entity.getY(),"z",d.entity.getZ(),"left",d.budget.remaining,"team",d.team));
        return out;
    }
    void leave(UUID player){for(var d:new ArrayList<>(devices.values()))if(d.owner.equals(player))remove(d);cooldowns.remove(player);var entity=server.getPlayerList().getPlayer(player);if(entity!=null)down(entity);else healing.remove(player);}
    void close(){for(var d:new ArrayList<>(devices.values()))remove(d);cooldowns.clear();for(var id:new ArrayList<>(healing.keySet())){var player=server.getPlayerList().getPlayer(id);if(player!=null)down(player);}healing.clear();}
    private void remove(Device d){d.entity.discard();devices.remove(d.entity.getUUID());}
    private void refreshName(Device d){d.entity.setCustomName(Component.literal(d.kind.label+(d.kind==SupportRules.Kind.GROUND_SENSOR?"":" · "+d.budget.remaining+" 次")));}
}
