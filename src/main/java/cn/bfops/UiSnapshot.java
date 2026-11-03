package cn.bfops;
import com.google.gson.*;
import java.util.Set;
/** Control state excludes live map telemetry; rendering still reads every fresh server snapshot. */
final class UiSnapshot {
 private static final Set<String> VISUAL=Set.of("mates","marks","flags","hazards","supportDevices","elapsed","squadRoster");
 static JsonObject controls(JsonObject state){
  var result=new JsonObject();
  for(var entry:state.entrySet()){
   String key=entry.getKey();if(VISUAL.contains(key))continue;JsonElement value=entry.getValue();
   if(key.equals("me")&&value.isJsonObject())value=withoutPosition(value.getAsJsonObject());
   else if((key.equals("deploySites")||key.equals("vehicles"))&&value.isJsonArray()){
    var sites=new JsonArray();for(var site:value.getAsJsonArray())sites.add(site.isJsonObject()?withoutPosition(site.getAsJsonObject()):site);value=sites;
   }
   result.add(key,value);
  }
  return result;
 }
 private static JsonObject withoutPosition(JsonObject source){
  var result=new JsonObject();for(var entry:source.entrySet())if(!Set.of("x","y","z","yaw","pitch").contains(entry.getKey()))result.add(entry.getKey(),entry.getValue());return result;
 }
}
