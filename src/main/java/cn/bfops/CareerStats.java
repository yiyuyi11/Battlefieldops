package cn.bfops;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Server-owned career ledger. Room deltas prevent polling and reconnects from duplicating totals. */
final class CareerStats {
    static final class Entry {
        String name="";
        long score,kills,deaths,assists,revives,captures;
        Map<String,Integer> honours=new TreeMap<>();
    }
    private final Path folder;
    private final Map<String,Entry> entries=new TreeMap<>();
    private final IdentityHashMap<Engine.Member,long[]> observed=new IdentityHashMap<>();
    private final IdentityHashMap<Engine.Member,Map<String,Integer>> counters=new IdentityHashMap<>();
    private final IdentityHashMap<Engine.Member,Set<String>> earned=new IdentityHashMap<>();
    private boolean dirty;
    private static final class Life {long reviveAt,lastKillAt,arsenalAt,combo4At,raiderAt;int revives;UUID bail;long bailAt,airDestroyedAt;String weapon="";Set<String> weapons=new HashSet<>();}
    private final IdentityHashMap<Engine.Member,Life> lives=new IdentityHashMap<>();
    private record Strike(UUID shooter,String weapon,String type,long at,boolean sniperAiming){}
    private final Map<UUID,Strike> strikes=new HashMap<>();
    private record Collision(UUID driver,long at){}
    private final Map<UUID,Collision> collisions=new HashMap<>();
    private record Threat(UUID attacker,String map,long at){}
    private record TeamKill(String map,int team,UUID victim,long at){}
    private final Map<UUID,Deque<Threat>> threats=new HashMap<>();
    private final Map<UUID,TeamKill> teamKills=new HashMap<>();
    void vehicleDamage(ServerPlayer player,Engine.Member m,float amount){
        int value=counters.computeIfAbsent(m,k->new HashMap<>()).merge("round:vehicleDamage",Math.round(amount),(a,b)->(int)Math.min(Integer.MAX_VALUE,(long)a+b));
        if(value>=1500)award(player,m,"artillery",false);
    }
    void rushObjective(Engine.Member m,boolean plant){increment(m,plant?"stage:plant":"stage:defuse");}
    void damage(ServerPlayer victim,ServerPlayer attacker,net.minecraft.world.damagesource.DamageSource source){
        if(attacker!=null&&VehicleKillCompat.collisionVehicle(source)!=null)collisions.put(victim.getUUID(),new Collision(attacker.getUUID(),System.currentTimeMillis()));
        else collisions.remove(victim.getUUID());
        if(attacker!=null){Engine.Member target=BattlefieldOps.engine.members.get(victim.getUUID()),sourceMember=BattlefieldOps.engine.members.get(attacker.getUUID());
            if(target!=null&&sourceMember!=null&&target.map.equals(sourceMember.map)&&target.team>=0&&sourceMember.team>=0&&target.team!=sourceMember.team){var history=threats.computeIfAbsent(victim.getUUID(),k->new ArrayDeque<>());long now=System.currentTimeMillis();while(!history.isEmpty()&&now-history.getFirst().at>5000)history.removeFirst();history.addLast(new Threat(attacker.getUUID(),target.map,now));while(history.size()>16)history.removeFirst();}
        }
    }
    void hit(ServerPlayer attacker,net.minecraft.world.entity.Entity victim,String weapon,String type){
        if(victim instanceof ServerPlayer target){
            Engine.Member m=BattlefieldOps.engine.members.get(attacker.getUUID()),v=BattlefieldOps.engine.members.get(target.getUUID());
            if(m==null||v==null||!m.map.equals(v.map)||m.team==v.team)return;
            strikes.put(victim.getUUID(),new Strike(attacker.getUUID(),weapon,type,System.currentTimeMillis(),GunFeedbackCompat.sniperAiming(target)));
        }
    }
    void destroyVehicle(ServerPlayer p,Engine.Member m,net.minecraft.world.entity.Entity hull,boolean air,boolean tank,boolean flying){
        count(p,m,"destroyVehicle",7,"demolition",false);
        if(tank)repeated(p,m,"tank_destroyer");
        if(air){
            if(!p.isPassenger())repeated(p,m,"ballistics");
            if(flying)repeated(p,m,"ace_pilot");
            Life life=lives.get(m);long now=System.currentTimeMillis();
            if(life!=null&&life.bail!=null&&!life.bail.equals(hull.getUUID())&&now-life.bailAt<=10000&&life.airDestroyedAt==0)life.airDestroyedAt=now;
        }
    }
    void mount(ServerPlayer p,Engine.Member m,net.minecraft.world.entity.Entity hull,boolean mounting){
        Life life=lives.computeIfAbsent(m,k->new Life());long now=System.currentTimeMillis();
        if(!mounting){life.bail=hull.getUUID();life.bailAt=now;life.airDestroyedAt=0;}
        else if(hull.getUUID().equals(life.bail)&&life.airDestroyedAt>0&&now-life.airDestroyedAt<=10000){life.bail=null;life.airDestroyedAt=0;repeated(p,m,"bail_rocket");}
    }
    void reload(ServerPlayer player){Engine.Member m=BattlefieldOps.engine.members.get(player.getUUID());if(m!=null)counters.computeIfAbsent(m,k->new HashMap<>()).remove("life:machineGun");}
    private final Map<String,Map<UUID,Map<String,Integer>>> roomHonours=new HashMap<>();
    private final Map<String,Set<Integer>> firstKills=new HashMap<>();
    CareerStats(Path folder){
        this.folder=folder;
        Path file=folder.resolve("career.json");
        if(Files.exists(file))try{
            JsonObject data=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for(var row:data.entrySet())entries.put(row.getKey(),BattlefieldOps.GSON.fromJson(row.getValue(),Entry.class));
        }catch(Exception ex){throw new IllegalStateException("career.json 损坏，保留原文件并停止加载",ex);}
    }
    void observe(UUID id,Engine.Member m){
        if(m.bot)return;
        Entry row=entries.computeIfAbsent(id.toString(),k->new Entry());row.name=m.name;
        long[] now={m.score,m.kills,m.deaths,m.assists,m.revives,m.captures};
        long[] before=observed.getOrDefault(m,new long[6]);
        row.score+=Math.max(0,now[0]-before[0]);row.kills+=Math.max(0,now[1]-before[1]);
        row.deaths+=Math.max(0,now[2]-before[2]);row.assists+=Math.max(0,now[3]-before[3]);
        row.revives+=Math.max(0,now[4]-before[4]);row.captures+=Math.max(0,now[5]-before[5]);
        if(!Arrays.equals(now,before))dirty=true;observed.put(m,now);
    }
    void leave(UUID id,Engine.Member m){observe(id,m);observed.remove(m);threats.remove(id);teamKills.remove(id);strikes.remove(id);collisions.remove(id);collisions.entrySet().removeIf(e->e.getValue().driver.equals(id));strikes.entrySet().removeIf(e->e.getValue().shooter.equals(id));counters.remove(m);earned.remove(m);lives.remove(m);save();}
    int increment(Engine.Member m,String key){return counters.computeIfAbsent(m,k->new HashMap<>()).merge(key,1,Integer::sum);}
    void death(Engine.Member m){
        lives.remove(m);
        var c=counters.get(m);if(c!=null)c.keySet().removeIf(k->k.startsWith("life:"));
        var e=earned.get(m);if(e!=null)e.removeIf(k->k.startsWith("life:"));
    }
    void count(ServerPlayer p,Engine.Member m,String key,int threshold,String honour,boolean life){
        if(m.bot)return;
        if(increment(m,(life?"life:":"round:")+key)>=threshold)award(p,m,honour,life);
    }
    void award(ServerPlayer p,Engine.Member m,String id,boolean life){
        if(p==null||m==null||m.bot)return;
        if(!earned.computeIfAbsent(m,k->new HashSet<>()).add((life?"life:":"round:")+id))return;
        HonourCatalog def=HonourCatalog.all().stream().filter(h->h.id().equals(id)).findFirst().orElse(null);
        if(def==null)return;
        Entry row=entries.computeIfAbsent(p.getUUID().toString(),k->new Entry());
        row.name=m.name;int previous=row.honours.getOrDefault(id,0);
        boolean globalBest=previous+1>=entries.values().stream().mapToInt(e->e.honours.getOrDefault(id,0)).max().orElse(0);
        row.honours.put(id,previous+1);dirty=true;
        var room=roomHonours.computeIfAbsent(m.map,k->new HashMap<>());
        int count=room.computeIfAbsent(p.getUUID(),k->new HashMap<>()).merge(id,1,Integer::sum);
        boolean matchBest=count>=room.values().stream().mapToInt(e->e.getOrDefault(id,0)).max().orElse(0);
        Network.send(p,Map.of("type","honour","label","荣誉","points",0,"detail","","icon","honor","honorId",id,"honorStyle","style="+(globalBest?(matchBest?"gm":"g"):(matchBest?"m":""))));
    }
    void finish(String map){roomHonours.remove(map);firstKills.remove(map);teamKills.entrySet().removeIf(e->e.getValue().map.equals(map));for(var history:threats.values())history.removeIf(t->t.map.equals(map));threats.values().removeIf(Deque::isEmpty);save();}
    void poll(ServerPlayer p,Engine.Member m){
        if(m.bot)return;
        long[] previous=observed.getOrDefault(m,new long[6]);
        int gain=(int)Math.max(0,m.score-previous[0]);
        if(gain>0){var c=counters.computeIfAbsent(m,k->new HashMap<>());
            FixedClasses.Role role=FixedClasses.resolve(m.kit);
            if(role!=null){int score=c.merge("life:classScore:"+role.stem,gain,Integer::sum);if(score>=1000)award(p,m,role.stem,true);}}
        if(m.deaths>previous[2])death(m);
        observe(p.getUUID(),m);
    }
    void repeated(ServerPlayer p,Engine.Member m,String id){if(m.bot)return;earned.computeIfAbsent(m,k->new HashSet<>()).remove("life:"+id);award(p,m,id,true);}
    void revive(ServerPlayer p,Engine.Member m){
        Life life=lives.computeIfAbsent(m,k->new Life());long now=System.currentTimeMillis();
        boolean smoke=p.serverLevel().getEntities(p,p.getBoundingBox().inflate(5)).stream().anyMatch(e->{String id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();return (id.equals("superbwarfare:m18_smoke_grenade")||id.equals("lrtactical:smoke_grenade"))&&e.distanceToSqr(p)<=25;});
        if(smoke)count(p,m,"smokeRevive",5,"smoke_screen",true);
        if(now-life.reviveAt>8000)life.revives=0;
        life.reviveAt=now;
        if(++life.revives>=3){life.revives=0;repeated(p,m,"medic");}
    }
    void stage(ServerPlayer p,Engine.Member m,boolean rush){
        var c=counters.get(m);if(c==null)return;
        int kills=c.getOrDefault("stage:kills",0),score=c.getOrDefault("stage:capture",0);
        if(m.team==0&&kills>5&&(rush?c.getOrDefault("stage:plant",0)>0:score>150))repeated(p,m,"breaker");
        if(m.team==1&&kills>7&&(rush?c.getOrDefault("stage:defuse",0)>0:score>50))repeated(p,m,"ground_control");
        c.keySet().removeIf(k->k.startsWith("stage:"));
    }
    void capture(Engine.Member m,int score){counters.computeIfAbsent(m,k->new HashMap<>()).merge("stage:capture",score,Integer::sum);}
    void kill(ServerPlayer p,Engine.Member m,Honours.Evidence e,ServerPlayer victim){
        if(m.bot)return;
        long nowKill=System.currentTimeMillis();
        TeamKill revenge=teamKills.remove(victim.getUUID());
        if(revenge!=null&&revenge.map.equals(m.map)&&revenge.team==m.team&&!revenge.victim.equals(p.getUUID())&&nowKill-revenge.at<=60000)repeated(p,m,"avenger");
        Engine.Member deadMember=BattlefieldOps.engine.members.get(victim.getUUID());
        if(deadMember!=null)teamKills.put(p.getUUID(),new TeamKill(m.map,deadMember.team,victim.getUUID(),nowKill));
        boolean saved=false;
        for(var participant:BattlefieldOps.engine.members.entrySet()){
            var ally=participant.getValue();if(participant.getKey().equals(p.getUUID())||!ally.alive||ally.team!=m.team||!ally.map.equals(m.map))continue;
            var history=threats.get(participant.getKey());if(history!=null&&history.stream().anyMatch(h->h.attacker.equals(victim.getUUID())&&h.map.equals(m.map)&&nowKill-h.at<=5000)){saved=true;break;}
        }
        if(saved)repeated(p,m,"guardian");
        threats.remove(victim.getUUID());
        boolean positive=p.getActiveEffects().stream().anyMatch(effect->effect.getEffect().value().isBeneficial());
        boolean negative=p.getActiveEffects().stream().anyMatch(effect->!effect.getEffect().value().isBeneficial());
        if(positive&&!negative&&(p.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED)||p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)))repeated(p,m,"frenzy");
        if(victim.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)||victim.getActiveEffects().stream().anyMatch(effect->java.util.Set.of("lrtactical:blinded","superbwarfare:shock").contains(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString())))repeated(p,m,"flank");
        Collision collision=collisions.remove(victim.getUUID());
        if(collision!=null&&collision.driver.equals(p.getUUID())&&System.currentTimeMillis()-collision.at<=2000)repeated(p,m,"roadhog");
        Strike strike=strikes.remove(victim.getUUID());long stamp=System.currentTimeMillis();
        boolean gun=strike!=null&&strike.shooter.equals(p.getUUID())&&stamp-strike.at<=2000;
        String type=gun?strike.type:"";
        if(type.equals("sniper")){
            if(FixedClasses.resolve(m.kit)==FixedClasses.Role.RECON)count(p,m,"sniper",15,"marksman",false);
            if(strike.sniperAiming)repeated(p,m,"sniper_duel");
        }
        if(type.equals("rifle")&&FixedClasses.resolve(m.kit)==FixedClasses.Role.ASSAULT)count(p,m,"rifle",20,"rifleman",false);
        if(type.equals("rpg")&&e.headshot())repeated(p,m,"warhead");
        var tally=counters.computeIfAbsent(m,k->new HashMap<>());
        if(type.equals("mg")){if(increment(m,"life:machineGun")>=5){tally.remove("life:machineGun");repeated(p,m,"unstoppable");}}
        else tally.remove("life:machineGun");
        if(firstKills.computeIfAbsent(m.map,k->new HashSet<>()).add(m.team))award(p,m,"vanguard",false);
        if(e.headshot())count(p,m,"headshot",3,"headhunter",true);
        if(e.attackerMoving()>0.01)count(p,m,"moving",3,"warrior",true);
        var heldItem=p.getMainHandItem().getItem();
        boolean melee=heldItem instanceof net.minecraft.world.item.SwordItem||heldItem instanceof net.minecraft.world.item.AxeItem||net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(heldItem).getNamespace().equals("lrtactical");
        if(e.backstab()&&e.distance()<2&&melee)repeated(p,m,"executioner");
        if(p.getVehicle()!=null)try{Object seat=p.getVehicle().getClass().getMethod("getSeatIndex",net.minecraft.world.entity.Entity.class).invoke(p.getVehicle(),p);if(seat instanceof Integer index&&index>0)count(p,m,"gunner",5,"gunner",true);}catch(ReflectiveOperationException ignored){}
        increment(m,"stage:kills");
        Life life=lives.computeIfAbsent(m,k->new Life());long now=System.currentTimeMillis();
        String identity=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString();
        if(identity.startsWith("tacz:"))try{Object held=p.getMainHandItem().getItem();identity=held.getClass().getMethod("getGunId",net.minecraft.world.item.ItemStack.class).invoke(held,p.getMainHandItem()).toString();}catch(ReflectiveOperationException ignored){}
        if(gun)identity=strike.weapon;
        if(life.lastKillAt>0&&now-life.lastKillAt<=4000&&!identity.equals(life.weapon))repeated(p,m,"quickdraw");
        if(life.arsenalAt==0||now-life.arsenalAt>30000){life.arsenalAt=now;life.weapons.clear();}
        life.weapons.add(identity);
        if(life.weapons.size()>=3&&FixedClasses.resolve(m.kit)!=FixedClasses.Role.ASSAULT)repeated(p,m,"arsenal");
        if(life.combo4At>0&&now-life.combo4At<=8000&&(life.raiderAt==0||now-life.raiderAt>=30000)){repeated(p,m,"raider");life.raiderAt=now;}
        if(e.streak()==4)life.combo4At=now;
        life.lastKillAt=now;life.weapon=identity;
        var weapon=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem());
        if(weapon.getNamespace().equals("tacz")||weapon.getNamespace().equals("superbwarfare")){
            if(e.distance()>=100&&e.headshot())repeated(p,m,"sharp_shooter");
            if(e.distance()>=50&&e.distance()<100)repeated(p,m,"long_shot");
        }
    }
    void save(){long now=System.currentTimeMillis();collisions.entrySet().removeIf(e->now-e.getValue().at>2000);strikes.entrySet().removeIf(e->now-e.getValue().at>2000);if(!dirty)return;try{MapFiles.save(folder,"career",BattlefieldOps.GSON.toJson(entries));dirty=false;}
        catch(Exception ex){BattlefieldOps.LOG.error("Cannot save career ledger; retrying later",ex);}}
    private long value(Entry e,String sort){return switch(sort){case "kills"->e.kills;case "assists"->e.assists;case "revives"->e.revives;case "captures"->e.captures;case "honours"->e.honours.values().stream().mapToLong(Integer::longValue).sum();default->e.score;};}
    void leaderboard(ServerPlayer p,int offset,int limit,long requestId){
        var engine=BattlefieldOps.engine;
        if(engine!=null)for(var pair:engine.members.entrySet())observe(pair.getKey(),pair.getValue());
        Map<String,Entry> snapshot=new TreeMap<>(entries);
        for(var online:p.server.getPlayerList().getPlayers())if(engine==null||!engine.isBot(online)){
            Entry row=snapshot.computeIfAbsent(online.getUUID().toString(),k->new Entry());row.name=online.getScoreboardName();
        }
        var sorted=snapshot.entrySet().stream().sorted(Comparator.<Map.Entry<String,Entry>>comparingLong(e->e.getValue().score).reversed().thenComparing(Map.Entry::getKey)).toList();
        var onlineIds=p.server.getPlayerList().getPlayers().stream().map(v->v.getUUID().toString()).collect(java.util.stream.Collectors.toSet());
        var live=sorted.stream().filter(v->onlineIds.contains(v.getKey())).toList();var offline=sorted.stream().filter(v->!onlineIds.contains(v.getKey())).toList();
        var merged=new ArrayList<Map.Entry<String,Entry>>();for(int i=0;i<Math.max(live.size(),offline.size());i++){if(i<live.size())merged.add(live.get(i));if(i<offline.size())merged.add(offline.get(i));}sorted=merged;
        int from=Math.min(Math.max(0,offset),sorted.size()),to=Math.min(sorted.size(),from+Math.clamp(limit,1,128));
        var rows=new ArrayList<Map<String,Object>>();
        for(var pair:sorted.subList(from,to)){
            UUID id;try{id=UUID.fromString(pair.getKey());}catch(IllegalArgumentException ex){continue;}
            var online=p.server.getPlayerList().getPlayer(id);var e=pair.getValue();var row=new LinkedHashMap<String,Object>();
            row.put("uuid",id.toString());row.put("name",e.name);row.put("score",e.score);row.put("kills",e.kills);row.put("deaths",e.deaths);row.put("assists",e.assists);row.put("revives",e.revives);row.put("online",online!=null);row.put("ping",online==null?0:online.connection.latency());rows.add(row);
        }
        Network.send(p,Map.of("type","leaderboard","rows",rows,"offset",from,"total",sorted.size(),"requestId",requestId));
    }
    void send(ServerPlayer p,String sort){
        List<Map<String,Object>> rows=new ArrayList<>();
        entries.entrySet().stream().sorted(Comparator.<Map.Entry<String,Entry>>comparingLong(e->value(e.getValue(),sort)).reversed().thenComparing(Map.Entry::getKey)).limit(100).forEach(e->{
            Entry r=e.getValue();rows.add(Map.of("id",e.getKey(),"name",r.name,"score",r.score,"kills",r.kills,"deaths",r.deaths,"assists",r.assists,"revives",r.revives,"captures",r.captures,"honours",r.honours));});
        Network.send(p,Map.of("type","career","rows",rows,"self",entries.getOrDefault(p.getUUID().toString(),new Entry())));
    }
}
