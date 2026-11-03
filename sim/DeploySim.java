package cn.bfops;

import java.util.List;

/**
 * Offline checks for deploy-site selection and vehicle launching.
 *
 * <p>These cover the rules added for "vehicles spawn together with the player near a deploy point,
 * and aircraft get their own point":
 *
 * <ul>
 *   <li>a spawn point appears in the list only for its own team and its own breakthrough stage;</li>
 *   <li>a capture point becomes available exactly when the team owns it, and never launches aircraft;</li>
 *   <li>a ground vehicle can only launch from a point that allows {@code G};</li>
 *   <li>an aircraft can only launch from a point that allows {@code A};</li>
 *   <li>the fallback site keeps the vehicle class when one is available, so a suppressed spawn does
 *       not silently turn an aircraft request into an infantry deployment.</li>
 * </ul>
 */
public final class DeploySim {
    private static int checks;

    public static void main(String[] args) {
        spawnsBelongToTheirTeamAndStage();
        heldPointsAreLaunchSites();
        aircraftNeedAnAirPoint();
        fallbackKeepsTheClass();
        masksValidate();
        nonVehiclesAreFiltered();
        System.out.println("DeploySim: " + checks + " checks passed");
    }

    private static Model.Arena map() {
        Model.Arena map = new Model.Arena();
        map.mode = "CONQUEST";
        map.boundary.addAll(List.of(
                new Model.Vec(0, 0), new Model.Vec(400, 0),
                new Model.Vec(400, 400), new Model.Vec(0, 400)));

        Model.Spawn blueBase = new Model.Spawn();
        blueBase.id = "蓝基地";
        blueBase.team = 0;
        blueBase.x = 20;
        blueBase.z = 20;
        blueBase.allows = "G";
        map.spawns.add(blueBase);

        Model.Spawn blueAirfield = new Model.Spawn();
        blueAirfield.id = "蓝机场";
        blueAirfield.team = 0;
        blueAirfield.x = 40;
        blueAirfield.z = 20;
        blueAirfield.allows = "A";
        map.spawns.add(blueAirfield);

        Model.Spawn blueInfantry = new Model.Spawn();
        blueInfantry.id = "蓝步兵点";
        blueInfantry.team = 0;
        blueInfantry.x = 60;
        blueInfantry.z = 20;
        blueInfantry.allows = "N";
        map.spawns.add(blueInfantry);

        Model.Spawn redBase = new Model.Spawn();
        redBase.id = "红基地";
        redBase.team = 1;
        redBase.x = 380;
        redBase.z = 380;
        redBase.allows = "G";
        map.spawns.add(redBase);

        for (int i = 0; i < 3; i++) {
            Model.Point point = new Model.Point();
            point.id = String.valueOf((char) ('A' + i));
            point.x = 100 + i * 80;
            point.z = 200;
            point.radius = 20;
            map.points.add(point);
        }
        return map;
    }

    private static void spawnsBelongToTheirTeamAndStage() {
        Model.Arena map = map();
        // No points held yet: only spawn points are offered.
        List<Deploy.Site> blue = Deploy.sites(map, 0, 0, false, new int[]{-1, -1, -1});
        require(ids(blue).equals("蓝基地,蓝机场,蓝步兵点"), "blue spawn points: " + ids(blue));
        List<Deploy.Site> red = Deploy.sites(map, 1, 0, false, new int[]{-1, -1, -1});
        require(ids(red).equals("红基地"), "red spawn points: " + ids(red));

        // Breakthrough filters by stage; CONQUEST ignores it.
        map.mode = "BREAKTHROUGH";
        map.spawns.get(0).sector = 0;
        map.spawns.get(1).sector = 1;
        map.spawns.get(2).sector = 0;
        require(ids(Deploy.sites(map, 0, 0, true, null)).equals("蓝基地,蓝步兵点"),
                "stage 0 spawns: " + ids(Deploy.sites(map, 0, 0, true, null)));
        require(ids(Deploy.sites(map, 0, 1, true, null)).equals("蓝机场"),
                "stage 1 spawns: " + ids(Deploy.sites(map, 0, 1, true, null)));
        // An invalid team never yields sites, so a stray request cannot be interpreted as team 0.
        require(Deploy.sites(map, -1, 0, false, null).isEmpty(), "team -1 has no sites");
        require(Deploy.sites(map, 2, 0, false, null).isEmpty(), "team 2 has no sites");
        checks++;
    }

    private static void heldPointsAreLaunchSites() {
        Model.Arena map = map();
        // Blue owns points A and C.
        int[] owners = {0, -1, 0};
        List<Deploy.Site> blue = Deploy.sites(map, 0, 0, false, owners);
        require(ids(blue).equals("蓝基地,蓝机场,蓝步兵点,据点 A,据点 C"), "held points: " + ids(blue));

        // A point owned by the other side is never offered.
        List<Deploy.Site> red = Deploy.sites(map, 1, 0, false, owners);
        require(!ids(red).contains("据点 A"), "red must not get blue's point");

        // Held capture points launch ground vehicles only: a flag is a staging area, not a runway.
        for (Deploy.Site site : blue) {
            if (site.spawn()) continue;
            require(site.allows('G'), "held point must allow ground: " + site.id());
            require(!site.allows('A'), "held point must not allow air: " + site.id());
        }
        // Neutral points are not sites.
        require(!ids(Deploy.sites(map, 0, 0, false, new int[]{-1, -1, -1})).contains("据点"),
                "neutral points must not appear");
        checks++;
    }

