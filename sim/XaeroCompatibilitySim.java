package cn.bfops;
import cn.bfops.compat.BattlefieldMixinPlugin;
public final class XaeroCompatibilitySim {
    public static void main(String[] args){
        boolean world=BattlefieldMixinPlugin.worldMapCompatible(), mini=BattlefieldMixinPlugin.xaeroCompatible();
        System.out.println("worldMap="+world+" minimap="+mini);
        if(args.length>0&&args[0].equals("world")&&!world)throw new AssertionError("World map hook unavailable");
        if(args.length>0&&args[0].equals("minimap")&&!mini)throw new AssertionError("Minimap hook unavailable");
    }
}
