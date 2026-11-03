package cn.bfops;

import java.util.*;
import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Fixed identities, legacy preservation, authoritative pickup/ownership and cross-room elite occupancy. */
public final class EliteClassSim {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static void reject(Runnable r){try{r.run();throw new AssertionError("Invalid class/pickup configuration accepted");}catch(IllegalArgumentException expected){checks++;}}
    static Model.Kit kit(String name,String gun){var k=new Model.Kit();k.id=name;k.primary=gun;return k;}
    static Model.Arena map(){var a=new Model.Arena();a.boundary.addAll(List.of(new Model.Vec(-100,-100),new Model.Vec(100,-100),new Model.Vec(100,100),new Model.Vec(-100,100)));a.kits.add(new Model.Kit());FixedClasses.complete(a);EliteRules.complete(a);return a;}
    static boolean calls(String type,String method,String owner,String target)throws Exception{
        var n=new ClassNode();new ClassReader(Files.readAllBytes(Path.of("build/classes/cn/bfops/"+type+".class"))).accept(n,0);
        for(var m:n.methods)if(method==null||m.name.equals(method))for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals("cn/bfops/"+owner)&&c.name.equals(target))return true;
        return false;
    }
    static String block(String text,String name){int a=text.indexOf("case \""+name+"\"");if(a<0)throw new AssertionError(name);int b=text.indexOf("case ",a+5);return text.substring(a,b<0?text.length():b);}
    public static void main(String[] args)throws Exception{
        check(FixedClasses.Role.values().length==4,"four reference ordinary classes");
        check(EliteRules.Kind.values().length==5,"five fixed reference elite types");
        for(var role:FixedClasses.Role.values()){
            check(FixedClasses.resolve(role.name)==role&&FixedClasses.resolve(role.stem)==role&&FixedClasses.resolve(role.digit)==role,"reference aliases share icon and identity");
        }
        check(FixedClasses.resolve("医疗兵")==FixedClasses.Role.SUPPORT,"reference medical alias");
        var a=map();a.kits.get(0).id="自建兵";reject(()->FixedClasses.validateActive(a));
        a.kits.get(0).id="突击兵";a.kits.get(1).id="突击兵";reject(()->FixedClasses.validateActive(a));
        var legacy=new Model.Arena();Set<String> expected=new HashSet<>();
        for(int i=0;i<7;i++){legacy.kits.add(kit("自定义"+i,"test:gun"+i));expected.add("test:gun"+i);}
        check(FixedClasses.migrateLegacy(legacy),"legacy custom classes migrate");check(legacy.kits.size()==4&&legacy.legacyKits.size()==3,"extra old equipment is archived");
        Set<String> actual=new HashSet<>();for(var k:legacy.kits)actual.add(k.primary);for(var k:legacy.legacyKits)actual.add(k.primary);check(actual.equals(expected),"all legacy weapon configurations preserved");
        FixedClasses.restore(legacy,0,0);actual.clear();for(var k:legacy.kits)actual.add(k.primary);for(var k:legacy.legacyKits)actual.add(k.primary);check(actual.equals(expected),"restoring old equipment is a lossless swap");
        FixedClasses.validateActive(legacy);check(!FixedClasses.migrateLegacy(legacy),"migration is idempotent");
        var oldElite=new Model.Arena();oldElite.kits.add(kit("哨戒兵","test:sentry"));FixedClasses.migrateLegacy(oldElite);EliteRules.complete(oldElite);
        check(EliteRules.template(oldElite,"sentry").kit.primary.equals("test:sentry"),"legacy named elite moves to elite template");
        check(oldElite.kits.stream().noneMatch(k->k.primary.equals("test:sentry")),"elite template is absent from normal deployment classes");
        check(!EliteRules.configured(null),"missing elite template is unavailable");
        var empty=new EliteRules.Template();empty.kit.id="反载具兵";check(!EliteRules.configured(empty),"blank elite equipment is unavailable");
        empty.kit.gear().accessories.put("ring","test:ring");check(!EliteRules.configured(empty),"accessory-only template cannot replace the combat loadout with nothing");
        empty.kit.primary="test:rifle";check(EliteRules.configured(empty),"a configured combat weapon enables the fixed template");
        var valid=map();EliteRules.validate(valid);
        var p=new EliteRules.Spawn();valid.eliteSpawns.add(p);EliteRules.validate(valid);
        check(EliteRules.reachable(p,0,0,false,3,64,0),"pickup radius includes boundary");
        check(!EliteRules.reachable(p,0,0,false,3.01,64,0),"remote pickup rejected");
        check(!EliteRules.reachable(p,0,0,false,0,68,0),"vertical distance enforced");
        p.team=1;check(!EliteRules.reachable(p,0,0,false,0,64,0),"enemy-side pickup rejected");p.team=-1;
        p.sector=1;check(!EliteRules.reachable(p,0,0,true,0,64,0),"future breakthrough stage locked");
        check(EliteRules.reachable(p,0,1,true,0,64,0),"active breakthrough stage allowed");
        p.initialDelaySeconds=2;check(EliteRules.delayRemaining(100,p,139)==1&&EliteRules.delayRemaining(100,p,140)==0,"initial refresh exact tick boundary");
        p.radius=Double.NaN;reject(()->EliteRules.validate(valid));p.radius=3;
        p.type="invented";reject(()->EliteRules.validate(valid));p.type="sentry";
        valid.eliteKits.get(0).kit.id="自建精英兵";reject(()->EliteRules.validate(valid));
        var roomA=new EliteRuntime();var roomB=new EliteRuntime();UUID blue=UUID.randomUUID(),red=UUID.randomUUID(),other=UUID.randomUUID();
        roomA.reserve(blue,0,"sentry",10,100);check(!roomA.blocker(other,0,"sentry",100).isEmpty(),"same team/type occupied");
        check(roomA.blocker(red,1,"sentry",100).isEmpty(),"other team independent");roomA.reserve(red,1,"sentry",10,100);
        check(roomB.blocker(other,0,"sentry",100).isEmpty(),"other room independent");roomB.reserve(other,0,"sentry",10,100);
        reject(()->roomA.reserve(blue,0,"runner",10,100));
        roomA.release(blue,200);check(!roomA.blocker(other,0,"sentry",399).isEmpty(),"cooldown starts on release");
        check(roomA.blocker(other,0,"sentry",400).isEmpty(),"cooldown ends exactly");
        check(!roomA.blocker(other,1,"sentry",400).isEmpty(),"blue release cannot free red elite");
        check(!roomB.blocker(blue,0,"sentry",400).isEmpty(),"one room release cannot free another room elite");
        roomA.reserve(blue,0,"runner",10,400);roomA.cancel(blue);check(roomA.blocker(blue,0,"runner",400).isEmpty(),"failed grant rollback releases reservation without cooldown");
        UUID host=UUID.randomUUID(),guest=UUID.randomUUID();
        check(RoomPolicy.canManage(false,host,host),"ordinary room host can start/close own room");
        check(!RoomPolicy.canManage(false,guest,host),"other ordinary player cannot manage this room");
        check(RoomPolicy.canManage(true,guest,host),"administrator can manage all rooms");
        String engine=Files.readString(Path.of("src/main/java/cn/bfops/Engine.java"));
        for(String op:List.of("lobbyCreate","createRoom")){String c=block(engine,op);check(!c.contains("admin(player)")&&c.contains("createRoom("),"creation allowed for ordinary players");}
        for(String op:List.of("save","delete","scan","lobbySet"))check(block(engine,op).contains("admin(player)"),"editing/configuration remains administrator-only: "+op);
        for(String op:List.of("lobbyStart","lobbyClose","start","stop"))check(block(engine,op).contains("requireRoomControl"),"room lifecycle guarded by owner/admin: "+op);
        check(calls("Engine","claimElite","EliteRuntime","reserve"),"server reserves authoritative elite claim");
        check(calls("Engine","claimElite","EliteRuntime","cancel"),"server has equipment rollback reservation cleanup");
        check(calls("Engine","death","Engine","releaseElite"),"actual death releases elite");
        check(calls("Engine","leave","Engine","releaseElite"),"room exit releases elite");
        check(calls("Engine","deploy","Engine","releaseElite"),"redeployment clears stale elite state");
        check(calls("Engine","deploy","FixedClasses","resolve"),"normal deploy excludes elite identities");
        check(calls("CombatHud","draw","EliteHud","draw"),"combat HUD interaction hint is connected");
        check(calls("MapIcons",null,"HeroIcons","draw")&&calls("WorldMarkers","draw","HeroIcons","draw"),"same elite icons on map and world HUD");
        try(var shipped=new ZipFile("build/libs/battlefield-ops-0.8.10-neoforge-1.21.1.jar");var reference=new ZipFile("gd656conquest-0.10.6-normal.jar")){
            for(var kind:EliteRules.Kind.values()){
                String path="textures/gui/support/herokit_"+kind.key+".png";
                byte[] x=shipped.getInputStream(shipped.getEntry("assets/battlefield_ops/"+path)).readAllBytes();byte[] y=reference.getInputStream(reference.getEntry("assets/gd656conquest/"+path)).readAllBytes();
                check(Arrays.equals(x,y),"reference elite icon copied exactly: "+kind.key);
            }
        }
        System.out.println("EliteClassSim: "+checks+" fixed-class, permission, pickup and room-isolation checks passed (no live game test)");
    }
}
