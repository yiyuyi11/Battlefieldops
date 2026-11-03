package cn.bfops;

import java.util.*;
import java.nio.file.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Forged masks, dynamic selection transitions, server guard wiring and reference passenger policy. */
public final class DynamicDeploymentSim {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static Deploy.Site site(String id,String kind,String mask){return new Deploy.Site(id,id,0,64,0,0,mask,false,kind);}
    static boolean calls(String cls,String method,String owner,String name)throws Exception{
        var n=new ClassNode();new ClassReader(Files.readAllBytes(Path.of("build/classes/cn/bfops/"+cls+".class"))).accept(n,0);
        for(var m:n.methods)if(method==null||m.name.equals(method))for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals("cn/bfops/"+owner)&&c.name.equals(name))return true;
        return false;
    }
    public static void main(String[] args)throws Exception{
        var base=site("基地","BASE","G");var air=site("机场","AIR","A");var flag=site("据点 A","POINT","G");
        List<Deploy.Site> all=new ArrayList<>(List.of(base,air,flag));
        for(String kind:List.of("TEAMMATE","BEACON","VEHICLE"))for(String mask:List.of("","G","A","GA")){
            String prefix=kind.equals("TEAMMATE")?"mate:":kind.equals("BEACON")?"beacon:":"vehicle:";
            var dynamic=site(prefix+UUID.randomUUID(),kind,mask);all.add(dynamic);
            check(!dynamic.allows('G')&&!dynamic.allows('A'),"dynamic target cannot advertise a new hull even with forged GA mask");
            check(!DeploymentRules.canSpawnNew(dynamic),"dynamic target cannot create hull");
            check(DeploymentRules.keepVehicle(dynamic,"test:tank").isEmpty(),"snapshot/selection clears old vehicle pick");
            check(Deploy.siteFor(all,dynamic.id(),"test:tank",'G').equals(dynamic.id()),"vehicle pick cannot silently move reinforcement to a base");
            check(Deploy.siteFor(all,dynamic.id(),"",'G').equals(dynamic.id()),"infantry target survives snapshot refresh");
            check(!DeploymentRules.requestBlocker(dynamic,"test:tank",kind.equals("VEHICLE")).isEmpty(),"server rejects new hull at dynamic target");
            check(DeploymentRules.requestBlocker(dynamic,"",kind.equals("VEHICLE")).isEmpty(),"correct infantry/boarding action remains usable");
        }
        check(Deploy.sitesFor(all,'G').equals(List.of(base,flag)),"only legitimate fixed ground sites remain in vehicle pool");
        check(Deploy.sitesFor(all,'A').equals(List.of(air)),"only legitimate fixed airport remains in aircraft pool");
        check(Deploy.siteFor(all,base.id(),"test:jet",'A').equals(air.id()),"explicit vehicle pick may reconcile between fixed bases");
        check(DeploymentRules.requestBlocker(base,"test:tank",false).isEmpty(),"fixed-base vehicle deploy remains supported");
        check(!DeploymentRules.requestBlocker(base,"",true).isEmpty(),"boarding entity cannot masquerade as fixed spawn");
        check(!DeploymentRules.requestBlocker(site("beacon:"+UUID.randomUUID(),"BASE","GA"),"test:tank",false).isEmpty(),"reserved target prefix cannot masquerade as base");
        check(!DeploymentRules.canSpawnNew(site("mystery","UNKNOWN","GA")),"unknown type fails closed");
        check(!DeploymentRules.requestBlocker(site("vehicle:"+UUID.randomUUID(),"VEHICLE","GA"),"",false).isEmpty(),"missing existing hull is not replaced with a new one");
        var json=JsonParser.parseString("{\"deploySites\":[{\"id\":\"beacon:fake\",\"kind\":\"BEACON\",\"allows\":\"GA\"}]}").getAsJsonObject();
        var decoded=Deploy.fromState(json,new Model.Arena(),0).getFirst();check(!decoded.allows('G'),"snapshot decoder cannot turn bad dynamic mask into new vehicle option");
        check(VehicleDeploy.boardingBlocker("a",0,1,false,false,null,-1,0,false).isEmpty(),"friendly empty hull remains boardable");
        check(VehicleDeploy.boardingBlocker("a",0,1,false,true,"a",0,1,true).isEmpty(),"same-squad friendly driver supports passenger deploy");
        check(!VehicleDeploy.boardingBlocker("a",0,1,false,true,"a",0,2,true).isEmpty(),"other squad cannot board under default reference rule");
        check(VehicleDeploy.boardingBlocker("a",0,1,true,true,"a",0,2,true).isEmpty(),"administrator team-sharing option opens same-team passenger deploy");
        check(!VehicleDeploy.boardingBlocker("a",0,1,true,true,"a",1,1,true).isEmpty(),"enemy driver remains blocked even with sharing");
        check(!VehicleDeploy.boardingBlocker("a",0,1,true,true,"b",0,1,true).isEmpty(),"other-room driver blocked");
        check(!VehicleDeploy.boardingBlocker("a",0,1,true,true,"a",0,1,false).isEmpty(),"dead/downed/nonparticipant driver blocked");
        check(SquadRules.teammateBlocker(true,false,true,false,false,false).isEmpty(),"valid squadmate remains deployable");
        check(!SquadRules.teammateBlocker(false,false,true,false,false,false).isEmpty(),"respawning teammate unavailable");
        check(!SquadRules.teammateBlocker(true,true,true,false,false,false).isEmpty(),"downed teammate unavailable");
        check(!SquadRules.teammateBlocker(true,false,false,false,false,false).isEmpty(),"actually dead teammate unavailable");
        check(!SquadRules.teammateBlocker(true,false,true,true,false,false).isEmpty(),"spectator teammate unavailable");
        check(!SquadRules.teammateBlocker(true,false,true,false,true,false).isEmpty(),"creative teammate unavailable like reference");
        check(!SquadRules.teammateBlocker(true,false,true,false,false,true).isEmpty(),"riding teammate directs to existing-hull workflow");
        check(calls("Engine","deploymentOptions","SquadRules","teammateBlocker"),"server invokes teammate-state validator");
        check(calls("Engine","deploy","DeploymentRules","requestBlocker"),"actual deploy request invokes common mode gate");
        check(calls("Engine","spawnVehicle","DeploymentRules","canSpawnNew"),"actual entity-creation helper independently checks fixed spawn");
        check(calls("Engine","deploymentOptions","VehicleDeploy","boardingBlocker"),"server existing-hull options use tested driver policy");
        check(calls("BattleScreen","selectSite","DeploymentRules","canSpawnNew"),"map/list selection applies same vehicle rule");
        check(calls("BattleScreen","sync","DeploymentRules","keepVehicle"),"server refresh cannot resurrect a stale new-hull selection");
        check(calls("VehicleDeployScreen","rebuild","Form","paintRow"),"vehicle card button uses actual scrolling artwork row");
        System.out.println("DynamicDeploymentSim: "+checks+" mode, spoofing, transition, boarding and actual-call-path checks passed");
    }
}
