package cn.bfops;

import java.util.List;

/**
 * The decisions in the vehicle deployment path, extracted so they can be tested without a server.
 *
 * <p>Every method here is pure. The engine calls them, so the assertions in {@code VehicleDeploySim}
 * test the code that actually runs rather than a copy of it.
 */
final class VehicleDeploy {
    private VehicleDeploy() { }

    /**
     * The outcome of the checks that decide whether a deployment happens at all.
     *
     * @param blocker why it was refused, or {@code null} when it was not
     * @param success whether the vehicle actually appeared
     */
    record Gate(String blocker, boolean success) {
        /**
         * Builds the gate result from the conditions the engine actually checks.
         *
         * <p>Ordered the way a player meets them: whether the site can launch this hull, whether the
         * match still has a spare slot, then whether the seat accepted the driver. Being camped is not a
         * blocker — it relocates the site instead — so it deliberately does not appear here.
         *
         * @param spawnOk  the site that will be used can launch this vehicle class
         * @param reserved hulls already in the match
         * @param limit    hulls allowed in the match
         * @param seatOk   the vehicle was created and the driver was seated
         */
        static Gate of(boolean spawnOk, int reserved, int limit, boolean seatOk) {
            if (!spawnOk) return new Gate("部署点无法放飞该载具", false);
            if (reserved >= limit) return new Gate("本方载具名额已满", false);
            if (!seatOk) return new Gate("未能坐进载具", false);
            return new Gate(null, true);
        }

        /** Only a deployment that actually produced a vehicle may start the cooldown. */
        boolean chargesCooldown() {
            return success;
        }
    }

    /**
     * Whether an empty vehicle should be reclaimed to free its match slot.
     *
     * <p>Three conditions, all required: nobody is riding it, no living member of its own team is within
     * {@code range} of it, and it has an owner to compare against. Distinct from "is it destroyed" —
     * a destroyed hull releases its slot through the removal path instead.
     *
     * @param passengers how many entities are currently riding
     * @param nearestAlly flat distance to the closest living teammate, or a large number for none
     * @param range      how close a teammate must be to claim it
     */
    static boolean orphaned(int passengers, double nearestAlly, double range) {
        if (passengers > 0) return false;
        // "Strictly farther than the range" rather than "at or beyond": a teammate standing exactly at
        // the range boundary is still close enough to walk back to it.
        return nearestAlly > range;
    }

    /**
     * Whether the deploy button may be pressed.
     *
     * <p>Both screens used to compute this themselves, and they drifted: the in-match screen checked
     * the cooldown and the hull cap, the editor's deploy page checked only the site. The editor
     * therefore offered a button that the server refused — which reads as a broken control, not as a
     * real restriction. One function, called from both, is what keeps them honest.
     *
     * <p>This is deliberately narrower than the server's own check: the server has two blockers the
     * client cannot see (a site camped by the enemy, and the canonical site it will actually use), so
     * the client only removes the cases it can prove are impossible.
     *
     * @param running        the match is running
     * @param siteKnown      the selected site still exists for this player
     * @param siteFits       the selected site can launch the chosen vehicle (ignored with no vehicle)
     * @param kitKnown       a kit is selected
     * @param vehicleBlocked the server reports the hull as blocked (cooldown, or the per-match cap)
     */
    static boolean deployable(boolean running, boolean siteKnown, boolean siteFits,
                              boolean kitKnown, boolean vehicleBlocked) {
        return running && siteKnown && siteFits && kitKnown && !vehicleBlocked;
    }

    /**
     * Whether a player's current vehicle pick is still valid for this map.
     *
     * <p>Both rows — ground and air — share one vehicle field, so the validity test must cover the union
     * of both lists. The deploy screen originally tested the pick against the <em>ground</em> row's
     * options only, which meant choosing an aircraft set the field and the very next rebuild cleared it
     * straight back to "no vehicle". Aircraft therefore looked impossible to select while ground
     * vehicles worked perfectly, and nothing in the code looked like a permission problem because it
     * was not one: the pick was simply being erased one frame after it was made.
     *
     * @param ground  ids authorised as ground vehicles on this map
     * @param air     ids authorised as aircraft on this map
     * @param vehicle the current pick, empty for none
     * @return the pick to keep, or an empty string when it is no longer authorised
     */
    static String keepPick(List<String> ground, List<String> air, String vehicle) {
        if (vehicle == null || vehicle.isEmpty()) return "";
        if (ground != null && ground.contains(vehicle)) return vehicle;
        if (air != null && air.contains(vehicle)) return vehicle;
        return "";
    }

    /**
     * The canonical site for a vehicle, falling back to the first launchable one.
     *
     * <p>Wraps {@link Deploy#siteFor} so the engine has one entry point for the rule and the tests have
     * one thing to assert against.
     */
    static String siteFor(List<Deploy.Site> sites, String current, String vehicle, char vehicleClass) {
        return Deploy.siteFor(sites, current, vehicle, vehicleClass);
    }
}
