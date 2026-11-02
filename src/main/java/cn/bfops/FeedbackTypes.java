package cn.bfops;

/** Server/client vocabulary bridge; rewards are not implicitly kills. */
final class FeedbackTypes {
    private FeedbackTypes(){}
    static String cosmeticIcon(boolean headshot,String label){return "摧毁载具".equals(label)?"vehicle":headshot?"headshot":"kill";}
    static int killType(String icon){return switch(icon==null?"":icon){
        case "headshot"->1;case "explosion"->2;case "crit"->3;case "assist"->4;
        case "vehicle"->5;case "capture"->6;case "vehicle_assist"->7;case "medic"->8;case "mark"->9;default->0;};}
    static String bonusId(String label,String icon){
        String specific=switch(label==null?"":label){
            case "远距离击杀"->"KILL_LONG_DISTANCE";case "绝境反击"->"DESPERATE_COUNTERATTACK";
            case "盲杀"->"BLIND_KILL";case "背刺"->"BACKSTAB_KILL";case "轻松击杀"->"EFFORTLESS_KILL";
            case "天降正义"->"JUSTICE_FROM_ABOVE";case "绝对制空"->"ABSOLUTE_AIR_CONTROL";
            case "终结连杀"->"INTERRUPTED_STREAK";case "英勇回击"->"BRAVE_RETURN";
            case "嗜血"->"BLOODTHIRSTY";case "无情"->"MERCILESS";case "英勇"->"VALIANT";case "凶猛"->"FIERCE";case "残暴"->"SAVAGE";
            case "助攻"->"ASSIST";case "标记击杀"->"SPOTTING_KILL";case "连杀加成"->"KILL_COMBO";default->"";};
        if(!specific.isEmpty())return specific;
        if("救援".equals(label))return "REVIVE";
        if("地面侦测".equals(label))return "GROUND_SENSOR_SCAN";
        if(label!=null&&label.contains("信标"))return "SQUAD_BEACON_DEPLOY";
        if(label!=null&&label.contains("弹药"))return "AMMO_SUPPLY";
        if("爆头加成".equals(label))return "HEADSHOT";
        return switch(icon==null?"":icon){case "kill"->"KILL";case "headshot"->"KILL_HEADSHOT";
            case "explosion"->"KILL_EXPLOSION";case "crit"->"KILL_CRIT";case "assist"->"ASSIST";
            case "vehicle"->"DESTROY_VEHICLE";case "vehicle_assist"->"VEHICLE_DESTROY_ASSIST";
            case "capture"->"CONQUEST_CAPTURE_CONTROL";case "medic"->"HEALING";case "mark"->"SPOTTING";
            case "revenge"->"AVENGE";default->"";};
    }
}
