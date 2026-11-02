package cn.bfops;
import java.nio.file.*;
import com.google.gson.*;
/** Personal lobby preferences only. Server map policy remains authoritative at deployment. */
final class LoadoutProfiles {
    private static Path file(){return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("battlefield_ops/lobby-loadouts.json");}
    static void load(){
        try{if(!Files.isRegularFile(file()))return;var root=JsonParser.parseString(Files.readString(file())).getAsJsonObject();
            if(root.has("classes"))for(var e:root.getAsJsonObject("classes").entrySet()){
                String[] row=BattlefieldOps.GSON.fromJson(e.getValue(),String[].class);if(row!=null&&row.length>=4&&java.util.Arrays.stream(row).allMatch(s->s!=null&&s.length()<200))Client.lobbyLoadouts.put(e.getKey(),row);
            }
            if(root.has("fittings"))for(var e:root.getAsJsonObject("fittings").entrySet()){
                if(Client.fittingMemory.size()>=512)break;var map=new java.util.LinkedHashMap<String,String>();for(var part:e.getValue().getAsJsonObject().entrySet())if(map.size()<32&&part.getValue().isJsonPrimitive())map.put(part.getKey(),part.getValue().getAsString());Client.fittingMemory.put(e.getKey(),map);
            }
        }catch(Exception ex){BattlefieldOps.LOG.warn("Cannot read lobby loadouts; original file retained",ex);}
    }
    static void save(){
        try{Files.createDirectories(file().getParent());var root=new JsonObject();root.add("classes",BattlefieldOps.GSON.toJsonTree(Client.lobbyLoadouts));root.add("fittings",BattlefieldOps.GSON.toJsonTree(Client.fittingMemory));var temp=file().resolveSibling("lobby-loadouts.json.tmp");Files.writeString(temp,BattlefieldOps.GSON.toJson(root));Files.move(temp,file(),StandardCopyOption.REPLACE_EXISTING);}
        catch(Exception ex){BattlefieldOps.LOG.warn("Cannot save lobby loadouts",ex);}
    }
    private LoadoutProfiles(){}
}
