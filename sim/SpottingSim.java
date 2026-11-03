package cn.bfops;

import java.util.List;

/**
 * Spotting rules: team exposure, mark ageing and the crosshair cone.
 *
 * <p>These decide what a tactical map is allowed to reveal, so they are the part that must not be wrong: a
 * mark that never expires is a permanent wall-hack, and an exposure test that only checks the viewing player
 * would make the map a personal radar instead of a squad one.
 */
public final class SpottingSim {
    private static int checks;

    public static void main(String[] args) {
        ownEyesOnly();
        marks();
        cone();
        System.out.println("SpottingSim: " + checks + " checks passed");
    }

    /**
     * A contact is visible to the player who can see it, and to nobody else automatically.
     *
     * <p>The rule this pins: the engine's own per-viewer test is the only automatic source. A teammate
     * standing next to the same enemy must not silently put that enemy on your map — that is what the mark
     * key is for.
     */
    private static void ownEyesOnly() {
        Tactics.At viewer = new Tactics.At(0, 64, 0);
        Tactics.At enemy = new Tactics.At(0, 64, 10);

        check(Tactics.enemyVisible(viewer, enemy, true), "in range with line of sight is visible");
        check(!Tactics.enemyVisible(viewer, enemy, false), "blocked line of sight is not visible");
        check(!Tactics.enemyVisible(viewer, new Tactics.At(0, 64, 10_000), true),
                "beyond range is not visible even with a clear line");
        check(!Tactics.enemyVisible(viewer, viewer, true), "a viewer never sees their own position as a contact");

        // Being seen by someone else is not a fact about this viewer: the predicate takes one viewer only, so
        // there is no way for another player's line of sight to leak into it.
        Tactics.At teammate = new Tactics.At(5, 64, 5);
        check(!Tactics.enemyVisible(teammate, enemy, false),
                "a teammate without line of sight does not expose the target for anyone");
        check(Tactics.enemyVisible(teammate, enemy, true),
                "and a teammate's own clear line only affects the teammate's own test");

        // The range boundary is exact, so "just outside" is never visible.
        double range = Tactics.ENEMY_GLOW_RANGE;
        check(Tactics.enemyVisible(viewer, new Tactics.At(0, 64, range), true),
                "exactly at range is visible");
        check(!Tactics.enemyVisible(viewer, new Tactics.At(0, 64, range + 0.01), true),
                "just past range is not");
    }

    /** Marks expire, are per-team, and cannot exceed the cap. */
    private static void marks() {
        long now = 1_000_000L;
        Spotting.Mark live = new Spotting.Mark(0, 10, 20, now + 5_000);
        Spotting.Mark expired = new Spotting.Mark(0, 30, 40, now - 1);
        Spotting.Mark neutral = new Spotting.Mark(-1, 50, 60, now + 5_000);
        Spotting.Mark otherTeam = new Spotting.Mark(1, 70, 80, now + 5_000);

        check(live.active(now), "a fresh mark is active");
        check(!expired.active(now), "an expired mark is not active");
        check(live.visibleTo(0), "a mark is visible to its own team");
        check(!live.visibleTo(1), "a mark is hidden from the other team");
        check(neutral.visibleTo(0) && neutral.visibleTo(1), "a neutral mark is visible to both");

        List<Spotting.Mark> pruned = Spotting.prune(List.of(live, expired, neutral), now);
        check(pruned.size() == 2, "pruning drops exactly the expired mark");
        check(pruned.stream().noneMatch(m -> m.x() == 30), "the expired mark is the one removed");
        check(Spotting.prune(List.of(), now).isEmpty(), "pruning nothing yields nothing");
        check(Spotting.prune(null, now).isEmpty(), "pruning null yields nothing");
        check(Spotting.prune(List.of(expired), now).isEmpty(), "pruning everything yields nothing");

        // Only this team's marks are sent to a viewer.
        List<Spotting.Mark> forTeamZero = Spotting.visibleTo(List.of(live, expired, neutral, otherTeam), 0, now);
        check(forTeamZero.size() == 2, "only own-team and neutral live marks are sent");
        check(forTeamZero.stream().noneMatch(m -> m.team() == 1), "the other team's mark is not sent");
        check(Spotting.visibleTo(List.of(live), 1, now).isEmpty(),
                "a team sees nothing from the other side");

        // The cap keeps the newest marks, which are the ones somebody just called out.
        java.util.List<Spotting.Mark> many = new java.util.ArrayList<>();
        for (int i = 0; i < Spotting.MAX_MARKS + 5; i++) {
            many.add(new Spotting.Mark(0, i, i, now + 5_000));
        }
        List<Spotting.Mark> capped = Spotting.prune(many, now);
        check(capped.size() == Spotting.MAX_MARKS, "the cap is enforced");
        check(capped.getLast().x() == Spotting.MAX_MARKS + 4, "the newest mark survives the cap");
        check(capped.getFirst().x() == 5, "the oldest marks are the ones dropped");
    }

    /** The crosshair cone decides whether a mark gesture hit the enemy being aimed at. */
    private static void cone() {
        double half = Math.toRadians(Spotting.MARK_CONE_DEGREES);

        // Yaw 0 looks towards +Z.
        check(Spotting.inCone(0, half, 0, 10), "straight ahead along +Z is inside the cone");
        check(!Spotting.inCone(0, half, 0, -10), "directly behind is outside the cone");
        check(!Spotting.inCone(0, half, 10, 0), "directly to the side is outside a narrow cone");

        // Turning right (increasing yaw) faces towards -X.
        double right = Math.toRadians(90);
        check(Spotting.inCone(right, half, -10, 0), "yaw 90 looks along -X");
        check(!Spotting.inCone(right, half, 10, 0), "and not along +X");

        // The cone has width: a target just inside the half-angle counts, just outside does not.
        double edge = Tactics.ENEMY_GLOW_RANGE;
        double justInside = Math.toRadians(Spotting.MARK_CONE_DEGREES - 0.5);
        double justOutside = Math.toRadians(Spotting.MARK_CONE_DEGREES + 0.5);
        check(Spotting.inCone(0, half, Math.sin(justInside) * edge, Math.cos(justInside) * edge),
                "a target just inside the cone is markable");
        check(!Spotting.inCone(0, half, Math.sin(justOutside) * edge, Math.cos(justOutside) * edge),
                "a target just outside the cone is not markable");

        // Zero offset is always inside, so a mark gesture on yourself never fails on geometry.
        check(Spotting.inCone(0, half, 0, 0), "zero offset is inside");
        check(Spotting.inCone(1.234, half, 0, 0), "zero offset is inside at any heading");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL  " + message);
            throw new AssertionError(message);
        }
    }
}
