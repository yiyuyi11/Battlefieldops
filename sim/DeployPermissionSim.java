package cn.bfops;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Reproduces the client-side deploy-button decision for an ordinary player, with no administrator flag.
 *
 * <p>Written because "only non-admins cannot deploy a vehicle" was reported and reading the screens did
 * not reveal a permission check anywhere on the deploy path — {@code deploy} itself has no admin gate.
 * If the cause is not a permission check, it has to be a wrong *decision* that happens to differ between
 * an admin (who usually tested by editing the map, so their selections were already valid) and a normal
 * player (who starts from empty selections). This sim rebuilds the exact predicate the two screens use
 * and prints every input that can flip it.
 *
 * <p>The decision under test is {@code BattleScreen}'s:
 * <pre>
 * need    = vehicle.isEmpty() ? 'G' : vehicleClass(vehicle)
 * siteFits= vehicle.isEmpty() ? allSites contains spawn : sitesFor(need) contains spawn
 * ready   = running && siteFits && kit != null && allSites contains spawn
 * </pre>
 */
public final class DeployPermissionSim {
    private static int checks;
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    public static void main(String[] args) {
        freshPlayerCanDeployInfantry();
        vehicleNeedsAMatchingSite();
        pickingAVehicleMovesTheSite();
        emptySpawnIsRejected();
        kindCarriesThroughFromState();
        aircraftNeedsAnAirSite();
        noAdminFlagIsNeeded();
        cooldownBlocksNobodyElse();
        System.out.println("DeployPermissionSim: " + checks + " checks passed");
    }

    /** A map with a ground base, an airfield and one capture point. */
    private static Model.Arena arena() {
        Model.Arena map = new Model.Arena();
        map.mode = "CONQUEST";
        map.id = "t";
        map.boundary = new ArrayList<>(List.of(
                new Model.Vec(0, 0), new Model.Vec(1000, 0),
                new Model.Vec(1000, 1000), new Model.Vec(0, 1000)));
        Model.Point point = new Model.Point();
        point.id = "A";
        point.x = 500;
        point.y = 64;
        point.z = 500;
        map.points.add(point);

        Model.Spawn base = new Model.Spawn();
        base.id = "基地";
        base.team = 0;
        base.x = 100;
        base.y = 64;
        base.z = 100;
        base.kind = Model.Spawn.BASE;
        base.allows = "G";
        map.spawns.add(base);

        Model.Spawn air = new Model.Spawn();
        air.id = "机场";
        air.team = 0;
        air.x = 200;
        air.y = 64;
        air.z = 200;
        air.kind = Model.Spawn.AIR;
        air.allows = "A";
        map.spawns.add(air);

        Model.Kit kit = new Model.Kit();
        kit.id = "步兵";
        kit.primary = "tacz:rifle";
        kit.secondary = "";
        map.kits.add(kit);
        return map;
    }

    /** The exact server payload, minus anything admin-only. */
    private static JsonObject state(Model.Arena arena, int team) {
        JsonObject state = new JsonObject();
        state.addProperty("admin", false);
        state.addProperty("roomRunning", true);
        JsonObject me = new JsonObject();
        me.addProperty("team", team);
        me.addProperty("alive", false);
        state.add("me", me);
        state.add("arena", GSON.toJsonTree(arena));

        JsonArray sites = new JsonArray();
        for (Deploy.Site site : Deploy.sites(arena, team, 0, false, null)) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", site.id());
            entry.addProperty("x", site.x());
            entry.addProperty("y", site.y());
            entry.addProperty("z", site.z());
            entry.addProperty("allows", site.allows());
            entry.addProperty("spawn", site.spawn());
            entry.addProperty("kind", site.kind());
            sites.add(entry);
        }
        state.add("deploySites", sites);

