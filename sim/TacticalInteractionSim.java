package cn.bfops;
import java.util.*;
import com.google.gson.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
public final class TacticalInteractionSim {
    private static int count;
    private static void check(boolean yes,String reason){if(!yes)throw new AssertionError(reason);count++;}
    public static void main(String[] args) {
        check(Spotting.MARK_RANGE==150,"manual marks use requested 150-block acquisition range");
        var eye=new Vec3(0,65,0);var end=eye.add(0,0,150);
        var hit=new AABB(-1,64,149,1,67,151).clip(eye,end);
        check(hit.isPresent(),"a target touching the far endpoint can be aimed at");
        check(new AABB(-1,64,151,1,67,153).clip(eye,end).isEmpty(),"entities outside 150-block ray cannot be selected");
        check(eye.distanceToSqr(hit.get())>eye.distanceToSqr(new Vec3(0,65,20)),"near wall distance beats farther target");
        check(!EnemyTag.roleVisible(EnemyTag.size(150)),"far enemy marker omits illegible class glyph");
        check(EnemyTag.roleVisible(EnemyTag.size(20)),"near enemy marker can contain a class glyph");
        var state=new JsonObject();check(!DeploymentLock.required(state),"lobby never forces deployment");
        state.addProperty("roomExists",true);var me=new JsonObject();me.addProperty("alive",false);state.add("me",me);
        check(DeploymentLock.required(state),"joined but not deployed stays in deployment UI");
        me.addProperty("alive",true);me.addProperty("downed",true);check(!DeploymentLock.required(state),"downed crawler is not trapped in deployment");
        me.addProperty("alive",false);check(DeploymentLock.required(state),"actual death locks deployment again");
        state.addProperty("roomExists",false);check(!DeploymentLock.required(state),"leaving room releases UI lock");
        var mark=new Spotting.Mark(0,1,2,2000,Spotting.Kind.ENEMY,"enemy",66,"uuid","支援兵","ENEMY");
        check(mark.visibleTo(0)&&!mark.visibleTo(1),"marker only shared within its own side");
        check(mark.active(1999)&&!mark.active(2000),"marker expires without permanent tracking");
        for(var kind:Spotting.Kind.values())check(Spotting.Kind.of(kind.id())==kind,"context marker kind round-trip: "+kind);
        var arena=new Model.Arena();arena.boundary=new ArrayList<>(List.of(new Model.Vec(0,0),new Model.Vec(100,0),new Model.Vec(100,100),new Model.Vec(0,100)));
        var common=zone(0,"ALL",0,100);var foot=zone(0,"I",0,25);var ground=zone(0,"G",25,60);var air=zone(0,"A",60,100);
        arena.zones=new ArrayList<>(List.of(common,foot,ground,air));
        check(arena.inZone(0,"I",10,10)&&!arena.inZone(0,"I",80,80),"infantry scope overrides legacy ALL area");
        check(arena.inZone(0,"G",40,40)&&!arena.inZone(0,"G",10,10),"ground vehicle scope is independent");
        check(arena.inZone(0,"A",80,80)&&!arena.inZone(0,"A",40,40),"aircraft scope is independent");
        check(arena.inZone(1,"I",80,80),"other team's unrestricted area stays independent");
        arena.zones.remove(foot);check(arena.inZone(0,"I",80,80),"legacy ALL area remains fallback");
        var gson=new Gson();var copy=gson.fromJson(gson.toJson(arena),Model.Arena.class);
        check(copy.zone(0,"A").mobility.equals("A")&&!copy.inZone(0,"A",10,10),"scoped zones survive save/load");
        var legacy=gson.fromJson("{\"team\":0,\"boundary\":[]}",Model.Zone.class);
        check(legacy.mobility.equals("ALL"),"old map JSON keeps ALL default");
        System.out.println("TacticalInteractionSim: "+count+" targeting, icons, lifecycle, scoped-zone and save/load checks passed");
    }
    private static Model.Zone zone(int team,String type,int min,int max){var z=new Model.Zone();z.team=team;z.mobility=type;z.boundary=new ArrayList<>(List.of(new Model.Vec(min,min),new Model.Vec(max,min),new Model.Vec(max,max),new Model.Vec(min,max)));return z;}
}
