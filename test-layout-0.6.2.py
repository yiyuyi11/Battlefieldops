from pathlib import Path
p=Path('src/main/java/cn/bfops/Client.java');s=p.read_text('utf-8').replace('if (!VehicleUi.active()) CombatHud.draw', 'if (!(VehicleUi.active() && VehicleUi.scoped())) CombatHud.draw');p.write_text(s,'utf-8')
p=Path('src/main/java/cn/bfops/AwardHud.java');s=p.read_text('utf-8').replace('if (!vehicle) {','{',1);p.write_text(s,'utf-8')
Path('sim/AwardLayoutSim.java').write_text('''package cn.bfops;
import java.util.List;
public final class AwardLayoutSim {
    public static void main(String[] args) {
        int count=0,visible=0;
        for(int w=240;w<=1920;w+=40) for(int h=180;h<=1080;h+=30)
            for(boolean vehicle:new boolean[]{false,true}) for(int rows=1;rows<=3;rows++) {
                var minimap=new AwardLayout.Rect(8,40,116,136);
                var zones=List.of(minimap,new AwardLayout.Rect((w-260)/2,0,260,94));
                var result=AwardLayout.place(w,h,vehicle,rows,zones);
                count++;
                if(result==null)continue;
                visible++;
                check(result.fits(w,h),"card outside viewport");
                for(var zone:zones)check(!result.overlaps(zone),"overlaps match HUD");
                if(vehicle) for(var zone:AwardLayout.vehicleZones(w,h))check(!result.overlaps(zone),"overlaps reserved vehicle instrument/reticle area");
                check(result.h()==rows*34,"stack overlaps its own cards");
            }
        check(AwardLayout.place(320,240,true,2,List.of())==null,"small vehicle screen must defer instead of cover instruments");
        check(AwardLayout.place(960,540,true,2,List.of())!=null,"normal vehicle screen must show confirmations");
        check(AwardLayout.place(640,360,false,1,List.of())!=null,"standard infantry screen must show confirmations");
        System.out.println("AwardLayoutSim: "+count+" layouts checked, "+visible+" collision-free placements");
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
''','utf-8')
s=Path('install-0.6.1.ps1').read_text('utf-8-sig');s=s.replace('0.6.1','0.6.2');s=s.replace("'battlefield-ops-0.6.0-neoforge-1.21.1.jar',", "'battlefield-ops-0.6.0-neoforge-1.21.1.jar','battlefield-ops-0.6.1-neoforge-1.21.1.jar',");Path('install-0.6.2.ps1').write_text(s,'utf-8-sig')
