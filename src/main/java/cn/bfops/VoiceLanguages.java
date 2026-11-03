package cn.bfops;
import java.util.*;
import cn.bfops.voice.common.audio.CampType;
/** Per-map speech selection, evaluated on the server using the speaker's actual team. */
public final class VoiceLanguages {
    public record Language(String id,String label){}
    public static final List<Language> ALL=List.of(new Language("AUTO","跟随地图阵营"),new Language("CN","中文（现有配音）"),new Language("UK","英语（英军）"),new Language("US","英语（美军）"),new Language("DE","德语"),new Language("FR","法语"),new Language("IT","意大利语"),new Language("RU","俄语"),new Language("TK","土耳其语"),new Language("NONE","关闭战场语音"));
    public static boolean valid(String id){return id!=null&&ALL.stream().anyMatch(l->l.id().equals(id));}
    public static CampType resolve(Model.Arena map,int team){
        if(map==null||team<0||team>1)return CampType.CN;
        String selected=team==0?map.blueVoiceLanguage:map.redVoiceLanguage;
        if(selected==null||selected.equals("AUTO"))selected=switch(Factions.id(map,team)){
            case "germanempire","austrohungarianempire" -> "DE";
            case "kingdomofitaly" -> "IT";
            case "ottomanempire" -> "TK";
            case "unitedkingdom" -> "UK";
            case "unitedstatesofamerica" -> "US";
            default -> "CN";
        };
        return CampType.fromString(selected);
    }
}
