package cn.bfops;
import java.util.*;
import com.google.gson.*;
public final class MinimapSettingsSim {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        var defaults=BattlefieldOps.GSON.fromJson("{}",MinimapOptions.class);check(defaults.span==256&&defaults.size==116&&defaults.squad,"older settings get sensible defaults");
        var config=new MinimapOptions();config.span=Integer.MAX_VALUE;config.size=-1;config.terrainOpacity=999;config.sanitize();
        check(config.span==2048&&config.size==64&&config.terrainOpacity==100,"damaged settings clamped");
        for(int i=0;i<30;i++)config.zoom(true);check(config.span==64,"zoom-in cap");
        for(int i=0;i<30;i++)config.zoom(false);check(config.span==2048,"zoom-out cap");
        config.span=384;config.size=180;config.coordinates=true;config.vehicles=false;config.names=true;
        var loaded=config.copy();check(loaded.span==384&&loaded.size==180&&loaded.coordinates&&!loaded.vehicles&&loaded.names,"settings survive JSON save/load");loaded.span=64;check(config.span==384,"cancel copy cannot mutate active settings");
        for(int width:new int[]{320,426,640,854,1280})for(int height:new int[]{200,240,360,480,720})for(int members=0;members<=4;members++)for(String corner:List.of("TOP_LEFT","TOP_RIGHT","BOTTOM_LEFT","BOTTOM_RIGHT")){
            int roster=MinimapOptions.rosterHeight(members)+17,size=MinimapOptions.fitSize(220,width,height,roster);int[] at=MinimapOptions.origin(corner,width,height,size,roster);
            check(at[0]>=0&&at[0]+size<=width,"map fits horizontal viewport");check(at[1]>=0&&at[1]+size+roster<=height,"map plus roster fits vertical viewport");
        }
        var state=JsonParser.parseString("{\"inMatch\":true,\"me\":{\"name\":\"self\",\"alive\":true},\"squadRoster\":[{\"name\":\"dead\",\"alive\":false},{\"name\":\"down\",\"alive\":true,\"downed\":true},{\"name\":\"self\",\"alive\":true},{\"name\":\"alive\",\"alive\":true},{\"name\":\"self\"}]}").getAsJsonObject();
        var rows=SquadRoster.rows(state);check(rows.size()==4&&rows.getFirst().self(),"self included once, first of the four-member roster");
        check(rows.stream().anyMatch(r->r.name().equals("dead")&&!r.alive()),"waiting squad members remain visible in roster");check(rows.stream().anyMatch(SquadRoster.Row::downed),"downed squad member retained");
        state.addProperty("inMatch",false);check(SquadRoster.rows(state).isEmpty(),"roster cleared outside match");
        check(DeploymentText.label("beacon:"+UUID.randomUUID(),"BEACON","").equals("小队信标"),"beacon UUID never displayed");
        check(DeploymentText.label("mate:"+UUID.randomUUID(),"TEAMMATE","").equals("小队成员"),"teammate UUID never displayed");
        check(DeploymentText.label("vehicle:"+UUID.randomUUID(),"VEHICLE","").equals("友军载具"),"vehicle UUID never displayed");
        check(DeploymentText.label("spawn","BASE","Blue HQ").equals("Blue HQ"),"configured base name retained");
        int[][] arms=SelectionReticle.arms(100,80,10);check(arms.length==4,"four-arm selected crosshair");
        for(var arm:arms)check(arm[0]==arm[2]||arm[1]==arm[3],"axis-aligned crosshair arms");
        System.out.println("MinimapSettingsSim: "+checks+" settings, roster and geometry checks passed (no GPU test)");
    }
}
