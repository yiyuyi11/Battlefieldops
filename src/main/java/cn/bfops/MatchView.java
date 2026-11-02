package cn.bfops;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
/** Own only the perspective during active play; cinematic position remains independent. */
public final class MatchView {
    private static final PerspectivePolicy perspective=new PerspectivePolicy();
    private static boolean clearing;
    public static boolean clearingProne(){return clearing;}
    public static boolean active(JsonObject state){return state!=null&&state.has("inMatch")&&state.get("inMatch").getAsBoolean()&&state.has("me");}
    public static boolean combat(JsonObject state){return active(state)&&state.getAsJsonObject("me").has("alive")&&state.getAsJsonObject("me").get("alive").getAsBoolean();}
    public static boolean canProne(){return combat(Client.state)&&(!Client.state.getAsJsonObject("me").has("downed")||!Client.state.getAsJsonObject("me").get("downed").getAsBoolean());}
    public static void lock(){
        var mc=Minecraft.getInstance();if(mc.player==null||!active(Client.state)){restore();return;}
        CameraType current=mc.options.getCameraType(),next=perspective.update(true,mc.player.isPassenger(),current);
        if(next!=current)mc.options.setCameraType(next);
    }
    static void restore(){var options=Minecraft.getInstance().options;CameraType current=options.getCameraType(),next=perspective.update(false,false,current);if(current!=next)options.setCameraType(next);}
    static void leftMatch(){
        restore();var player=Minecraft.getInstance().player;if(player==null)return;
        clearing=true;try{Class<?> type=Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");Object op=type.getMethod("fromLocalPlayer",net.minecraft.client.player.LocalPlayer.class).invoke(null,player);type.getMethod("crawl",boolean.class).invoke(op,false);}
        catch(ReflectiveOperationException ignored){}finally{clearing=false;}
        DeployMarkers.select("");
    }
}
