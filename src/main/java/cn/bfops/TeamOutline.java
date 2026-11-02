package cn.bfops;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import com.google.gson.JsonObject;

public final class TeamOutline {
    private static JsonObject last;
    private static final java.util.Map<String,Boolean> allowed=new java.util.HashMap<>();
    public static int color(Entity entity) {
        var mc=Minecraft.getInstance(); var state=Client.state;
        if(mc.player==null||entity==mc.player||!(entity instanceof Player player)||!entity.isAlive()
                ||!state.has("inMatch")||!state.get("inMatch").getAsBoolean())return 0;
        if(state!=last){
            last=state;allowed.clear();
            if(state.has("mates"))for(var element:state.getAsJsonArray("mates")){
                var row=element.getAsJsonObject();
                if(row.has("alive")&&!row.get("alive").getAsBoolean())continue;
                allowed.put(Engine.str(row,"uuid",Engine.str(row,"name","")),row.has("hostile")&&row.get("hostile").getAsBoolean());
            }
        }
        Boolean hostile=allowed.get(player.getUUID().toString());
        if(hostile==null)hostile=allowed.get(player.getGameProfile().getName());
        if(hostile==null)return 0;
        if(!hostile && !MateGlow.allowed(state))return 0;
        double range=hostile?Tactics.ENEMY_GLOW_RANGE:Tactics.MATE_GLOW_RANGE;
        if(mc.player.distanceToSqr(entity)>range*range)return 0;
        // A stale one-second snapshot must not continue outlining an enemy behind a wall.
        if(hostile&&!mc.player.hasLineOfSight(entity))return 0;
        return hostile?0xffed6675:0xff65b5fa;
    }
}
