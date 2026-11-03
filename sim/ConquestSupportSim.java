package cn.bfops;
import java.util.*;
import java.nio.file.*;
import java.util.zip.ZipFile;
import com.google.gson.*;

public final class ConquestSupportSim {
    static int checks;
    static void check(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        UUID a=new UUID(0,1),b=new UUID(0,2);var budget=new SupportRules.Budget(16);
        check(!budget.consume(a,0,false),"full recipient consumes no charge");check(budget.remaining==16,"unchanged recipient preserves uses");
        check(budget.consume(a,0,true),"first successful resupply");check(budget.remaining==15,"one charge consumed");
        check(!budget.ready(a,599),"recipient cooldown");check(budget.ready(a,600),"cooldown boundary");
        check(budget.ready(b,1),"another teammate does not inherit cooldown");
        for(int i=0;i<20;i++)budget.consume(new UUID(1,i),1000,true);
        check(budget.remaining==0,"budget never below zero");check(!budget.consume(a,10000,true),"depleted box cannot supply");
        for(var kind:SupportRules.Kind.values()){
            check(ConquestEquipment.kind(kind.key())==kind,"canonical tool ID");
            check(ConquestEquipment.kind("gd656conquest:"+kind.id)==kind,"reference ID adapter");
            check(ConquestEquipment.kind("othermod:"+kind.id)==null,"foreign namespace not hijacked");
        }
        check(!SupportRules.sameRoomTeam("a",0,"b",0),"rooms isolated");
        check(!SupportRules.sameRoomTeam("a",0,"a",1),"enemy cannot use friendly box");
        check(SupportRules.sensorContact("a",0,"a",1,true,100),"10m scan boundary inclusive");
        check(!SupportRules.sensorContact("a",0,"a",1,true,100.01),"beyond scan range rejected");
        check(!SupportRules.sensorContact("a",0,"b",1,true,1),"scanner cannot cross rooms");
        check(!SupportRules.sensorContact("a",0,"a",0,true,1),"scanner excludes friends");
        check(!SupportRules.sensorContact("a",0,"a",1,false,1),"scanner excludes dead");
        check(!SupportRules.sensorContact("a",0,"a",1,true,Double.NaN),"invalid scan coordinate rejected");
        check(SupportRules.consumeBeacon(6,false)==6,"failed deployment preserves beacon charge");
        int remaining=6;for(int i=0;i<8;i++)remaining=SupportRules.consumeBeacon(remaining,true);
        check(remaining==0,"six uses exhaust without underflow");
        check(SupportRules.feedbackIcon("医疗补给").equals("medic"),"medical feedback is not a kill skull");
        check(SupportRules.feedbackIcon("地面侦测").equals("mark"),"sensor feedback remains spotting");
        check(SupportRules.feedbackIcon("信标部署").equals("rescue"),"beacon feedback is reinforcement");
        for(String cue:List.of("ammo_throw","ammo_pickup","heal","beacon_deploy","sensor_fire","sensor_activate","sensor_beep"))check(SoundBank.pick(cue,0)!=null,"support action has reachable sound "+cue);
        try(var z=new ZipFile("build/libs/battlefield-ops-0.8.10-neoforge-1.21.1.jar")){
            var model=JsonParser.parseString(new String(z.getInputStream(z.getEntry("assets/minecraft/models/item/paper.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var overrides=model.getAsJsonArray("overrides");check(overrides.size()==7,"six custom models and upper range fallback");
            for(var row:overrides){var entry=row.getAsJsonObject();String name=entry.get("model").getAsString().replace("battlefield_ops:","assets/battlefield_ops/models/")+".json";check(z.getEntry(name)!=null,"reachable item model "+name);}
            for(var kind:SupportRules.Kind.values()){
                String name="assets/battlefield_ops/models/item/support/"+kind.id+".json";
                var data=JsonParser.parseString(new String(z.getInputStream(z.getEntry(name)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                String texture=data.getAsJsonObject("textures").get("layer0").getAsString().replace("battlefield_ops:","assets/battlefield_ops/textures/")+".png";
                check(z.getEntry(texture)!=null,"reachable native equipment sprite");
            }
        }
        System.out.println("ConquestSupportSim: "+checks+" checks passed");
    }
}
