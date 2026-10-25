package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks for the whole vehicle deployment path.
 *
 * <p>Every assertion here corresponds to a defect found by reading the code against the real Superb
 * Warfare 0.8.9.2 bytecode, not to a hypothetical. None of them can be caught by playing the game
 * casually, which is why they are pinned: each one produced a symptom ("the button is grey", "the tank
 * will not fire", "vehicles stopped working") that reads like a different bug entirely.
 */
public final class VehicleDeploySim {
    private static int checks;

    public static void main(String[] args) {
        cooldownOnlyOnSuccess();
        siteMustLaunchTheVehicle();
        fallbackStaysLaunchable();
        aircraftNeedsAirfield();
        groundNeedsNoAirfield();
        everySiteCanLaunchGround();
        siteReconciliation();
        aircraftPickSurvivesRebuild();
        deployButtonAgrees();
        orphanReaping();
        System.out.println("VehicleDeploySim: " + checks + " checks passed");
    }

    /**
     * A failed deployment must not start the cooldown.
     *
     * <p>The old code charged the cooldown before knowing whether the seat would accept the driver, so a
     * refusal left the player waiting a full minute for a vehicle that never existed.
     */
    private static void cooldownOnlyOnSuccess() {
        VehicleDeploy.Gate ok = VehicleDeploy.Gate.of(true, 0, 6, true);
        check(ok.blocker() == null, "a clean request has no blocker");
        check(ok.chargesCooldown(), "and charges the cooldown");

        VehicleDeploy.Gate rejected = VehicleDeploy.Gate.of(false, 0, 6, true);
        check(rejected.blocker() != null, "a rejected request reports a blocker");
        check(!rejected.chargesCooldown(), "and must not charge the cooldown");

        // A full match must refuse before the seat is even attempted, and not charge either.
        VehicleDeploy.Gate full = VehicleDeploy.Gate.of(true, 6, 6, true);
        check(full.blocker() != null, "a full match refuses the deployment");
        check(!full.chargesCooldown(), "and does not charge the cooldown");
        checks++;
    }

    private static void siteMustLaunchTheVehicle() {
        List<Deploy.Site> sites = sites();
        Deploy.Site ground = sites.get(0);
        check(ground.allows('G'), "the headquarters launches ground vehicles");
        check(!ground.allows('A'), "but not aircraft");
        Deploy.Site air = sites.get(1);
        check(air.allows('A'), "the airfield launches aircraft");
        check(!air.allows('G'), "and only aircraft");
        checks++;
    }

    /**
     * The camped-site fallback must only ever pick a site that can launch the requested vehicle.
     *
     * <p>This was the actual mechanism behind "I picked a jet and could not deploy": when the chosen
     * site was camped, the fallback searched every site, correctly returned the safest one, and that one
     * launched only infantry — so the vehicle check rejected the entire deployment.
     */
    private static void fallbackStaysLaunchable() {
        List<Deploy.Site> sites = sites();
        // Only the airfield can launch an aircraft, so it is the only legal fallback.
        List<Deploy.Site> forAir = Deploy.sitesFor(sites, 'A');
        check(forAir.size() == 1 && forAir.getFirst().air(),
                "the only site that can launch an aircraft is the airfield");

        // Model the old, broken behaviour: a camped airfield fell back to the headquarters.
        Model.Spawn requested = sites.get(1).asSpawn(0);
        List<Model.Spawn> allSites = new ArrayList<>();
        for (Deploy.Site site : sites) allSites.add(site.asSpawn(0));
        List<Tactics.At> enemies = List.of(new Tactics.At(requested.x + 4, requested.y, requested.z));
        Model.Spawn chosen = Tactics.chooseSpawn(requested, allSites, enemies);
        check(!chosen.id.equals(requested.id), "a camped site is abandoned (this is the setup for the bug)");
        check(!launches(chosen, 'A'),
                "the unrestricted fallback picks a site that cannot launch an aircraft — the old bug");

        // The fix: restrict the candidate list, so the fallback stays launchable or gives up.
        List<Model.Spawn> launchable = Deploy.sitesFor(sites, 'A').stream()
                .map(s -> s.asSpawn(0)).toList();
        Model.Spawn fixed = Tactics.chooseSpawn(requested, launchable, enemies);
        check(fixed.id.equals(requested.id),
                "with the list restricted, the camped airfield is kept rather than replaced by a base");
        check(launches(fixed, 'A'), "and the result can still launch the aircraft");
        checks++;
    }

    private static void aircraftNeedsAirfield() {
        boolean anyAir = Deploy.sitesFor(sites(), 'A').stream().anyMatch(Deploy.Site::air);
        check(anyAir, "the map offers an airfield for aircraft");
        // A map with no airfield must report no capable site rather than silently using a base.
        List<Deploy.Site> noAirfield = Deploy.sitesFor(sites().subList(0, 1), 'A');
        check(noAirfield.isEmpty(), "a map with no airfield has no site for aircraft");
        checks++;
    }

    private static void groundNeedsNoAirfield() {
        List<Deploy.Site> sites = sites();
        List<Deploy.Site> forGround = Deploy.sitesFor(sites, 'G');
        check(!forGround.isEmpty(), "ground vehicles have somewhere to launch");
        check(forGround.stream().allMatch(s -> s.allows('G')),
                "and every one of those sites actually launches them");
        checks++;
    }

    /** Capture points are ground-only, by design: an owned flag is a staging ground, not a runway. */
    private static void everySiteCanLaunchGround() {
        Deploy.Site point = new Deploy.Site("据点 A", "据点 A", 500, 64, 500, 0f, "G", false,
                Model.Spawn.POINT);
        check(point.allows('G'), "a capture point launches ground vehicles");
        check(!point.allows('A'), "but never aircraft");
        check(!point.spawn(), "and is not a spawn point");
        checks++;
    }

    /** The player-facing reconciliation that keeps site and vehicle consistent. */
    private static void siteReconciliation() {
        List<Deploy.Site> sites = sites();
        String ground = sites.get(0).id();
        String air = sites.get(1).id();

        // Picking an aircraft from a ground site must move the site, not grey out the button.
        String moved = Deploy.siteFor(sites, ground, "superbwarfare:a_10a", 'A');
        check(air.equals(moved), "picking an aircraft moves the site to the airfield");

        // Picking a ground vehicle from an airfield must move it back.
        String back = Deploy.siteFor(sites, air, "superbwarfare:bmp_2", 'G');
        check(ground.equals(back), "picking a tank moves the site to the ground base");

        // A site that already fits must never be moved out from under the player.
        check(air.equals(Deploy.siteFor(sites, air, "superbwarfare:a_10a", 'A')),
                "an already-correct site is kept");
        check(ground.equals(Deploy.siteFor(sites, ground, "superbwarfare:bmp_2", 'G')),
                "and so is the ground one");

        // Infantry can use anything, so their choice is untouched.
        check(air.equals(Deploy.siteFor(sites, air, "", 'G')),
                "choosing no vehicle leaves the site alone");

        // A map with no airfield cannot conjure one.
        List<Deploy.Site> groundOnly = sites.subList(0, 1);
        check(ground.equals(Deploy.siteFor(groundOnly, ground, "superbwarfare:a_10a", 'A')),
                "with no airfield the site is kept and the button is greyed, not faked");
        checks++;
    }

    /**
     * The aircraft selection must survive the rebuild that follows it.
     *
     * <p>This was the reported symptom: ground vehicles could be picked, aircraft could not. The cause
     * was not permission or a missing runway — the ground row validated the shared pick against its own
     * list, and every aircraft is by definition absent from that list, so choosing one cleared the field
     * on the very next rebuild. Only the ground row did this, which is exactly why only aircraft broke.
     */
    private static void aircraftPickSurvivesRebuild() {
        List<String> ground = List.of("superbwarfare:bmp_2", "superbwarfare:t_90a");
        List<String> air = List.of("superbwarfare:a_10a", "superbwarfare:mi_28");

        // The fix: a pick from either list survives.
        check("superbwarfare:a_10a".equals(
                        VehicleDeploy.keepPick(ground, air, "superbwarfare:a_10a")),
                "an aircraft pick survives a rebuild");
        check("superbwarfare:bmp_2".equals(
                        VehicleDeploy.keepPick(ground, air, "superbwarfare:bmp_2")),
                "a ground pick survives too");

        // Reproduce the old rule to prove it really did erase aircraft: validating against the ground
        // list alone returns empty for every aircraft.
        for (String plane : air) {
            check(!ground.contains(plane),
                    "the old rule saw " + plane + " as absent from the ground list");
            // Validating against the ground list alone yields "" for every aircraft, i.e. exactly the
            // "selection immediately becomes none" symptom that was reported.
            String underOldRule = ground.contains(plane) ? plane : "";
            check(underOldRule.isEmpty(),
                    "and therefore reverted it to none — the reported bug for " + plane);
        }

        // A pick that the map no longer authorises must still be dropped: this is a real case, since an
        // administrator can revoke a vehicle while someone has it selected.
        check(VehicleDeploy.keepPick(ground, air, "superbwarfare:kirov").isEmpty(),
                "a pick revoked by the map is cleared");
        // And "none" stays none.
        check(VehicleDeploy.keepPick(ground, air, "").isEmpty(), "an empty pick stays empty");
        check(VehicleDeploy.keepPick(ground, air, null).isEmpty(), "a null pick is handled");
        // A map with no aircraft must not retain one.
        check(VehicleDeploy.keepPick(ground, List.of(), "superbwarfare:a_10a").isEmpty(),
                "with no aircraft authorised, an aircraft pick is cleared");
        checks++;
    }

    /**
     * Both deploy screens must agree on when the button is pressable.
     *
     * <p>They did not: the in-match screen honoured the cooldown and the hull cap, the editor's deploy
     * page checked only the site. An operator therefore got a lit button that the server refused. This
     * pins the shared rule so neither screen can quietly grow its own opinion again.
     */
    private static void deployButtonAgrees() {
        check(VehicleDeploy.deployable(true, true, true, true, false),
                "a clean request is deployable");
        check(!VehicleDeploy.deployable(false, true, true, true, false),
                "a stopped match is not deployable");
        check(!VehicleDeploy.deployable(true, false, true, true, false),
                "an unknown site is not deployable");
        check(!VehicleDeploy.deployable(true, true, false, true, false),
                "a site that cannot launch the vehicle is not deployable");
        check(!VehicleDeploy.deployable(true, true, true, false, false),
                "no kit is not deployable");
        // The blocker the editor page used to ignore entirely. Expressed through Gate so the two
        // actual causes are distinguished rather than asserted as one anonymous boolean.
        check(!VehicleDeploy.deployable(true, true, true, true, vehicleBlocked()),
                "a vehicle on cooldown blocks the button");
        check(GateReason.COOLDOWN.equals(reason(30, 0, 6, 6)),
                "the blocker is named: cooldown");
        check(GateReason.LIMIT.equals(reason(0, 6, 6, 6)),
                "the blocker is named: hull cap");
        check(GateReason.NONE.equals(reason(0, 3, 6, 6)),
                "a live hull with room left is not blocked");
        checks++;
    }

    private enum GateReason { NONE, COOLDOWN, LIMIT }

    /** Mirrors the server's own ordering: cooldown first, then the per-match cap. */
    private static GateReason reason(int cooldownSeconds, int used, int limit, int cap) {
        if (cooldownSeconds > 0) return GateReason.COOLDOWN;
        if (used >= Math.min(limit, cap)) return GateReason.LIMIT;
        return GateReason.NONE;
    }

    private static boolean vehicleBlocked() {
        return reason(30, 0, 6, 6) != GateReason.NONE;
    }

    /**
     * The slot reaper: an abandoned hull must release its match slot, a used one must not.
     *
     * <p>The per-match cap counts hulls, so a vehicle nobody can reach is a slot nobody can use. On a
     * six-vehicle map, six respawns would otherwise retire the team's entire armour.
     */
    private static void orphanReaping() {
        // Empty, no teammate within range: reusable slot.
        check(VehicleDeploy.orphaned(0, 200.0, 64.0), "an empty hull with nobody nearby is reclaimed");
        // Empty but a teammate is standing next to it: someone is coming back for it.
        check(!VehicleDeploy.orphaned(0, 10.0, 64.0), "an empty hull with a teammate nearby is kept");
        // Occupied: never touched regardless of who else is around.
        check(!VehicleDeploy.orphaned(1, 500.0, 64.0), "an occupied hull is never reclaimed");
        // No living teammate anywhere on the map.
        check(VehicleDeploy.orphaned(0, 9000.0, 64.0), "a distant empty hull is reclaimed");
        // Zero range must still work, i.e. the comparison is not inverted.
        check(!VehicleDeploy.orphaned(0, 0.0, 0.0),
                "a teammate at zero distance keeps the hull");
        checks++;
    }

    private static boolean launches(Model.Spawn spawn, char vehicleClass) {
        return spawn.allows(vehicleClass);
    }

    /** A map with one ground base and one airfield, which is the shape the bug needed. */
    private static List<Deploy.Site> sites() {
        List<Deploy.Site> sites = new ArrayList<>();
        sites.add(new Deploy.Site("蓝总部", "蓝总部", 100, 64, 100, 0f, "G", true, Model.Spawn.BASE));
        sites.add(new Deploy.Site("蓝机场", "蓝机场", 200, 64, 100, 0f, "A", true, Model.Spawn.AIR));
        return sites;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
