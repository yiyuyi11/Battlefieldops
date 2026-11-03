package cn.bfops;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

/** Exercises actual rule, validation, deployment, persistence and shared HUD implementations. */
public final class DeathmatchSim {
    private static int checks;
    private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    private static Model.Arena arena(){
        var a=new Model.Arena();a.id="dm";a.mode="DEATHMATCH";a.targetKills=10;
        a.boundary=new ArrayList<>(List.of(new Model.Vec(-200,-200),new Model.Vec(200,-200),new Model.Vec(200,200),new Model.Vec(-200,200)));
        for(int team=0;team<2;team++){var s=new Model.Spawn();s.id="base"+team;s.team=team;s.x=team==0?-100:100;a.spawns.add(s);}
        a.kits.add(new Model.Kit());return a;
    }
    private static void reject(Model.Arena a,String reason){boolean rejected=false;try{a.validate();}catch(IllegalArgumentException ex){rejected=true;}check(rejected,reason);}
    private static JsonObject state(Rules r){var o=new JsonObject();var g=new Gson();o.add("tickets",g.toJsonTree(r.tickets));o.add("teamScores",g.toJsonTree(r.teamScores));return o;}
    public static void main(String[] args)throws Exception{
        var a=arena();a.validate();check(a.points.isEmpty(),"deathmatch needs no fake objective");
        var bad=arena();bad.targetKills=0;reject(bad,"zero target rejected");
        bad=arena();bad.spawns.removeLast();reject(bad,"both teams require spawn");
        bad=arena();bad.mode="CONQUEST";reject(bad,"conquest still requires flags");
        bad=arena();bad.mode="BREAKTHROUGH";reject(bad,"breakthrough still requires flags");
        var zone=new Model.Zone();zone.boundary=new ArrayList<>(List.of(new Model.Vec(-190,-190),new Model.Vec(0,-190),new Model.Vec(0,190),new Model.Vec(-190,190)));
        a.zones.add(zone);a.validate();check(a.inZone(0,-100,0)&&!a.inZone(0,100,0),"deathmatch team boundary enforced");
        var flag=new Model.Point();flag.x=100;a.points.add(flag);a.validate();
        var r=new Rules(a);check(!r.active(0),"leftover map flag inactive");
        check(Deploy.sites(a,0,0,false,new int[]{0}).size()==1,"held leftover objective cannot deploy");
        for(int i=0;i<9;i++)r.tick(new int[][]{{99,0}});
        check(r.flags.getFirst().progress==50&&r.teamScores[0]==0,"presence never captures or grants kills");
        check(!r.tracksTickets(0)&&!r.tracksTickets(1),"neither team has ticket pool");
        int initial=r.tickets[0];r.death(0);r.death(1);check(r.tickets[0]==initial&&r.tickets[1]==initial,"death never deducts tickets");
        r.kill(0,0);r.kill(-1,1);r.kill(0,2);check(r.teamScores[0]==0&&r.teamScores[1]==0,"friendly and invalid team cannot score");
        var victim=new Engine.Member("dm","victim",1);
        if(victim.claimKill())r.kill(0,1);
        if(victim.claimKill())r.kill(0,1);
        check(r.teamScores[0]==1,"down and subsequent bleed out credit once");
        victim.killCredited=false;if(victim.claimKill())r.kill(0,1);
        check(r.teamScores[0]==2,"revived victim can be downed for next credit");
        var second=new Rules(arena());check(second.teamScores[0]==0,"rooms do not share scores");
        var snap=state(r);check(MatchScore.value(a,snap,0)==2&&MatchScore.value(a,snap,1)==0,"HUD reads kills rather than tickets");
        check(MatchScore.line(a,snap).contains("目标 10")&&!MatchScore.line(a,snap).contains("兵力"),"HUD explains target and score type");
        check(MatchScore.value(a,new JsonObject(),0)==0,"pre-snapshot score defaults to zero");
        check(MatchScore.maximum(a)==10,"progress bars use kill target");
        for(int i=2;i<10;i++)r.kill(0,1);
        check(r.winner==0&&r.teamScores[0]==10,"first reaching target wins");
        check(!r.acceptsKillCredit(),"deathmatch reaching target blocks subsequent personal credits");
        check(r.result().contains("机械轴心胜利")&&r.result().contains("机械轴心 10 : 格雷同盟 0"),"final result preserves team score after leaving room");
        r.kill(1,0);r.tick(new int[0][2]);check(r.teamScores[1]==0&&r.winner==0,"finished round freezes scoring and outcome");
        var one=arena();one.targetKills=1;var instant=new Rules(one);instant.kill(1,0);check(instant.winner==1,"one-kill target supports red victory");
        var time=arena();time.roundMinutes=1;var timed=new Rules(time);timed.kill(1,0);
        for(int i=0;i<59;i++)timed.tick(new int[0][2]);check(timed.winner==-2,"timeout does not end early");
        timed.tick(new int[0][2]);check(timed.winner==1,"timeout chooses score leader");
        var tie=new Rules(time);for(int i=0;i<60;i++)tie.tick(new int[0][2]);check(tie.winner==-1,"equal score timeout draws");
        var notices=new Rules(arena());for(int i=0;i<8;i++)notices.kill(0,1);
        var first=notices.drainNotices();check(first.stream().filter(n->n.text().contains("过半")).count()==1,"half target announces once");
        check(first.stream().filter(n->n.text().contains("还剩")).count()==1,"80 percent announces once");
        check(first.stream().filter(n->n.text().contains("领先优势")).count()==1,"20 percent lead announces once");
        check(notices.drainNotices().isEmpty(),"drain prevents repeat every second");
        var leadMap=arena();leadMap.targetKills=100;var lead=new Rules(leadMap);lead.kill(0,1);lead.kill(1,0);lead.kill(1,0);
        check(lead.drainNotices().stream().filter(n->n.text().contains("反超")).count()==1,"lead reversal announces after tie");
        var g=new Gson();var loaded=g.fromJson(g.toJson(a),Model.Arena.class);loaded.validate();
        check(loaded.isDeathmatch()&&loaded.targetKills==10,"JSON reload preserves mode and target");
        var old=g.fromJson("{\"mode\":\"CONQUEST\"}",Model.Arena.class);check(old.targetKills==100,"old map JSON gets safe default");
        var conquest=arena();conquest.mode="CONQUEST";conquest.tickets=1;var lastTicket=new Rules(conquest);lastTicket.death(0);
        check(lastTicket.winner==1&&lastTicket.acceptsKillCredit(),"final conquest ticket death still permits that elimination's personal credit");
        Path dir=Files.createTempDirectory("bfops-dm-");try{
            MapFiles.save(dir,a.id,g.toJson(a));var disk=g.fromJson(Files.readString(dir.resolve("dm.json")),Model.Arena.class);disk.validate();
            check(disk.targetKills==10&&disk.mode.equals("DEATHMATCH"),"actual atomic map save reloads playable rules");
        }finally{Files.deleteIfExists(dir.resolve("dm.json"));Files.deleteIfExists(dir.resolve("dm.json.tmp"));Files.delete(dir);}
        System.out.println("DeathmatchSim: "+checks+" gameplay, persistence, deployment and HUD checks passed");
    }
}
