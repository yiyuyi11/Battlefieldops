package cn.bfops;
import com.google.gson.*;
public final class Bf1SyncSim {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("BF1 sync check "+checks);}
    public static void main(String[] args){
        var state=JsonParser.parseString("""
            {"me":{"name":"self","team":0,"x":0,"y":0,"z":0,"alive":true},
             "mates":[],"deploySites":[{"x":0,"z":0,"deployable":true}],"vehicles":[{"x":0,"state":"READY"}]}
            """).getAsJsonObject();
        var base=UiSnapshot.controls(state);
        for(String key:new String[]{"x","y","z","yaw","pitch"}){
            var moved=state.deepCopy();moved.getAsJsonObject("me").addProperty(key,100);
            check(base.equals(UiSnapshot.controls(moved)));
        }
        var moved=state.deepCopy();moved.getAsJsonArray("deploySites").get(0).getAsJsonObject().addProperty("x",100);
        moved.getAsJsonArray("vehicles").get(0).getAsJsonObject().addProperty("x",200);
        check(base.equals(UiSnapshot.controls(moved)));
        moved.getAsJsonArray("deploySites").get(0).getAsJsonObject().addProperty("deployable",false);
        check(!base.equals(UiSnapshot.controls(moved)));
        var dead=state.deepCopy();dead.getAsJsonObject("me").addProperty("alive",false);
        check(!base.equals(UiSnapshot.controls(dead)));
        var mates=state.getAsJsonArray("mates");
        for(int i=0;i<8;i++){
            var m=new JsonObject();m.addProperty("name","m"+i);m.addProperty("kit","support");m.addProperty("alive",true);
            m.addProperty("team",0);m.addProperty("x",i+1);m.addProperty("y",0);m.addProperty("z",0);mates.add(m);
        }
        mates.get(0).getAsJsonObject().addProperty("self",true);
        mates.get(1).getAsJsonObject().addProperty("hostile",true);
        mates.get(2).getAsJsonObject().addProperty("team",1);
        mates.get(3).getAsJsonObject().addProperty("downed",true);
        mates.get(4).getAsJsonObject().addProperty("alive",false);
        mates.get(5).getAsJsonObject().addProperty("kit","engineer");
        mates.get(6).getAsJsonObject().addProperty("y",41);
        var rows=DeathContacts.medics(state,state.getAsJsonObject("me"));
        check(rows.size()==1&&rows.getFirst().name().equals("m7")&&rows.getFirst().metres()==8);
        mates.get(7).getAsJsonObject().addProperty("kit","medic");
        check(DeathContacts.medics(state,state.getAsJsonObject("me")).size()==1);
        mates.get(7).getAsJsonObject().addProperty("name","self");
        check(DeathContacts.medics(state,state.getAsJsonObject("me")).isEmpty());
        for(int i=0;i<8;i++){
            var m=mates.get(i).getAsJsonObject();m.remove("self");m.remove("hostile");m.remove("downed");
            m.addProperty("name","m"+i);m.addProperty("alive",true);m.addProperty("team",0);m.addProperty("y",0);m.addProperty("kit","支援兵");
        }
        rows=DeathContacts.medics(state,state.getAsJsonObject("me"));
        check(rows.size()==3&&rows.getFirst().metres()==1&&rows.getLast().metres()==3);
        System.out.println("Bf1SyncSim OK "+checks);
    }
}
