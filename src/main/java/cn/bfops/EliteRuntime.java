package cn.bfops;

import java.util.*;

/** One instance per room; occupancy/cooldown is scoped to team + fixed elite type. */
final class EliteRuntime {
    record Claim(String scope,int cooldownSeconds){}
    final Map<UUID,Claim> claims=new HashMap<>();
    final Map<String,UUID> holders=new HashMap<>();
    final Map<String,Long> readyAt=new HashMap<>();
    long startedAt;
    String blocker(UUID player,int team,String type,long now){
        String scope=EliteRules.scope(team,type);
        if(claims.containsKey(player))return "当前已是精英兵";
        if(holders.containsKey(scope))return "本方已有玩家使用该精英兵";
        long remaining=readyAt.getOrDefault(scope,0L)-now;
        return remaining>0?"刷新冷却 "+((remaining+19)/20)+" 秒":"";
    }
    void reserve(UUID player,int team,String type,int cooldown,long now){
        String reason=blocker(player,team,type,now);Model.require(reason.isEmpty(),reason);
        String scope=EliteRules.scope(team,type);claims.put(player,new Claim(scope,cooldown));holders.put(scope,player);
    }
    void release(UUID player,long now){
        Claim claim=claims.remove(player);if(claim==null)return;
        holders.remove(claim.scope,player);readyAt.put(claim.scope,now+claim.cooldownSeconds*20L);
    }
    void cancel(UUID player){Claim claim=claims.remove(player);if(claim!=null)holders.remove(claim.scope,player);}
}
