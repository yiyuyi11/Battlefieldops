package cn.bfops;
import net.minecraft.client.CameraType;
public final class PerspectiveVehicleSim {
    static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        var p=new PerspectivePolicy();
        check(p.update(false,false,CameraType.THIRD_PERSON_BACK)==CameraType.THIRD_PERSON_BACK,"outside match leaves perspective alone");
        check(p.update(true,false,CameraType.THIRD_PERSON_BACK)==CameraType.FIRST_PERSON,"infantry forced first person");
        check(p.update(true,false,CameraType.THIRD_PERSON_FRONT)==CameraType.FIRST_PERSON,"F5 cannot give infantry third person");
        check(p.update(true,true,CameraType.FIRST_PERSON)==CameraType.FIRST_PERSON,"boarding initially keeps current camera");
        check(p.update(true,true,CameraType.THIRD_PERSON_BACK)==CameraType.THIRD_PERSON_BACK,"vehicle rear third person allowed");
        check(p.update(true,true,CameraType.THIRD_PERSON_FRONT)==CameraType.THIRD_PERSON_FRONT,"vehicle front third person allowed");
        check(p.update(true,false,CameraType.THIRD_PERSON_FRONT)==CameraType.FIRST_PERSON,"dismount locks immediately");
        check(p.update(true,true,CameraType.FIRST_PERSON)==CameraType.THIRD_PERSON_FRONT,"reboarding restores chosen vehicle view");
        check(p.update(false,true,CameraType.THIRD_PERSON_FRONT)==CameraType.THIRD_PERSON_BACK,"leaving restores pre-match preference");
        check(p.update(false,false,CameraType.FIRST_PERSON)==CameraType.FIRST_PERSON,"completed session does not retain ownership");
        System.out.println("PerspectiveVehicleSim: "+checks+" transition checks passed");
    }
}
