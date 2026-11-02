package cn.bfops;

import java.util.*;

/** Map/class/weapon restrictions. Deny takes precedence over level and whitelist grants. */
final class InfantryAttachments {
    static final class Entry { boolean disabled; int unlockLevel; }
    static final class Policy {
        boolean whitelist;
        Map<String,Entry> entries=new LinkedHashMap<>();
        String blocker(String attachment,int level){
            Entry entry=entries.get(attachment);
            if(entry!=null&&entry.disabled)return "本图此兵种禁用该配件";
            if(whitelist&&entry==null)return "该配件未列入本兵种白名单";
            if(entry!=null&&level<entry.unlockLevel)return "需要等级 "+entry.unlockLevel+"（当前 "+level+"）";
            return "";
        }
        void validate(){
            Model.require(entries!=null&&entries.size()<=2048,"每把武器最多配置 2048 个配件规则");
            entries.forEach((id,entry)->{
                Model.require(validId(id)&&entry!=null,"配件规则无效");
                Model.range(entry.unlockLevel,0,100,"配件解锁等级");
            });
        }
    }
    static boolean validId(String id){return id!=null&&id.length()<160&&id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");}
    static void validate(Model.Kit kit){
        Model.require(kit.attachments!=null&&kit.attachments.size()<=128,"兵种配件规则过多");
        kit.attachments.forEach((gun,policy)->{
            Model.require(validId(gun)&&policy!=null,"武器配件配置无效");
            Model.require(kit.options(true).contains(gun)||kit.options(false).contains(gun),"配件规则对应武器已不在此兵种武器库："+gun);
            policy.validate();
        });
    }
    static String blocker(Model.Kit kit,String gun,String attachment,int level){
        Policy policy=kit.attachments==null?null:kit.attachments.get(gun);
        return policy==null?"":policy.blocker(attachment,level);
    }
    static void authorize(Model.Kit kit,String gun,Map<String,String> selected,int level,List<Fittings.Group> compatible){
        Fittings.validate(compatible,selected);
        for(String attachment:selected.values())if(!attachment.isEmpty()){
            String reason=blocker(kit,gun,attachment,level);
            Model.require(reason.isEmpty(),attachment+"："+reason);
        }
    }
}
