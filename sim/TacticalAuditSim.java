package cn.bfops;

import com.google.gson.JsonObject;
import java.util.List;

public final class TacticalAuditSim {
    private static int checks;
    private static void check(boolean condition,String message) {
        checks++; if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var eye=new Tactics.At(0,64,0);
        var near=new Tactics.At(10,64,0);
        var far=new Tactics.At(500,64,0);
        check(Tactics.contactVisible(0,0,false,false,eye,far,false),"own vehicles remain available while deploying");
        check(!Tactics.contactVisible(1,0,true,true,eye,near,false),"enemy behind wall withheld");
        check(!Tactics.contactVisible(1,0,true,true,eye,far,true),"far enemy withheld");
        check(!Tactics.contactVisible(1,0,false,true,eye,near,true),"spectator cannot scout");
        check(!Tactics.contactVisible(1,0,true,false,eye,near,true),"enemy radar toggle respected");
        check(Tactics.contactVisible(1,0,true,true,eye,near,true),"visible nearby enemy allowed");
        check(!Tactics.contactVisible(0,-1,true,true,eye,near,true),"non member excluded");
        check(!Tactics.contactVisible(-1,0,true,true,eye,near,false),"unowned hull does not bypass sight");
        var mark=new Spotting.Mark(0,10,20,5000,Spotting.Kind.MATE,"ally",17.25);
        check(mark.y()==17.25,"indoor last seen height retained");
        check(mark.kind()==Spotting.Kind.MATE,"friendly kind retained");
        check(Spotting.visibleTo(List.of(mark),1,1000).isEmpty(),"opposing side cannot read callout");
        check(Spotting.visibleTo(List.of(mark),0,5000).isEmpty(),"expired callout removed");
        check(!Medic.lethal(10,4),"armour-reduced hit must not down");
        check(Medic.lethal(10,10),"exact lethal damage downs");
        check(!Medic.lethal(1,0),"zero damage cannot down");
        check(!Medic.lethal(1,Float.NaN),"invalid damage cannot down");
        var state=new JsonObject();state.addProperty("inMatch",true);
        var arena=new JsonObject();arena.addProperty("glowTeammates",false);state.add("arena",arena);
        check(!MateGlow.allowed(state),"actual flat Arena switch respected");
        arena.addProperty("glowTeammates",true);
        check(MateGlow.allowed(state),"enabled friendly glow allowed");
        state.addProperty("inMatch",false);
        check(!MateGlow.allowed(state),"no glow outside match");
        var map=new Model.Arena();
        var zone=new Model.Zone();zone.team=0;
        zone.boundary=new java.util.ArrayList<>(List.of(new Model.Vec(-10,-10),new Model.Vec(10,-10),new Model.Vec(10,10),new Model.Vec(-10,10)));
        map.zones.add(zone);
        var inside=new Model.Point();inside.id="inside";inside.x=0;inside.z=0;
        var outside=new Model.Point();outside.id="outside";outside.x=30;outside.z=30;
        map.points.add(inside);map.points.add(outside);
        var sites=Deploy.sites(map,0,0,false,new int[]{0,0});
        check(sites.stream().anyMatch(site->site.id().equals(Deploy.pointId(inside))),"owned legal forward point is deployable");
        check(sites.stream().noneMatch(site->site.id().equals(Deploy.pointId(outside))),"owned point outside team zone is not deployable");
        System.out.println("TacticalAuditSim: "+checks+" checks passed");
    }
}
