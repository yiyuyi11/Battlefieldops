package cn.bfops;

import com.google.gson.JsonObject;

/** Shared scoreboard semantics: increasing kills must never be presented as remaining troops. */
final class MatchScore {
    private MatchScore(){}
    static int value(Model.Arena arena,JsonObject state,int team){
        String key=arena.isDeathmatch()?"teamScores":"tickets";
        if(state==null||!state.has(key)||!state.get(key).isJsonArray()||team<0||team>1)return 0;
        var values=state.getAsJsonArray(key);
        return values.size()>team?Math.max(0,values.get(team).getAsInt()):0;
    }
    static int maximum(Model.Arena arena){return Math.max(1,arena.isDeathmatch()?arena.targetKills:arena.tickets);}
    static String line(Model.Arena arena,JsonObject state){
        if(state==null||!state.has(arena.isDeathmatch()?"teamScores":"tickets"))return arena.modeName()+" · 未开局";
        int blue=value(arena,state,0),red=value(arena,state,1);
        if(arena.isDeathmatch())return "团队击杀  "+Board.label(0)+" "+blue+" / "+Board.label(1)+" "+red+" · 目标 "+arena.targetKills;
        if(arena.isStaged())return "进攻方兵力 "+blue+" · 阶段 "+(state.has("stage")?state.get("stage").getAsInt()+1:1)+" / "+(state.has("stages")?state.get("stages").getAsInt():1);
        return "兵力  "+Board.label(0)+" "+blue+" / "+Board.label(1)+" "+red;
    }
}
