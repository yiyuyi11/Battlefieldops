package cn.bfops;
import com.google.gson.Gson;
public final class FactionsSim {
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  Gson gson=new Gson();Model.Arena old=gson.fromJson("{\"id\":\"old\"}",Model.Arena.class);
  check("legacy".equals(old.blueFaction)&&"legacy".equals(old.redFaction),"old maps retain defaults");
  check("机械轴心".equals(Factions.name(old,0)),"legacy name");
  for(var blue:Factions.ALL)for(var red:Factions.ALL){
   Model.Arena map=new Model.Arena();map.blueFaction=blue.id();map.redFaction=red.id();
   Model.Arena copy=gson.fromJson(gson.toJson(map),Model.Arena.class);
   check(copy.blueFaction.equals(blue.id())&&copy.redFaction.equals(red.id()),"per-map save/load");
   check(!Factions.name(copy,0).isBlank()&&!Factions.name(copy,1).isBlank(),"names");
   check(blue.id().equals("legacy")||Factions.emblem(copy,0).contains(blue.id()),"team0 emblem");
   check(red.id().equals("legacy")||Factions.emblem(copy,1).contains(red.id()),"team1 emblem");
  }
  check(Factions.valid("GermanEmpire"),"case-insensitive faction input remains supported");
  for(String invalid:new String[]{null,"unknown","../germanempire"}){
   Model.Arena map=new Model.Arena();map.blueFaction=invalid;
   try{map.validate();throw new AssertionError("invalid faction accepted");}catch(IllegalArgumentException expected){check(expected.getMessage().contains("阵营"),"faction validation must precede persistence");}
  }
  Model.Arena first=new Model.Arena(),second=new Model.Arena();first.blueFaction="germanempire";second.blueFaction="ottomanempire";
  check(!Factions.name(first,0).equals(Factions.name(second,0)),"simultaneous maps retain separate identities");
  System.out.println("FactionsSim: "+checks+" persistence/legacy/validation checks passed");
 }
}
