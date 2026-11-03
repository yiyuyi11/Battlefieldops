package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline tests for the vehicle HUD rules.
 *
 * <p>The friend/foe decision is the one that matters most: getting it wrong makes teammates shoot
 * their own armour, or walk past an enemy tank because it was coloured kind.
 */
public final class VehicleHudSim {
    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        relationTests();
        visibilityTests();
        availabilityTests();
        describeTests();
        occupancyTests();

        System.out.println();
        if (failures.isEmpty()) {
            System.out.println("ALL VEHICLE HUD TESTS PASSED");
            System.out.println("passed: " + passed);
        } else {
            System.out.println("FAILURES: " + failures.size());
            for (String failure : failures) System.out.println("  FAIL " + failure);
            System.exit(1);
        }
    }

    private static VehicleHud.Blip blip(int team, int occupants) {
        return new VehicleHud.Blip(team, 0, 0, occupants, false);
    }

    private static void relationTests() {
        check("own team vehicle is friendly", VehicleHud.friendly(blip(0, 0), 0));
        check("other team vehicle is not friendly", !VehicleHud.friendly(blip(1, 0), 0));
        check("red viewer sees red as friendly", VehicleHud.friendly(blip(1, 0), 1));

        // A spectator has no side, so nothing can be "friendly" to them. Colouring by someone
        // else's team would be a lie rather than a convenience.
        check("spectator sees no friendlies", !VehicleHud.friendly(blip(0, 0), -1));
        check("spectator sees no friendlies (own)", !VehicleHud.friendly(blip(0, 0), -1));

        // Unknown ownership must not read as friendly, otherwise an unclaimed enemy hull would be
        // drawn in the safe colour and ignored.
        check("unknown owner is not friendly", !VehicleHud.friendly(blip(-1, 0), 0));
        check("unknown owner not friendly for red", !VehicleHud.friendly(blip(-1, 0), 1));

        // Ownership is by spawn, not by crew: an empty friendly hull stays friendly.
        check("empty friendly hull stays friendly", VehicleHud.friendly(blip(0, 0), 0));
    }

    private static void visibilityTests() {
        check("live vehicle is visible", VehicleHud.visible(blip(0, 0)));
        check("destroyed vehicle is hidden",
                !VehicleHud.visible(new VehicleHud.Blip(0, 0, 0, 0, true)));

        List<VehicleHud.Blip> all = List.of(
                blip(0, 2), blip(1, 1), new VehicleHud.Blip(1, 0, 0, 0, true));
        List<VehicleHud.Blip> seen = VehicleHud.visibleFor(all, 0);
        check("destroyed hulls filtered out", seen.size() == 2);
        // Enemy vehicles are shown on purpose: they are loud, large and already visible in the world.
        check("enemy vehicles are included", seen.stream().anyMatch(b -> b.ownerTeam() == 1));
        check("friendly vehicles are included", seen.stream().anyMatch(b -> b.ownerTeam() == 0));
        check("empty list stays empty", VehicleHud.visibleFor(List.of(), 0).isEmpty());
    }

    private static void availabilityTests() {
        // The happy path must actually report READY, or the button never enables.
        check("all conditions met is deployable",
                VehicleHud.availability(true, true, true, 0, 0, 6) == VehicleHud.State.READY);
        check("READY maps to deployable",
                VehicleHud.deployable(VehicleHud.State.READY));

        check("cooldown blocks",
                VehicleHud.availability(true, true, true, 20, 0, 6)
                        == VehicleHud.State.COOLDOWN);
        check("match cap blocks",
                VehicleHud.availability(true, true, true, 0, 6, 6)
                        == VehicleHud.State.MATCH_LIMIT);
        check("unauthorised vehicle blocks",
                VehicleHud.availability(false, true, true, 0, 0, 6)
                        == VehicleHud.State.NOT_ALLOWED);
        check("wrong team blocks",
                VehicleHud.availability(true, false, true, 0, 0, 6)
                        == VehicleHud.State.WRONG_TEAM);
        check("no launch site blocks",
                VehicleHud.availability(true, true, false, 0, 0, 6)
                        == VehicleHud.State.NO_SITE);

        // Personal score is deliberately not an input any more: a player with nothing recorded must
        // still be able to take a vehicle once the cooldown is up. If credits ever creep back into the
        // signature this test is the one that should fail.
        check("score does not gate a vehicle",
                VehicleHud.availability(true, true, true, 0, 0, 6) == VehicleHud.State.READY);

        // Precedence: the reason reported must be the one the player can act on first. Cooldown is
        // personal and fixable by waiting; the match cap is not.
        check("cooldown reported before match cap",
                VehicleHud.availability(true, true, true, 40, 6, 6)
                        == VehicleHud.State.COOLDOWN);
        check("authorisation reported before cooldown",
                VehicleHud.availability(false, true, true, 40, 6, 6)
                        == VehicleHud.State.NOT_ALLOWED);
        check("missing site reported before cooldown",
                VehicleHud.availability(true, true, false, 40, 0, 6)
                        == VehicleHud.State.NO_SITE);

        // Below the cap is fine; the boundary is exclusive.
        check("one below cap is fine",
                VehicleHud.availability(true, true, true, 0, 5, 6) == VehicleHud.State.READY);

        check("only READY is deployable", !VehicleHud.deployable(VehicleHud.State.COOLDOWN));
        check("blocked states are not deployable",
                !VehicleHud.deployable(VehicleHud.State.MATCH_LIMIT));
        check("unauthorised is not deployable",
                !VehicleHud.deployable(VehicleHud.State.NOT_ALLOWED));
    }

    private static void describeTests() {
        // Every state needs text; a blank label would leave the player with a dead button and no
        // explanation, which is the exact failure this HUD exists to prevent.
        for (VehicleHud.State state : VehicleHud.State.values()) {
            String text = VehicleHud.describe(state, 42, 6, 4);
            check("state " + state + " has text", text != null && !text.isBlank());
        }
        check("ready says deployable",
                VehicleHud.describe(VehicleHud.State.READY, 0, 6, 2).contains("可部署"));
        check("cooldown text shows seconds",
                VehicleHud.describe(VehicleHud.State.COOLDOWN, 42, 6, 2).contains("42"));
        check("cap text shows the limit",
                VehicleHud.describe(VehicleHud.State.MATCH_LIMIT, 0, 6, 2).contains("2"));
        // No state may mention a score price, because vehicles no longer cost any.
        for (VehicleHud.State state : VehicleHud.State.values()) {
            String text = VehicleHud.describe(state, 42, 6, 4);
            check("state " + state + " mentions no price",
                    !text.contains("分不足") && !text.contains("费用"));
        }
    }

    private static void occupancyTests() {
        check("parked is empty", VehicleHud.occupancy(blip(0, 0)).equals("空车"));
        check("crewed is occupied", VehicleHud.occupancy(blip(0, 3)).equals("占用"));

        // Sharing only makes sense for an empty friendly hull.
        check("empty friendly is shareable", VehicleHud.shareable(blip(0, 0), 0));
        check("occupied friendly is not shareable", !VehicleHud.shareable(blip(0, 2), 0));
        check("enemy is never shareable", !VehicleHud.shareable(blip(1, 0), 0));
        check("spectator gets nothing shared", !VehicleHud.shareable(blip(0, 0), -1));
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failures.add(name);
        }
    }
}