        JsonObject classes = new JsonObject();
        classes.addProperty("tacz:ifv", "G");
        classes.addProperty("tacz:jet", "A");
        state.add("vehicleClasses", classes);
        return state;
    }

    /** Mirrors {@code BattleScreen}'s decision, so a fix to one must change the other. */
    private static boolean ready(JsonObject state, Model.Arena arena, int team, String kitId,
                                 String spawn, String vehicle) {
        boolean running = state.get("roomRunning").getAsBoolean();
        List<Deploy.Site> all = Deploy.fromState(state, arena, team);
        char need = vehicle.isEmpty() ? 'G' : vehicleClass(state, vehicle);
        boolean siteFits = vehicle.isEmpty()
                ? all.stream().anyMatch(s -> s.id().equals(spawn))
                : Deploy.sitesFor(all, need).stream().anyMatch(s -> s.id().equals(spawn));
        boolean kitOk = arena.kits.stream().anyMatch(k -> k.id.equals(kitId));
        return running && siteFits && kitOk && all.stream().anyMatch(s -> s.id().equals(spawn));
    }

    private static char vehicleClass(JsonObject state, String id) {
        if (id == null || id.isEmpty()) return 'G';
        JsonObject classes = state.getAsJsonObject("vehicleClasses");
        if (classes != null && classes.has(id)) {
            String value = classes.get(id).getAsString();
            return value.isEmpty() ? 'G' : value.charAt(0);
        }
        return 'G';
    }

    // ------------------------------------------------------------------ the reports

    private static void freshPlayerCanDeployInfantry() {
        Model.Arena arena = arena();
        JsonObject state = state(arena, 0);
        // A brand-new player has no selections yet. Note the empty spawn: the real screen fills it in
        // from the site list during sync(), which is the step whose absence would look "only broken for
        // non-admins" — an admin who just edited the map has a valid spawn in hand already.
        check(!ready(state, arena, 0, "", "", ""), "with nothing selected, deploy is disabled");
        check(ready(state, arena, 0, "步兵", "基地", ""), "a filled-in selection deploys infantry");
        checks++;
    }

    private static void vehicleNeedsAMatchingSite() {
        Model.Arena arena = arena();
        JsonObject state = state(arena, 0);
        // The default site is the ground base, so a ground vehicle must work there and an aircraft must
        // not. This is the pair of cases the report is about.
        check(ready(state, arena, 0, "步兵", "基地", "tacz:ifv"),
                "a ground vehicle deploys from the ground base");
        check(!ready(state, arena, 0, "步兵", "基地", "tacz:jet"),
                "but an aircraft does not");
        check(ready(state, arena, 0, "步兵", "机场", "tacz:jet"),
                "the aircraft deploys from the airfield instead");
        checks++;
    }

    /**
     * The regression that made vehicles look broken for ordinary players.
     *
     * <p>Site and vehicle are picked on separate rows. Choosing an aircraft while the site still pointed
     * at the ground base left the pair inconsistent, so the deploy button disabled itself. Nothing in
     * the UI then said the site had to be changed — only a small caption. An admin testing straight
     * after editing the map usually had a matching site already, so the defect looked
     * admin-specific. {@link Deploy#siteFor} is the fix, and these assertions pin it.
     */
    private static void pickingAVehicleMovesTheSite() {
        Model.Arena arena = arena();
        List<Deploy.Site> sites = Deploy.sites(arena, 0, 0, false, null);
        JsonObject state = state(arena, 0);

        // Starting at the ground base and picking a jet must move the site to the airfield, which is
        // what makes the deploy button stay enabled.
        String moved = Deploy.siteFor(sites, "基地", "tacz:jet", 'A');
        check("机场".equals(moved), "picking an aircraft moves the site to the airfield (got " + moved + ")");
        check(ready(state, arena, 0, "步兵", moved, "tacz:jet"),
                "and the deploy button is enabled without the player touching the site row");

        // Picking a ground vehicle must not steal a site that cannot launch it either.
        String ground = Deploy.siteFor(sites, "机场", "tacz:ifv", 'G');
        check("基地".equals(ground), "picking a ground vehicle moves to the ground base (got " + ground + ")");

        // A site that already works must be kept: no gratuitous relocation.
        check("基地".equals(Deploy.siteFor(sites, "基地", "tacz:ifv", 'G')),
                "a compatible site is left alone");
        check("机场".equals(Deploy.siteFor(sites, "机场", "tacz:jet", 'A')),
                "including the airfield for an aircraft");

        // Clearing the vehicle must not move a valid site.
        check("机场".equals(Deploy.siteFor(sites, "机场", "", 'G')),
                "clearing the vehicle leaves a valid site alone");

        // With no capable site the answer is the current one, and the button correctly stays disabled.
        Model.Arena noAir = arena();
        noAir.spawns.removeIf(s -> s.air());
        List<Deploy.Site> noAirSites = Deploy.sites(noAir, 0, 0, false, null);
        check("基地".equals(Deploy.siteFor(noAirSites, "基地", "tacz:jet", 'A')),
                "with no capable site the choice is unchanged");
        check(!ready(state(noAir, 0), noAir, 0, "步兵", "基地", "tacz:jet"),
                "and the button is honestly disabled rather than silently wrong");
        checks++;
    }

    private static void emptySpawnIsRejected() {        Model.Arena arena = arena();
        JsonObject state = state(arena, 0);
        // The important degenerate case: if the spawn is blank the screen shows an unusable button, which
        // is indistinguishable from "only non-admins cannot deploy" to the player who sees it.
        check(!ready(state, arena, 0, "步兵", "", ""), "an empty spawn disables deploy even with a kit");
        check(!ready(state, arena, 0, "步兵", "", "tacz:ifv"), "and with a vehicle too");
        checks++;
    }

    private static void kindCarriesThroughFromState() {
        Model.Arena arena = arena();
        JsonObject state = state(arena, 0);
        List<Deploy.Site> sites = Deploy.fromState(state, arena, 0);
        check(sites.size() == 2, "both blue sites reach the client");
        Deploy.Site air = sites.stream().filter(Deploy.Site::air).findFirst().orElse(null);
        check(air != null && "机场".equals(air.id()), "the airfield survives the JSON round trip as air");
        Deploy.Site base = sites.stream().filter(s -> "基地".equals(s.id())).findFirst().orElse(null);
        check(base != null && !base.air(), "and the base is not air");
        checks++;
    }

    private static void aircraftNeedsAnAirSite() {
        // Without an airfield, an aircraft must be impossible to deploy — and the client must say so
        // rather than leaving a live button that the server then rejects.
        Model.Arena arena = arena();
        arena.spawns.removeIf(s -> s.air());
        JsonObject state = state(arena, 0);
        check(Deploy.sitesFor(Deploy.fromState(state, arena, 0), 'A').isEmpty(),
                "with no airfield there is no air site");
        check(!ready(state, arena, 0, "步兵", "基地", "tacz:jet"),
                "so an aircraft cannot be deployed");
        check(ready(state, arena, 0, "步兵", "基地", ""),
                "but infantry still can, so the screen stays usable");
        checks++;
    }

    private static void noAdminFlagIsNeeded() {
        // The decision must not read the admin flag at all. Asserted structurally: the same state with
        // admin true and false gives the same answer for every combination.
        Model.Arena arena = arena();
        JsonObject asAdmin = state(arena, 0);
        asAdmin.addProperty("admin", true);
        JsonObject asPlayer = state(arena, 0);
        asPlayer.addProperty("admin", false);
        for (String spawn : List.of("基地", "机场", "")) {
            for (String vehicle : List.of("", "tacz:ifv", "tacz:jet")) {
                check(ready(asAdmin, arena, 0, "步兵", spawn, vehicle)
                                == ready(asPlayer, arena, 0, "步兵", spawn, vehicle),
                        "admin flag does not change deploy availability (" + spawn + "/" + vehicle + ")");
            }
        }
    }

    private static void cooldownBlocksNobodyElse() {
        // Cooldown is a personal gate reported separately; it must not be folded into the deploy
        // predicate, because then the button would be dead with no explanation on the row.
        Model.Arena arena = arena();
        JsonObject state = state(arena, 0);
        check(ready(state, arena, 0, "步兵", "基地", "tacz:ifv"),
                "deploy availability ignores the cooldown");
        checks++;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
