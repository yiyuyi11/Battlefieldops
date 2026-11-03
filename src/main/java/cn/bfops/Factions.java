package cn.bfops;
import java.util.*;
/** Stable persisted faction keys; independent of client resources and server score-team IDs. */
final class Factions {
    record Nation(String id,String name,String emblem){}
    static final List<Nation> ALL=List.of(
        new Nation("legacy","默认阵营",""),
        new Nation("germanempire","德意志帝国","germanempire"),
        new Nation("austrohungarianempire","奥匈帝国","austrohungarianempire"),
        new Nation("kingdomofitaly","意大利王国","kingdomofitaly"),
        new Nation("ottomanempire","奥斯曼帝国","ottomanempire"),
        new Nation("unitedkingdom","大英帝国","unitedkingdom"),
        new Nation("unitedstatesofamerica","美利坚合众国","unitedstatesofamerica"));
    static boolean valid(String id){return id!=null&&ALL.stream().anyMatch(n->n.id().equalsIgnoreCase(id));}
    static String id(Model.Arena arena,int team){
        String value=arena==null?"legacy":team==0?arena.blueFaction:team==1?arena.redFaction:"legacy";
        if(value==null)return "legacy";
        value=value.trim().toLowerCase(java.util.Locale.ROOT);
        return value.isEmpty()?"legacy":value;
    }
    static String name(Model.Arena arena,int team){
        String id=id(arena,team);return ALL.stream().filter(n->n.id().equals(id)&&!id.equals("legacy")).map(Nation::name).findFirst().orElse(Board.label(team));
    }
    static String emblem(Model.Arena arena,int team){String id=id(arena,team);return valid(id)&&!id.equals("legacy")?"bases_"+id+"_deploy_default":null;}
}
