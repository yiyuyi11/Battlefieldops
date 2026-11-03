package cn.bfops;

import java.util.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;

/** Multi-room policies and actual server/browser call paths; no running Minecraft server required. */
public final class LobbySim {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static Model.Arena map(double x,double z){var a=new Model.Arena();a.exitMargin=0;a.boundary.addAll(List.of(new Model.Vec(x,z),new Model.Vec(x+7,z),new Model.Vec(x+7,z+7),new Model.Vec(x,z+7)));return a;}
    static ClassNode type(String name)throws Exception{var n=new ClassNode();new ClassReader(Files.readAllBytes(Path.of("build/classes/cn/bfops/"+name+".class"))).accept(n,0);return n;}
    static MethodNode method(String type,String name)throws Exception{return type(type).methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    static boolean calls(String cls,String method,String owner,String name)throws Exception{for(var i:method(cls,method).instructions)if(i instanceof MethodInsnNode m&&m.owner.equals("cn/bfops/"+owner)&&m.name.equals(name))return true;return false;}
    public static void main(String[] args)throws Exception{
        var a=map(1,1);var b=map(9,1);
        check(RoomPolicy.overlaps(a,b),"non-overlapping block boxes in the same chunk cannot host separate rooms");
        b=map(32,1);check(!RoomPolicy.overlaps(a,b),"separate chunks can host concurrent rooms");
        a.exitMargin=32;check(RoomPolicy.overlaps(a,b),"exit buffer reserves its chunks too");
        b=map(160,1);b.exitMargin=32;check(!RoomPolicy.overlaps(a,b),"sufficiently separated buffered rooms remain independent");
        b.dimension="minecraft:the_nether";check(!RoomPolicy.overlaps(a,b),"same coordinates across dimensions are independent");
        var negative=map(-8,1);var positive=map(0,1);check(!RoomPolicy.overlaps(negative,positive),"negative chunks use floor, not truncation");
        check(RoomPolicy.hubConflict(a,a.dimension,32,1),"hub must be outside battle and exit buffers");
        check(!RoomPolicy.hubConflict(a,a.dimension,512,1),"distant hub allowed");
        for(String own:List.of("a","b","c",""))for(String requested:List.of("a","b","c"))for(int team=-1;team<=2;team++)for(boolean running:new boolean[]{false,true}){
            check(RoomPolicy.canView(own,team,requested,running)==(running&&team>=0&&team<=1&&own.equals(requested)),"only current room members with a team receive its scoreboard");
            check(RoomPolicy.sameRoom(own,requested)==(!own.isEmpty()&&own.equals(requested)),"stale actions cannot target a replacement room");
        }
        check(!RoomPolicy.sameRoom(null,"a"),"outsider cannot send room action");
        for(int count=0;count<=65;count++)check(RoomPolicy.joinBlocker(false,true,count,64).isEmpty()==(count<64),"capacity enforced at server join time");
        check(!RoomPolicy.joinBlocker(true,true,1,64).isEmpty(),"player cannot join two rooms");
        check(!RoomPolicy.joinBlocker(false,false,0,64).isEmpty(),"closed room rejected");
        var rows=JsonParser.parseString("[{\"id\":\"a\",\"name\":\"Alpha\",\"mode\":\"CONQUEST\",\"modeName\":\"征服\",\"room\":true,\"running\":true,\"players\":8,\"capacity\":8},{\"id\":\"b\",\"name\":\"Beta\",\"mode\":\"BREAKTHROUGH\",\"modeName\":\"突破\",\"room\":true,\"running\":false,\"players\":2,\"capacity\":16},{\"id\":\"c\",\"name\":\"Gamma\",\"mode\":\"CONQUEST\",\"modeName\":\"征服\",\"room\":false,\"running\":false,\"players\":0,\"capacity\":64}]").getAsJsonArray();
        check(LobbyDirectory.visible(rows,"","","全部状态").size()==3,"all maps visible without joining");
        check(LobbyDirectory.visible(rows,"","CONQUEST","全部状态").size()==2,"mode grouping");
        check(LobbyDirectory.visible(rows,"ALPHA","","全部状态").size()==1,"case-insensitive name search");
        check(LobbyDirectory.visible(rows,"突破","","全部状态").size()==1,"Chinese mode search");
        check(LobbyDirectory.visible(rows,"","","可加入").getFirst().get("id").getAsString().equals("b"),"joinable excludes full and closed rooms");
        check(LobbyDirectory.visible(rows,"","","等待开始").size()==1,"waiting excludes closed rooms");
        check(LobbyDirectory.visible(rows,"","","对局进行中").size()==1,"running filter");
        check(LobbyDirectory.visible(rows,"missing","","全部状态").isEmpty(),"no-result filter is stable");
        check(new Model.Arena().maxPlayers==64,"old maps default to 64 slots");
        var settings=new LobbySettings();check(!settings.enabled,"existing servers are not teleported without configuration");settings.validate();
        settings.x=Double.NaN;try{settings.validate();throw new AssertionError("NaN lobby accepted");}catch(IllegalArgumentException expected){checks++;}
        check(calls("Engine","open","Engine","lobby"),"command and F6 share the lobby entry");
        check(calls("Engine","join","RoomPolicy","joinBlocker"),"server calls same authoritative join policy as browser");
        check(calls("Engine","createRoom","RoomPolicy","overlaps"),"server guards chunk conflicts");
        check(calls("Engine","request","RoomPolicy","sameRoom"),"server rejects stale room-scoped requests");
        check(calls("LobbyScreen","entries","LobbyDirectory","visible"),"actual browser uses tested filter");
        Set<String> strings=new HashSet<>();for(var i:method("Engine","lobby").instructions)if(i instanceof LdcInsnNode l&&l.cst instanceof String str)strings.add(str);
        for(String forbidden:List.of("scores","tickets","flags","mates","marks","deploySites"))check(!strings.contains(forbidden),"public lobby does not disclose "+forbidden);
        System.out.println("LobbySim: "+checks+" policy/filter/server-wiring checks passed (not a live multi-client test)");
    }
}
