package cn.bfops;
import java.util.List;
import com.google.gson.Gson;

public final class LoadoutSim {
    private static int checks;
    private static void check(boolean ok){if(!ok)throw new AssertionError("loadout case "+checks);checks++;}
    private static void rejects(Runnable action){try{action.run();throw new AssertionError("unauthorized selection accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        Model.Kit old=new Gson().fromJson("{\"id\":\"old\",\"primary\":\"test:rifle\",\"secondary\":\"\",\"ammoStacks\":3}",Model.Kit.class);
        check(old.options(true).equals(List.of("test:rifle")));
        check(old.options(false).equals(List.of("")));
        check(old.select(true,null).equals("test:rifle"));
        old.primaryOptions.add("test:smg");old.primaryOptions.add("test:rifle");
        check(old.options(true).size()==2);
        check(old.select(true,"test:smg").equals("test:smg"));
        rejects(()->old.select(true,"other_map:gun"));
        rejects(()->old.select(false,"test:rifle"));
        rejects(()->old.select(true,""));
        Model.Kit second=new Model.Kit();second.primary="test:sniper";
        rejects(()->second.select(true,"test:smg"));
        check(old.select(false,"").isEmpty());
        check(new Gson().fromJson(new Gson().toJson(old),Model.Kit.class).options(true).equals(old.options(true)));
        old.primaryOptions.clear();check(old.options(true).equals(List.of("test:rifle")));
        System.out.println("LOADOUT: "+checks+" assertions passed");
    }
}
