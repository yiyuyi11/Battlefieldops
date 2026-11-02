package cn.bfops;
/** Never expose transport identifiers as deployment feedback. */
final class DeploymentText {
    static String label(String id,String kind,String configured){
        String value=id==null?"":id;
        if(value.startsWith("beacon:")||"BEACON".equals(kind))return "小队信标";
        if(value.startsWith("mate:")||"TEAMMATE".equals(kind))return "小队成员";
        if(value.startsWith("vehicle:")||"VEHICLE".equals(kind))return "友军载具";
        return configured!=null&&!configured.isBlank()?configured:value.isBlank()?"部署点":value;
    }
}
