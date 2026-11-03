package cn.bfops;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class NativeEquipmentStatsSim {
    static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static ClassNode entry(ZipFile zip,String name)throws Exception{ClassNode result=new ClassNode();new ClassReader(zip.getInputStream(zip.getEntry(name+".class")).readAllBytes()).accept(result,0);return result;}
    static void method(ClassNode type,String name,String desc){check(type.methods.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(desc)),type.name+" actual API: "+name);}
    public static void main(String[] args)throws Exception{
        check(EquipmentStatistics.format(35).equals("35"),"integer display does not leave decimal dot");check(EquipmentStatistics.format(.375).equals("0.375"),"ADS precision retained");check(EquipmentStatistics.format(0).equals("0"),"zero displays as zero");
        check(EquipmentStatistics.plain("0.3s §a(-0.1)").equals("0.3s (-0.1)"),"native style codes stripped without losing values");
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-2,2})check(EquipmentStatistics.ratio(invalid)>=0&&EquipmentStatistics.ratio(invalid)<=1,"finite clipped diagram geometry");
        var quicker=new EquipmentStatistics.Row("ads","ADS",".4s",".3s",.8,.6,-.1,false);check(quicker.improved(),"lower ADS is beneficial");
        check(!new EquipmentStatistics.Row("recoil","Recoil","1","2",.4,.8,1,false).improved(),"higher recoil is harmful");
        check(new EquipmentStatistics.Row("mag","Magazine","20","30",.4,.6,10,true).improved(),"larger magazine is beneficial");
        var tacz=Path.of("D:/USTBL/.minecraft/versions/Mechanomania-航空学/mods/tacz-neoforge-1.21.1-1.1.8-hotfix-r1.jar");
        try(var jar=new ZipFile(tacz.toFile())){
            method(entry(jar,"com/tacz/guns/resource/modifier/AttachmentCacheProperty"),"eval","(Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/resource/pojo/data/gun/GunData;)V");
            method(entry(jar,"com/tacz/guns/api/modifier/IAttachmentModifier"),"getPropertyDiagramsData","(Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/modifier/AttachmentCacheProperty;)Ljava/util/List;");
            var data=entry(jar,"com/tacz/guns/api/modifier/IAttachmentModifier$DiagramsData");
            for(String name:List.of("defaultPercent","modifierPercent"))method(data,name,"()D");
            for(String name:List.of("titleKey","positivelyString","negativeString","defaultString"))method(data,name,"()Ljava/lang/String;");
            method(data,"modifier","()Ljava/lang/Number;");method(data,"positivelyBetter","()Z");
            method(entry(jar,"com/tacz/guns/client/resource/index/ClientAttachmentIndex"),"getName","()Ljava/lang/String;");
        }
        try(var jar=new ZipFile("D:/USTBL/.minecraft/versions/Mechanomania-航空学/mods/superbwarfare-0.8.9.2-mc1.21.1-c22d98c2-A10-groun-recipes-fixed.jar")){
            method(entry(jar,"com/atsuishio/superbwarfare/data/vehicle/DefaultVehicleData"),"weapons","()Ljava/util/Map;");
            method(entry(jar,"com/atsuishio/superbwarfare/data/gun/GunData"),"from","(Lnet/minecraft/world/item/ItemStack;Lkotlin/jvm/functions/Function0;)Lcom/atsuishio/superbwarfare/data/gun/GunData;");
            method(entry(jar,"com/atsuishio/superbwarfare/data/gun/GunData"),"get","(Lcom/atsuishio/superbwarfare/data/gun/GunProp;)Ljava/lang/Object;");
        }
        System.out.println("NativeEquipmentStatsSim: "+checks+" numeric, graph and installed-mod API checks passed (not a live resource-pack test)");
    }
}
