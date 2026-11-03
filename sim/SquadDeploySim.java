package cn.bfops;
import java.util.List;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class SquadDeploySim {
    static int checks;
    static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        check(SquadRules.same("a",0,1,"a",0,1),"real squad membership");
        check(!SquadRules.same("a",0,1,"b",0,1),"room isolation");
        check(!SquadRules.same("a",0,1,"a",1,1),"opposing squad numbers isolated");
        check(!SquadRules.same("a",0,0,"a",0,0),"unassigned is not a squad");
        check(SquadRules.canJoin(8,3),"last free slot");check(!SquadRules.canJoin(1,4),"full squad");
        check(!SquadRules.canJoin(9,0),"invalid squad");
        check(SquadRules.dynamic("VEHICLE")&&SquadRules.dynamic("BEACON")&&SquadRules.dynamic("TEAMMATE"),"dynamic sites identified");
        check(!SquadRules.dynamic("BASE"),"base can still spawn vehicles");
        var state=new JsonObject();state.add("deploySites",new JsonArray());
        var map=new Model.Arena();map.spawns.add(new Model.Spawn());
        check(Deploy.fromState(state,map,0).isEmpty(),"empty server list must not invent local sites");
        JsonObject site=new JsonObject();site.addProperty("id","mate:uuid");site.addProperty("label","队友 Alex");site.addProperty("allows","");site.addProperty("kind","TEAMMATE");
        state.getAsJsonArray("deploySites").add(site);
        var decoded=Deploy.fromState(state,map,0).getFirst();
        check(decoded.label().equals("队友 Alex")&&decoded.id().equals("mate:uuid"),"stable ID separate from display label");
        check(!decoded.allows('G')&&!decoded.allows('A'),"dynamic site cannot refresh a vehicle");
        var polygon=List.of(new Model.Vec(-100,-30),new Model.Vec(100,-30),new Model.Vec(100,30),new Model.Vec(-100,30));
        for(int[] dims:new int[][]{{640,360},{960,540},{480,360},{320,240},{1280,720}})for(double fov:new double[]{30,70,110}){
            int w=dims[0],h=dims[1];var viewport=Ui.headerBody(Layout.of(w,h).right);
            var frame=DeployView.frame(polygon,64,fov,w,h,viewport,4096);
            check(frame.fits(),"frame fits loaded budget");
            for(var p:polygon){
                var screen=Projector.project(new Vec3(frame.x(),frame.y(),frame.z()),new Vector3f(0,-1,0),new Vector3f(1,0,0),new Vector3f(0,0,1),p.x(),64,p.z(),fov,w,h,1.2f);
                check(screen!=null&&viewport.inside(screen.x(),screen.y()),"all corners must stay in unobscured viewport "+w+" fov "+fov);
            }
        }
        var narrow=DeployView.frame(polygon,64,70,640,360,new Layout.Rect(200,30,420,300),1024);
        var bigger=DeployView.frame(List.of(new Model.Vec(-200,-60),new Model.Vec(200,60)),64,70,640,360,new Layout.Rect(200,30,420,300),1024);
        check(bigger.headroom()>narrow.headroom(),"larger map needs greater framing height");
        check(!DeployView.frame(polygon,64,70,640,360,new Layout.Rect(200,30,420,300),16).fits(),"loading limit reported honestly");
        framingHonesty();
        beaconFate();
        System.out.println("SquadDeploySim: "+checks+" checks passed");
    }

    /**
     * A beacon must survive its squad walking away from it.
     *
     * <p>The bug this locks: the cleanup loop asked the world for the entity, which returns null when the
     * chunk is not loaded, and treated null as "destroyed". Every beacon was therefore deleted within one
     * second of its owner leaving the area — the exact opposite of what a squad beacon is for. The three
     * columns that matter are (chunk loaded, entity present); this walks all eight combinations of the
     * booleans so the reasoning is stated once rather than inferred from the engine's control flow.
     */
    static void beaconFate(){
        // The normal, working case.
        check(SquadRules.beaconFate(true,true,false,true,true,true,true)==null,"a live beacon in a loaded chunk stays");
        // The case the bug broke: chunk unloaded, so the entity is unreadable. It must stay.
        check(SquadRules.beaconFate(true,true,false,false,false,false,true)==null,
                "an unloaded chunk does not mean the beacon is gone");
        // Loaded chunk and the entity really is gone: that is a destroyed beacon.
        check("destroyed".equals(SquadRules.beaconFate(true,true,false,true,false,false,true)),
                "a loaded chunk with no entity means destroyed");
        check("destroyed".equals(SquadRules.beaconFate(true,true,false,true,true,false,true)),
                "a loaded chunk with a dead entity means destroyed too");
        // Facts that do not need the entity at all, and outrank its state.
        check("expired".equals(SquadRules.beaconFate(true,true,true,false,false,false,true)),
                "expiry ends a beacon even in an unloaded chunk");
        check("owner left".equals(SquadRules.beaconFate(false,true,false,false,false,false,true)),
                "the owner leaving ends it");
        check("match ended".equals(SquadRules.beaconFate(true,false,false,false,false,false,true)),
                "the match ending ends it");
        check("squad changed".equals(SquadRules.beaconFate(true,true,false,false,false,false,false)),
                "changing squad ends it");
        // Exhaustive: no combination of inputs may return an empty-string reason or throw.
        int kept=0,removed=0;
        for(int mask=0;mask<128;mask++){
            String fate=SquadRules.beaconFate((mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0,
                    (mask&16)!=0,(mask&32)!=0,(mask&64)!=0);
            check(fate==null||!fate.isBlank(),"every fate is either keep or a stated reason (mask "+mask+")");
            if(fate==null)kept++;else removed++;
        }
        check(kept+removed==128,"all 128 boolean combinations are classified");
        // Kept is the exact set where the beacon is legitimately alive: owner present, match running, same
        // squad, not expired, and (unloaded chunk OR a live entity).
        int expectedKept=0;
        for(int mask=0;mask<128;mask++){
            boolean ownerPresent=(mask&1)!=0,matchRunning=(mask&2)!=0,expired=(mask&4)!=0,loaded=(mask&8)!=0;
            boolean present=(mask&16)!=0,alive=(mask&32)!=0,same=(mask&64)!=0;
            if(ownerPresent&&matchRunning&&same&&!expired&&(!loaded||(present&&alive)))expectedKept++;
        }
        check(kept==expectedKept,"kept beacons == the states that are actually alive ("
                +kept+" of 128)");
    }

    /**
     * The fit report has to describe the camera that is actually placed.
     *
     * <p>The bug this locks: `frame` reported `fits` from a formula that ignored the height it had just
     * chosen. For a small area lifted to MIN_Y at a moderate view distance the reported answer and the real
     * camera disagreed, so the warning either fired on a perfectly framed map or stayed silent on a cropped
     * one. Both are worse than no warning, because they teach the operator to ignore it.
     */
    static void framingHonesty(){
        // The two fits functions must agree whenever the drawn height is MIN_Y, which is the small-area case.
        for(int view:new int[]{40,60,80,100,128}){
            double radius=8;
            var placement=DeployView.place(0,0,radius*2,64,view);
            check(placement.fits()==DeployView.fitsFromHeight(radius,placement.headroom(),view),
                    "place() reports the fit of the height it chose (view "+view+")");
            check(placement.fits()==(placement.overflow()==0),"overflow is zero exactly when it fits (view "+view+")");
        }
        // A view distance below MIN_Y cannot fit anything, however small the area: the camera alone is
        // already farther from the ground than the renderer will draw.
        int floor=(int)DeployView.MIN_Y;
        check(!DeployView.fitsFromHeight(0,DeployView.MIN_Y,floor-1),
                "a view distance under the minimum height fits nothing");
        check(DeployView.fitsFromHeight(0,DeployView.MIN_Y,floor),
                "and exactly at the minimum height fits a point");
        // A zero or negative view distance falls back to the default rather than reporting nonsense.
        check(DeployView.fitsFromHeight(8,DeployView.MIN_Y,0)
                        ==DeployView.fitsFromHeight(8,DeployView.MIN_Y,DeployView.DEFAULT_VIEW),
                "an unset view distance uses the default");
        // frame() and place() answer different questions and must not be asserted equal. `frame` derives the
        // height from the area's extent *and* the pixel size of the unobscured viewport, because the point is
        // to fit the ground plan into the space the HUD leaves free. `place` derives it from the span alone,
        // with no viewport at all. For the same extent they legitimately differ, and asserting equality here
        // was the test being wrong rather than the code.
        var square=List.of(new Model.Vec(-40,-40),new Model.Vec(40,-40),new Model.Vec(40,40),new Model.Vec(-40,40));
        var framed=DeployView.frame(square,64,70,640,360,new Layout.Rect(0,0,640,360),4096);
        var placed=DeployView.place(0,0,80,64,4096);
        // What must hold for both: the height is at least the minimum, and the fit report is honest about the
        // height that was actually chosen.
        check(framed.headroom()>=DeployView.MIN_Y,"frame() never puts the camera below the minimum height");
        check(placed.headroom()>=DeployView.MIN_Y,"place() never puts the camera below the minimum height");
        double framedRadius=Math.hypot(40,40);
        check(framed.fits()==DeployView.fitsFromHeight(framedRadius,framed.headroom(),4096),
                "frame() reports the fit of its own placement");
        check(placed.fits()==DeployView.fitsFromHeight(40,placed.headroom(),4096),
                "place() reports the fit of its own placement");
        // And a bigger area must never be framed lower than a smaller one, for either entry point.
        check(DeployView.place(0,0,400,64,4096).headroom()>=DeployView.place(0,0,80,64,4096).headroom(),
                "place() lifts higher for a bigger area");
        // Every placement either fits with zero overflow or does not fit with positive overflow. Never both,
        // never neither -- a marker at exactly the limit must land on one side.
        for(int view:new int[]{16,32,64,128,256}){
            for(double span:new double[]{8,40,120,400,1200}){
                var p=DeployView.place(0,0,span,64,view);
                check(p.fits() ? p.overflow()==0 : p.overflow()>0,
                        "fit and overflow are consistent (span "+span+", view "+view+")");
                check(p.headroom()>=DeployView.MIN_Y,"the camera never sits below the minimum height");
            }
        }
    }
}
