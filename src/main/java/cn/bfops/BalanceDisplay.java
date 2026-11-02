package cn.bfops;

/** Optional read-only bridge: the standalone balance mod remains independent of BattlefieldOps. */
final class BalanceDisplay {
    private static java.lang.reflect.Method damage,head,stamp;
    private static boolean resolved;
    private static void resolve(){
        if(resolved)return;resolved=true;
        try{var c=Class.forName("cn.combatbalance.ClientPolicy");damage=c.getMethod("damageMultiplier",String.class);head=c.getMethod("headshotMultiplier",String.class);stamp=c.getMethod("stamp");}
        catch(ReflectiveOperationException|LinkageError ignored){}
    }
    static double factor(String id,boolean headshot){
        resolve();var method=headshot?head:damage;if(method==null||id.isEmpty())return 1;
        try{return ((Number)method.invoke(null,id)).doubleValue();}catch(ReflectiveOperationException ignored){return 1;}
    }
    static long revision(){resolve();try{return stamp==null?0:((Number)stamp.invoke(null)).longValue();}catch(ReflectiveOperationException ignored){return 0;}}
    static String summary(String id,String text){
        double factor=factor(id,false);if(factor==1)return text;
        var matcher=java.util.regex.Pattern.compile("伤害\\s+([0-9]+(?:\\.[0-9]+)?)").matcher(text);
        if(!matcher.find())return text;
        return text.substring(0,matcher.start(1))+EquipmentStatistics.format(Double.parseDouble(matcher.group(1))*factor)+text.substring(matcher.end(1));
    }
    private BalanceDisplay(){}
}
