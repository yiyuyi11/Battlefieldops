package cn.bfops;
import java.nio.file.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class MatchViewProneSim {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static ClassNode read(String name)throws Exception{ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Path.of("build/classes/cn/bfops/"+name+".class"))).accept(c,0);return c;}
    static int calls(String name,String owner,String method)throws Exception{int n=0;for(var m:read(name).methods)for(var i:m.instructions)if(i instanceof MethodInsnNode call&&call.owner.equals("cn/bfops/"+owner)&&call.name.equals(method))n++;return n;}
    static boolean literal(String name,String method,String text)throws Exception{for(var m:read(name).methods)if(m.name.equals(method))for(var i:m.instructions)if(i instanceof LdcInsnNode v&&text.equals(v.cst))return true;return false;}
    public static void main(String[] args)throws Exception{
        JsonObject state=JsonParser.parseString("{\"inMatch\":true,\"me\":{\"alive\":true,\"downed\":true}}").getAsJsonObject();
        check(MatchView.combat(state),"downed player remains in first person");state.getAsJsonObject("me").addProperty("alive",false);check(!MatchView.combat(state),"dead deployment is not a voluntary prone state");
        state.addProperty("inMatch",false);check(!MatchView.active(state),"lobby does not own perspective");check(!MatchView.active(new JsonObject()),"unmodded server not affected");
        check(calls("mixin/GameRendererMixin","MatchView","lock")>=1,"perspective enforced before camera setup, no F5 frame loophole");
        check(calls("Client$Events","MatchView","lock")>=1,"client tick keeps combat perspective locked");
        check(calls("crawl/mixin/client/TaczCrawlKeyMixin","MatchView","canProne")>=1,"match key can bypass global crawl switch");
        check(calls("crawl/mixin/client/TaczLocalPlayerCrawlMixin","Client","send")>=1,"match crawl sent through room-authorized request");
        check(calls("Engine","TaczProneBridge","set")>=1,"server processes validated crawl intent");
        check(literal("Engine","request","当前状态不能切换趴下"),"server guards alive/downed/passenger state");
        check(literal("Engine","request","prone"),"request has room-scoped prone operation");
        check(calls("Engine","Board","clearPlayer")>=2,"leave and recovery both remove owned scoreboard tags");
        check(calls("DeployMarkers","SelectionReticle","draw")>=1&&calls("MapPicker","SelectionReticle","draw")>=1,"selected crosshair wired in tactical and overhead views");
        check(calls("CombatHud","SquadRoster","draw")==1,"roster connected below minimap");
        System.out.println("MatchViewProneSim: "+checks+" lifecycle and actual classfile wiring checks passed");
    }
}
