package cn.bfops;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
/** Native decoded BF1 audio event bindings; the JSON can be replaced by a resource pack. */
public final class NativeAudioBank {
    private static JsonObject cached;
    private NativeAudioBank(){}
    public static boolean voiceEnabled(){return ClientPrefs.voiceEnabled();}
    public static boolean nativeEvent(String id){return Bf1AudioCatalog.ALL.contains(id);}
    public static void clear(){cached=null;}
    private static JsonObject data(){
        if(cached!=null)return cached;cached=new JsonObject();
        try{var resource=Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("battlefield_ops","bf1_audio_bank.json"));
            if(resource.isPresent())try(var reader=resource.get().openAsReader()){cached=JsonParser.parseReader(reader).getAsJsonObject();}
        }catch(Exception e){BattlefieldOps.LOG.warn("Native BF1 sound bindings unavailable",e);}
        return cached;
    }
    public static String voice(String language,String cue){return lookup("voices",language,cue);}
    public static String announcer(String language,String cue){return lookup("announcer",language,cue);}
    private static String lookup(String field,String language,String cue){
        var root=data();if(!root.has(field)||!root.get(field).isJsonObject())return null;
        var all=root.getAsJsonObject(field);if(!all.has(language)||!all.get(language).isJsonObject())return null;
        return Engine.str(all.getAsJsonObject(language),cue,null);
    }
    static double duration(String event){var root=data();if(!root.has("durations"))return 3;var d=root.getAsJsonObject("durations");try{return d.has(event)?Math.max(.1,Math.min(60,d.get(event).getAsDouble())):3;}catch(RuntimeException e){return 3;}}
    static String music(String key){var root=data();return root.has("music")?Engine.str(root.getAsJsonObject("music"),key,null):null;}
    static String frontend(int index){var root=data();if(!root.has("frontend"))return null;var list=root.getAsJsonArray("frontend");return list.isEmpty()?null:list.get(Math.floorMod(index,list.size())).getAsString();}
}
