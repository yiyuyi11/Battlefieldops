package cn.bfops;
import java.util.*;
public final class FittingsSim {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        Model.Arena old=BattlefieldOps.GSON.fromJson("{\"vehicleLimit\":6}",Model.Arena.class);
        check(old.vehicleLimitFor(0)==6&&old.vehicleLimitFor(1)==6,"old map inherits both limits");
        old.blueVehicleLimit=0;old.redVehicleLimit=12;
        check(old.vehicleLimitFor(0)==0&&old.vehicleLimitFor(1)==12,"independent limits, zero disables");
        Model.Arena saved=BattlefieldOps.GSON.fromJson(BattlefieldOps.GSON.toJson(old),Model.Arena.class);
        check(saved.vehicleLimitFor(0)==0&&saved.vehicleLimitFor(1)==12,"limits persist");
        check(VehicleHud.availability(true,true,true,0,0,old.vehicleLimitFor(0))==VehicleHud.State.MATCH_LIMIT,"zero blocks deployment");
        check(VehicleHud.availability(true,true,true,0,0,old.vehicleLimitFor(1))==VehicleHud.State.READY,"other team remains available");
        var groups=List.of(new Fittings.Group("SCOPE","瞄具",List.of(new Fittings.Option("test:optic","optic","attachment","test:optic"))));
        Fittings.validate(groups,Map.of("SCOPE","test:optic"));
        boolean rejected=false;try{Fittings.validate(groups,Map.of("SCOPE","test:forged"));}catch(IllegalArgumentException ex){rejected=true;}
        check(rejected,"server rejects forged attachment");
        rejected=false;try{Fittings.validate(groups,Map.of("fake_station","0"));}catch(IllegalArgumentException ex){rejected=true;}
        check(rejected,"server rejects foreign weapon station");
        System.out.println("FittingsSim OK: independent quotas, persistence, incompatible equipment rejected");
    }
}
