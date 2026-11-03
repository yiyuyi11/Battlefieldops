package cn.bfops;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import cn.bfops.killicon.common.bonus.BonusRegistry;
public final class RewardRankingSim {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static MethodNode method(String owner,String name)throws Exception{ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Path.of("build/classes/cn/bfops/"+owner+".class"))).accept(c,0);return c.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    static int calls(MethodNode m,String owner,String name){int n=0;for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals("cn/bfops/"+owner)&&c.name.equals(name))n++;return n;}
    static boolean literal(MethodNode m,String s){for(var i:m.instructions)if(i instanceof LdcInsnNode c&&s.equals(c.cst))return true;return false;}
    public static void main(String[] args)throws Exception{
        var ids=Map.of("爆头加成","HEADSHOT","远距离击杀","KILL_LONG_DISTANCE","天降正义","JUSTICE_FROM_ABOVE","背刺","BACKSTAB_KILL","绝境反击","DESPERATE_COUNTERATTACK","连杀加成","KILL_COMBO","终结连杀","INTERRUPTED_STREAK","救援","REVIVE","盲杀","BLIND_KILL","助攻","ASSIST");
        ids.forEach((label,id)->{check(id.equals(FeedbackTypes.bonusId(label,"kill")),"specific bonus not disguised as ordinary kill: "+label);check(BonusRegistry.get(id)!=null,"bonus has original renderer definition: "+id);});
        check(FeedbackTypes.bonusId("","headshot").equals("KILL_HEADSHOT"),"headshot elimination separate from headshot bonus");
        check(calls(method("NativeKillIcons","deathStats"),"killicon/network/packet/DeathPacket","handle")==1,"death and nemesis stats consume original packet");
        check(calls(method("NativeKillIcons","hit"),"killicon/client/stats/ClientStatsManager","recordDamage")==1,"damage stats connected once");
        check(calls(method("NativeKillIcons","receive"),"killicon/client/stats/ClientStatsManager","recordKillDistance")==1,"distance ranking connected");
        check(calls(method("Engine","deploy"),"InfantryAttachments","authorize")==2,"server authorizes both primary and secondary before grant");
        check(calls(method("Engine","fittings"),"InfantryAttachments","blocker")>=1,"preview is governed by same restrictions");
        check(literal(method("Engine","creditKill"),"KILL_COMBO"),"combo sent as separate explicit award");
        check(!literal(method("Engine","awardKill"),"KILL_COMBO"),"base kill not replaced by streak category");
        check(literal(method("Engine","finish"),"ranking"),"final snapshot delivered before room removal");
        // This used to assert the opposite — that the full leaderboard entry was gated behind a live
        // match. That gate was the reported bug: the page refused to open outside a round. The claim is
        // now inverted, and pinned: opening the leaderboard must not consult the match state at all.
        check(calls(method("NativeKillIcons","openScoreboard"),"NativeKillIcons","inMatch")==0,"full leaderboard entry is not room gated");
        System.out.println("RewardRankingSim: "+checks+" checks passed (wiring, not live render validation)");
    }
}
