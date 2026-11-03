package cn.bfops;
import com.google.gson.Gson;
import cn.bfops.voice.common.audio.CampType;
public final class VoiceLanguageSim {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  Gson gson=new Gson();var old=gson.fromJson("{\"id\":\"old\"}",Model.Arena.class);
  check(old.blueVoiceLanguage.equals("AUTO")&&old.redVoiceLanguage.equals("AUTO"),"old-map defaults");
  check(VoiceLanguages.resolve(old,0)==CampType.CN,"legacy maps retain Chinese");
  for(var first:VoiceLanguages.ALL)for(var second:VoiceLanguages.ALL){
   var map=new Model.Arena();map.blueVoiceLanguage=first.id();map.redVoiceLanguage=second.id();
   var copy=gson.fromJson(gson.toJson(map),Model.Arena.class);
   check(copy.blueVoiceLanguage.equals(first.id())&&copy.redVoiceLanguage.equals(second.id()),"independent per-team serialization");
   if(!first.id().equals("AUTO"))check(VoiceLanguages.resolve(copy,0)==CampType.fromString(first.id()),"team 0 language");
   if(!second.id().equals("AUTO"))check(VoiceLanguages.resolve(copy,1)==CampType.fromString(second.id()),"team 1 language");
  }
  var a=new Model.Arena();a.blueFaction="germanempire";a.redFaction="unitedkingdom";
  check(VoiceLanguages.resolve(a,0)==CampType.DE&&VoiceLanguages.resolve(a,1)==CampType.UK,"automatic nationality selection");
  a.blueVoiceLanguage="FR";check(VoiceLanguages.resolve(a,0)==CampType.FR,"explicit language overrides nationality");
  a.blueVoiceLanguage="NONE";check(VoiceLanguages.resolve(a,0)==CampType.NONE,"mute selection");
  a.blueVoiceLanguage="bad";try{a.validate();throw new AssertionError("invalid language accepted");}catch(IllegalArgumentException expected){check(expected.getMessage().contains("语言"),"server validation");}
  check(CampType.CN.ordinal()==0&&CampType.US.ordinal()==1&&CampType.RU.ordinal()==2&&CampType.COMMON.ordinal()==3,"legacy packet enum positions preserved");
  System.out.println("VoiceLanguageSim: "+checks+" language/serialization checks passed");
 }
}
