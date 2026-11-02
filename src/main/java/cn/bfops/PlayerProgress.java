package cn.bfops;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import com.google.gson.*;

/** World-owned progression, independent of vanilla XP/inventory restoration. */
final class PlayerProgress {
    private final Path directory;
    private final Map<UUID,Integer> experience=new HashMap<>();
    private boolean loaded;
    PlayerProgress(Path directory){this.directory=directory;}
    private void load() throws IOException {
        if(loaded)return;
        Path file=directory.resolve("progression.json");
        if(Files.exists(file)){
            try{
                JsonObject data=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                Map<UUID,Integer> next=new HashMap<>();
                for(var entry:data.entrySet())next.put(UUID.fromString(entry.getKey()),Math.clamp(entry.getValue().getAsInt(),0,totalForLevel(100)));
                experience.putAll(next);
            }catch(RuntimeException ex){throw new IOException("等级存档损坏，已保留原文件",ex);}
        }
        loaded=true;
    }
    int level(UUID player) throws IOException {load();return levelFor(experience.getOrDefault(player,0));}
    void setLevel(UUID player,int level) throws IOException {
        Model.range(level,0,100,"玩家等级");load();Integer previous=experience.put(player,totalForLevel(level));
        try{MapFiles.save(directory,"progression",BattlefieldOps.GSON.toJson(experience));}
        catch(IOException ex){if(previous==null)experience.remove(player);else experience.put(player,previous);throw ex;}
    }
    int award(UUID player,int score,boolean won) throws IOException {
        load();int earned=matchExperience(score,won),before=experience.getOrDefault(player,0);
        experience.put(player,Math.min(totalForLevel(100),before+earned));
        try{MapFiles.save(directory,"progression",BattlefieldOps.GSON.toJson(experience));}
        catch(IOException ex){if(before==0)experience.remove(player);else experience.put(player,before);throw ex;}
        return earned;
    }
    // Reference PlayerLevelService: score * .005 + 7 for a win, +4 for lose/draw.
    static int matchExperience(int score,boolean won){return (int)Math.round(Math.max(0,score)*.005+(won?7:4));}
    static int totalForLevel(int level){int l=Math.clamp(level,0,100);return l<=16?l*l+6*l:l<=31?(int)(2.5*l*l-40.5*l+360):(int)(4.5*l*l-162.5*l+2220);}
    static int levelFor(int total){int level=0;while(level<100&&totalForLevel(level+1)<=Math.max(0,total))level++;return level;}
}
