package cn.bfops;

import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Bytecode wiring guard for the previously disconnected display paths. */
public final class NativeHudWiringSim {
    static int checks;
    static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    static MethodNode method(String type,String name)throws Exception{
        var c=new ClassNode();new ClassReader(Files.readAllBytes(Path.of("build/classes/cn/bfops/"+type+".class"))).accept(c,0);
        return c.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();
    }
    static List<String> strings(MethodNode method){var out=new ArrayList<String>();for(var i:method.instructions)if(i instanceof LdcInsnNode l&&l.cst instanceof String s)out.add(s);return out;}
    static long calls(MethodNode method,String owner,String name){long count=0;for(var i:method.instructions)if(i instanceof MethodInsnNode m&&m.owner.equals("cn/bfops/"+owner)&&m.name.equals(name))count++;return count;}
    public static void main(String[] args)throws Exception{
        var deploy=method("DeployMarkers","glyph");var world=method("WorldMarkers","draw");
        check(calls(deploy,"DeployIcon","capture")==1,"deployment objective uses native capture adapter");
        check(calls(deploy,"DeployIcon","teammate")==1,"deployment teammate uses oriented native icon");
        check(calls(world,"DeployIcon","capture")==1,"combat objective uses same adapter");
        check(calls(method("DeployIcon","lambda$capture$0"),"conquest/client/render/CaptureDiamondRenderer","drawCaptureIconInHud")==1,"adapter invokes original capture renderer");
        var rebuild=strings(method("DeployMarkers","rebuild"));check(rebuild.contains("yaw")&&rebuild.contains("progress"),"snapshot yaw and capture progress reach deploy model");
        var payload=strings(method("Engine","awardKill"));for(String field:List.of("elimination","combo","victimId","comboSeconds"))check(payload.contains(field),"kill payload carries "+field);
        check(calls(method("NativeKillIcons","receive"),"killicon/client/stats/ClientStatsManager","recordGeneralKillStats")==1,"local kill stats have one preset-independent recording site");
        check(strings(method("Engine","hitNotice")).contains("victimId"),"hit info identifies victim");
        check(calls(method("Client$Events","trackDeathAndDeploy"),"NativeKillIcons","death")==1,"actual death resets native combo state");
        check(FeedbackTypes.cosmeticIcon(false,"摧毁载具").equals("vehicle"),"vehicle destruction uses destroy icon");
        check(FeedbackTypes.cosmeticIcon(true,"击杀").equals("headshot"),"cosmetic headshot keeps red skull type");
        check(FeedbackTypes.cosmeticIcon(false,"击杀").equals("kill"),"cosmetic normal kill keeps white skull type");
        System.out.println("NativeHudWiringSim: "+checks+" actual classfile wiring checks passed (not a gameplay test)");
    }
}
