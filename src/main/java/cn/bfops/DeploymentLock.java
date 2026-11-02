package cn.bfops;
import com.google.gson.JsonObject;
/** Server-authored lifecycle: downed players remain alive and are never locked here. */
final class DeploymentLock {
    static boolean room(JsonObject state){return state!=null&&state.has("me")&&state.has("roomExists")&&state.get("roomExists").getAsBoolean();}
    static boolean required(JsonObject state) {
        if(!room(state))return false;
        JsonObject me=state.getAsJsonObject("me");
        return !me.has("alive")||!me.get("alive").getAsBoolean();
    }
}
