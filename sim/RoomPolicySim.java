package cn.bfops;

import java.util.List;

public final class RoomPolicySim {
    private static int passed;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed++;
    }
    public static void main(String[] args) {
        check(!RoomPolicy.canView(null,-1,"a",true),"Outsider must never receive live scores");
        check(!RoomPolicy.canView("a",-1,"a",true),"Unassigned room member cannot see live scores");
        check(!RoomPolicy.canView("a",0,"a",false),"Waiting room has no match scoreboard");
        for(int team=0;team<2;team++) {
            check(RoomPolicy.canView("a",team,"a",true),"Own match must be visible");
            check(!RoomPolicy.canView("a",team,"b",true),"Other concurrent room must be private");
        }
        check(!RoomPolicy.canView("a",2,"a",true),"Invalid team");
        Model.Arena a=new Model.Arena();
        a.boundary.addAll(List.of(new Model.Vec(0,0),new Model.Vec(100,0),new Model.Vec(100,100),new Model.Vec(0,100)));
        check(!RoomPolicy.beyond(a,50,64,50),"Centre stays in game");
        check(!RoomPolicy.beyond(a,132,64,50),"Exactly 32 blocks retains grace");
        check(RoomPolicy.beyond(a,132.01,64,50),"Beyond margin exits");
        check(RoomPolicy.beyond(a,-32.01,64,50),"Negative coordinates exit");
        check(!RoomPolicy.beyond(a,120,64,120),"Corner within circular margin");
        check(RoomPolicy.beyond(a,125,64,125),"Corner beyond diagonal distance exits");
        check(RoomPolicy.beyond(a,50,a.maxY+33,50),"Upper vertical margin");
        check(RoomPolicy.beyond(a,50,a.minY-33,50),"Lower vertical margin");
        a.exitMargin=0;
        check(RoomPolicy.beyond(a,100.01,64,50),"GUI zero margin supported");
        a.exitMargin=32;
        a.boundary=List.of(new Model.Vec(0,0),new Model.Vec(100,0),new Model.Vec(100,20),new Model.Vec(20,20),new Model.Vec(20,100),new Model.Vec(0,100));
        check(RoomPolicy.beyond(a,80,64,80),"Concave notch is outside despite bounding box");
        check(!RoomPolicy.beyond(a,40,64,40),"Concave notch grace uses nearest edge");
        check(!RoomPolicy.beyond(a,10,64,80),"Concave polygon arm remains inside");
        System.out.println("ROOM POLICY: " + passed + " assertions passed");
    }
}