    private static void aircraftNeedAnAirPoint() {
        Model.Arena map = map();
        List<Deploy.Site> sites = Deploy.sites(map, 0, 0, false, new int[]{0, -1, 0});

        List<Deploy.Site> air = Deploy.sitesFor(sites, 'A');
        require(ids(air).equals("蓝机场"), "only the airfield launches aircraft: " + ids(air));

        List<Deploy.Site> ground = Deploy.sitesFor(sites, 'G');
        require(ids(ground).equals("蓝基地,据点 A,据点 C"),
                "ground sites: " + ids(ground));
        // The infantry-only point launches nothing.
        require(!ids(ground).contains("蓝步兵点"), "infantry point must not launch vehicles");

        // A map with no air point at all: aircraft are unlaunchable, and that is visible as an empty
        // list rather than as a silent failure at deploy time.
        Model.Arena noAir = map();
        noAir.spawns.removeIf(s -> s.allows('A'));
        require(Deploy.sitesFor(Deploy.sites(noAir, 0, 0, false, null), 'A').isEmpty(),
                "no air point means no air sites");
        checks++;
    }

    private static void fallbackKeepsTheClass() {
        Model.Arena map = map();
        List<Deploy.Site> sites = Deploy.sites(map, 0, 0, false, null);

        // Requesting an aircraft when the nearest air point is gone must still pick a site where an
        // aircraft fits, not the first site in the list (which is a ground spawn).
        Deploy.Site chosen = Deploy.fallback(sites, 'A', true);
        require(chosen != null && chosen.allows('A'), "fallback for air must allow air: " + chosen);
        require(chosen.id().equals("蓝机场"), "fallback for air is the airfield: " + chosen.id());

        // With no aircraft in the request, the ordinary first site is fine.
        Deploy.Site plain = Deploy.fallback(sites, 'G', false);
        require(plain != null && plain.id().equals("蓝基地"), "plain fallback is the first site");

        // Asking for ground when only ground exists resolves to a ground site.
        List<Deploy.Site> groundOnly = Deploy.sitesFor(sites, 'G');
        Deploy.Site ground = Deploy.fallback(groundOnly, 'G', true);
        require(ground != null && ground.allows('G'), "ground fallback allows ground");

        require(Deploy.fallback(List.of(), 'A', true) == null, "empty list has no fallback");
        checks++;
    }

    private static void masksValidate() {
        require(Model.validAllows("G"), "G is valid");
        require(Model.validAllows("A"), "A is valid");
        require(Model.validAllows("GA"), "GA is valid");
        require(Model.validAllows("AG"), "AG is valid");
        require(Model.validAllows("N"), "N is valid");
        require(!Model.validAllows(""), "empty is invalid");
        // null must stay valid: maps saved before this field existed deserialise to null, and
        // rejecting them would make every pre-existing map unloadable after an update.
        require(Model.validAllows(null), "null is valid for backwards compatibility");
        require(!Model.validAllows("X"), "X is invalid");
        require(!Model.validAllows("GG"), "duplicate is invalid");
        require(!Model.validAllows("GN"), "N cannot be combined");
        require(!Model.validAllows("GAN"), "N cannot be combined with anything");
        require(!Model.validAllows("GAA"), "duplicate A is invalid");

        // The mask helpers agree with the raw string.
        Model.Spawn spawn = new Model.Spawn();
        spawn.allows = "GA";
        require(spawn.allows('G') && spawn.allows('A'), "GA allows both");
        require(!spawn.infantryOnly(), "GA is not infantry-only");
        spawn.allows = "N";
        require(!spawn.allows('G') && !spawn.allows('A'), "N allows nothing");
        require(spawn.infantryOnly(), "N is infantry-only");
        // A pre-existing map has no allows field; it must behave as a ground spawn, not as infantry.
        spawn.allows = "";
        require(spawn.allows('G'), "empty mask defaults to ground");
        spawn.allows = null;
        require(spawn.allows('G') && !spawn.allows('A'), "null mask defaults to ground only");
        checks++;
    }

    /**
     * Superb Warfare models its vehicle assembling table as a {@code VehicleEntity} declaring
     * {@code "Type": "Car"}, so a class-only scan offers a workbench as a fieldable tank.
     */
    private static void nonVehiclesAreFiltered() {
        Catalog catalog = new Catalog();
        catalog.vehicles.add("superbwarfare:t_90a");
        catalog.vehicles.add("superbwarfare:vehicle_assembling_table");
        require(catalog.deployableVehicle("superbwarfare:t_90a"), "a tank is deployable");
        require(!catalog.deployableVehicle("superbwarfare:vehicle_assembling_table"),
                "the assembling table is not a deployable vehicle");
        require(!catalog.deployableVehicle("superbwarfare:does_not_exist"),
                "an unknown id is not deployable");

        // Unknown classification defaults to ground, which is the safe direction: a tank launched from
        // a runway still works, an aircraft launched from a road does not.
        require(catalog.vehicleClass("superbwarfare:t_90a") == 'G', "unknown class defaults to ground");
        checks++;
    }

    private static String ids(List<Deploy.Site> sites) {
        StringBuilder sb = new StringBuilder();
        for (Deploy.Site site : sites) {
            if (!sb.isEmpty()) sb.append(',');
            sb.append(site.id());
        }
        return sb.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
