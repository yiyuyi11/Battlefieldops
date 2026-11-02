package cn.bfops;

import com.google.gson.*;
import java.util.*;

/** Public browser filtering is independent of match state, scores and marker coordinates. */
final class LobbyDirectory {
    static List<JsonObject> visible(JsonArray rows,String query,String mode,String status){
        List<JsonObject> list=new ArrayList<>();String q=query.strip().toLowerCase(Locale.ROOT);
        for(var e:rows)if(e.isJsonObject()){
            var row=e.getAsJsonObject();
            if(!mode.isEmpty()&&!mode.equals(Engine.str(row,"mode","")))continue;
            String hay=(Engine.str(row,"id","")+" "+Engine.str(row,"name","")+" "+Engine.str(row,"modeName","")).toLowerCase(Locale.ROOT);
            if(!hay.contains(q))continue;
            boolean exists=bool(row,"room"),running=bool(row,"running");
            if(status.equals("等待开始")&&(!exists||running))continue;
            if(status.equals("对局进行中")&&!running)continue;
            if(status.equals("可加入")&&(!exists||Engine.num(row,"players",0)>=Engine.num(row,"capacity",64)))continue;
            list.add(row);
        }
        list.sort(Comparator.comparing((JsonObject o)->Engine.str(o,"mode","")).thenComparing(o->Engine.str(o,"name","")).thenComparing(o->Engine.str(o,"id","")));
        return list;
    }
    private static boolean bool(JsonObject o,String key){return o.has(key)&&o.get(key).getAsBoolean();}
}
