package cn.bfops;

public final class InteractionSim {
    /** Same configuration as BattlefieldOps.GSON, without triggering the mod's static initialiser. */
    private static final com.google.gson.Gson JSON =
            new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        for(int height:new int[]{180,200,240,480,1080}){
            var layout=Layout.of(320,height);var area=layout.bar.inset(2);
            check(area.h()>=16,"small window keeps actions");
            Form bar=new Form(area,null);var button=new FrontButton("保存地图",area.x(),area.y(),area.w(),area.h(),()->{});
            bar.add(button);bar.finish();check(button.visible,"edge-aligned save button visible at "+height);
            check(button.isMouseOver(area.cx(),area.cy()),"save hit target at "+height);
            var outside=new FrontButton("clipped",area.x(),area.y()-1,20,area.h(),()->{});bar.add(outside);bar.finish();check(!outside.visible,"partly clipped control hidden");
        }
        check(LibrarySearch.matches("@superb tank","superbwarfare:tank","重型坦克","Superb Warfare superbwarfare 卓越前线"),"namespace plus name");
        check(LibrarySearch.matches("@卓越前线 坦克","superbwarfare:tank","重型坦克","Superb Warfare superbwarfare 卓越前线"),"Chinese mod");
        check(LibrarySearch.matches("@\"Superb Warfare\"","superbwarfare:tank","坦克","Superb Warfare"),"quoted display name");
        check(!LibrarySearch.matches("@tacz","superbwarfare:tank","tacz tank","Superb Warfare"),"source restriction is not name search");
        check(LibrarySearch.matches("@pack ak","pack:ak47","AK-47","pack tacz"),"gunpack namespace");
        Model.Point point=new Model.Point();point.x=0;point.y=64;point.z=0;point.radius=10;point.height=5;
        check(ObjectiveZone.contains(point,10,66.5,0),"inclusive radius and half height");
        check(!ObjectiveZone.contains(point,10.1,64,0),"outside radius");
        check(!ObjectiveZone.contains(point,0,66.6,0),"outside height");
        check(TacticalColors.objective(1,1)==TacticalColors.FRIEND,"red player's point friendly");
        check(TacticalColors.objective(0,1)==TacticalColors.ENEMY,"red player's enemy point");
        check((TacticalColors.ENEMY>>>24)==255,"enemy opaque");
        point.x=12;point.y=64;point.z=18;
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){
            boolean rejected=false;
            try{PointPlacement.move(point,99,invalid,99);}catch(IllegalArgumentException ex){rejected=true;}
            check(rejected&&point.x==12&&point.y==64&&point.z==18,"bad terrain must not corrupt any coordinate");
            // Serialise with a local Gson rather than BattlefieldOps.GSON. Both are built the same way,
            // but touching the mod class runs its static initialiser, which calls LogUtils.getLogger()
            // and needs the ModLauncher environment — under a plain JVM that call blocks forever, which
            // would turn this assertion into a hung test run instead of a result.
            check(!JSON.toJson(point).contains("NaN"),"draft remains serializable after failed placement");
        }
        Model.Spawn spawn=new Model.Spawn();boolean rejected=false;
        try{PointPlacement.move(spawn,Double.NaN,70,10);}catch(IllegalArgumentException ex){rejected=true;}
        check(rejected&&spawn.y==64,"spawn rejects bad coordinates atomically");
        PointPlacement.move(point,1,72,3);check(point.x==1&&point.y==72&&point.z==3,"loaded terrain places normally");
        System.out.println("InteractionSim OK: real button visibility/hit targets, source search, capture cylinder, relative colors");
    }
}
