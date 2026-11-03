package cn.bfops;
import java.nio.file.*;
import java.util.*;
public final class InfantryAttachmentsSim {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static void reject(Runnable action,String why){boolean failed=false;try{action.run();}catch(IllegalArgumentException ex){failed=true;}check(failed,why);}
    public static void main(String[] args)throws Exception{
        var kit=new Model.Kit();kit.primary="tacz:rifle";
        var groups=List.of(new Fittings.Group("SCOPE","Optics",List.of(new Fittings.Option("tacz:scope","Scope","attachment","tacz:scope"))),new Fittings.Group("MUZZLE","Muzzle",List.of(new Fittings.Option("tacz:silencer","Silencer","attachment","tacz:silencer"))));
        var chosen=Map.of("SCOPE","tacz:scope");
        InfantryAttachments.authorize(kit,kit.primary,chosen,0,groups);check(true,"legacy kits allow compatible fittings without migration");
        var policy=new InfantryAttachments.Policy();kit.attachments.put(kit.primary,policy);
        var scope=new InfantryAttachments.Entry();scope.unlockLevel=10;policy.entries.put("tacz:scope",scope);
        reject(()->InfantryAttachments.authorize(kit,kit.primary,chosen,9,groups),"under-level selection rejected before deployment");
        InfantryAttachments.authorize(kit,kit.primary,chosen,10,groups);check(true,"exact level unlock");
        scope.disabled=true;reject(()->InfantryAttachments.authorize(kit,kit.primary,chosen,100,groups),"explicit deny overrides level 100");
        policy.whitelist=true;scope.disabled=false;
        reject(()->InfantryAttachments.authorize(kit,kit.primary,Map.of("MUZZLE","tacz:silencer"),100,groups),"unlisted compatible fitting is blocked");
        InfantryAttachments.authorize(kit,kit.primary,Map.of(),0,groups);check(true,"default/no attachment always available");
        reject(()->InfantryAttachments.authorize(kit,kit.primary,Map.of("MUZZLE","tacz:scope"),100,groups),"forged slot fails compatibility even when entry unlocked");
        reject(()->InfantryAttachments.authorize(kit,kit.primary,Map.of("SCOPE","evil:injected"),100,groups),"unscanned id rejected");
        var other=new Model.Kit();other.primary=kit.primary;InfantryAttachments.authorize(other,other.primary,chosen,0,groups);check(true,"same gun in another class has separate policy");
        kit.validateOptions();var copy=BattlefieldOps.GSON.fromJson(BattlefieldOps.GSON.toJson(kit),Model.Kit.class);
        check(copy.attachments.get(kit.primary).whitelist,"whitelist survives map JSON roundtrip");
        check(copy.attachments.get(kit.primary).entries.get("tacz:scope").unlockLevel==10,"unlock level survives roundtrip");
        check(InfantryAttachments.blocker(copy,kit.primary,"tacz:silencer",100).contains("白名单"),"restored permission enforced");
        scope.unlockLevel=101;reject(kit::validateOptions,"out of range levels cannot save");scope.unlockLevel=10;
        kit.primary="tacz:removed";reject(kit::validateOptions,"orphaned weapon policy cannot save");kit.primary="tacz:rifle";
        for(int level=0;level<=100;level++){check(PlayerProgress.levelFor(PlayerProgress.totalForLevel(level))==level,"source XP threshold "+level);if(level>0)check(PlayerProgress.levelFor(PlayerProgress.totalForLevel(level)-1)==level-1,"below threshold "+level);}
        check(PlayerProgress.matchExperience(400,true)==9,"reference win score formula");check(PlayerProgress.matchExperience(400,false)==6,"reference loss/draw formula");
        Path folder=Files.createTempDirectory("bfops-attachments-");UUID id=UUID.randomUUID();
        var progress=new PlayerProgress(folder);check(progress.level(id)==0,"new career starts level zero");
        check(progress.award(id,0,true)==7,"match award persisted");check(new PlayerProgress(folder).level(id)==1,"restart restores unlock level");
        progress.award(id,Integer.MAX_VALUE,true);check(progress.level(id)==100,"level cap enforced");
        progress.setLevel(id,15);check(new PlayerProgress(folder).level(id)==15,"administrator level adjustment persists");
        String previous=Files.readString(folder.resolve("progression.json.bak"));check(previous.contains(id.toString()),"previous complete XP file retained");
        Files.writeString(folder.resolve("progression.json"),"broken");boolean rejected=false;try{new PlayerProgress(folder).award(id,100,true);}catch(java.io.IOException ex){rejected=true;}
        check(rejected&&Files.readString(folder.resolve("progression.json")).equals("broken"),"damaged progression never overwritten with zero data");
        System.out.println("InfantryAttachmentsSim: "+checks+" checks passed");
    }
}
