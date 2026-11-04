package cn.bfops;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
/** Resource-pack controlled emblems. Numeric keys are match teams, not nationalities. */
final class FactionArt {
    private static JsonObject cached;
    private static com.google.gson.JsonElement lastArena;
    private static Model.Arena currentArena;
    static Model.Arena arena(){var json=Client.state.get("arena");if(json!=lastArena){if(!java.util.Objects.equals(json,lastArena))currentArena=json==null?null:BattlefieldOps.GSON.fromJson(json,Model.Arena.class);lastArena=json;}return currentArena;}
    static String name(int team){return Factions.name(arena(),team);}
    static void clear(){cached=null;lastArena=null;currentArena=null;}
    static String icon(int team){
        Model.Arena arena=arena();
        String emblem=Factions.emblem(arena,team);
        if(emblem!=null){
            // Some factions only ship a HUD or map plate; walk the three sizes before falling back to the
            // generic base glyph, which is what made German Empire and the UK look "unset".
            for(var candidate:new String[]{emblem,emblem.replace("_deploy_default","_hud_default"),
                    emblem.replace("_deploy_default","_map_default")}){
                var loc=new ResourceLocation("battlefield_ops","textures/gui/bf1/"+candidate+".png");
                if(Minecraft.getInstance().getResourceManager().getResource(loc).isPresent())return candidate;
            }
        }
        if(team<0||team>1)return "base_map_default";
        if(cached==null){
            cached=new JsonObject();
            var id=new ResourceLocation("battlefield_ops","bf1_factions.json");
            try{var resource=Minecraft.getInstance().getResourceManager().getResource(id);
                if(resource.isPresent())try(var reader=resource.get().openAsReader()){
                    cached=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                }
            }catch(Exception e){BattlefieldOps.LOG.warn("Cannot read BF1 faction artwork",e);}
        }
        String value=Engine.str(cached,Integer.toString(team),"base_map_default");
        return value.matches("[a-z0-9_]+")?value:"base_map_default";
    }
}
