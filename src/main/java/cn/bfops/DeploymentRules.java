package cn.bfops;

/** Separate infantry reinforcement, boarding an existing hull, and creating a new hull. */
final class DeploymentRules {
    static boolean fixedKind(String kind){return "BASE".equals(kind)||"AIR".equals(kind)||"POINT".equals(kind);}
    static String prefixKind(String id){
        if(id==null)return "";
        if(id.startsWith("mate:"))return "TEAMMATE";
        if(id.startsWith("beacon:"))return "BEACON";
        if(id.startsWith("vehicle:"))return "VEHICLE";
        return "";
    }
    static boolean canSpawnNew(Deploy.Site site){return site!=null&&fixedKind(site.kind())&&prefixKind(site.id()).isEmpty();}
    static String keepVehicle(Deploy.Site site,String vehicle){return canSpawnNew(site)?vehicle:"";}
    static String requestBlocker(Deploy.Site site,String vehicle,boolean existingHull){
        if(site==null)return "部署目标已失效";
        String prefix=prefixKind(site.id());
        if(SquadRules.dynamic(site.kind())){
            if(!site.kind().equals(prefix))return "部署目标类型无效，请刷新";
            if(vehicle!=null&&!vehicle.isEmpty())return "小队、信标和登车部署只允许步兵，不能生成新载具";
            if("VEHICLE".equals(site.kind())!=existingHull)return "现有载具目标已失效，请重新选择";
            return "";
        }
        if(!fixedKind(site.kind())||!prefix.isEmpty()||existingHull)return "部署目标类型无效，请刷新";
        return "";
    }
    private DeploymentRules(){}
}
