package cn.bfops;

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
