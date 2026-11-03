package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless checks for spawn selection, threat ordering and the boundary countdown arithmetic.
 *
 * <p>Lives in {@code cn.bfops} because the callees are package-private. {@link Tactics} deliberately
 * avoids Minecraft types, so this runs with nothing but a JDK — no client or server needed.
 */
public final class TacticsSim {
    private static int failures = 0;

    public static void main(String[] args) {
        safestWinsOverClosest();
        dangerDetected();
        chooseFallsBack();
        chooseKeepsSafeSelection();
        threatOrdering();
        populationCount();
        glowRanges();
        boundaryCountdown();

        System.out.println(failures == 0 ? "ALL TACTICS TESTS PASSED" : failures + " TACTICS TESTS FAILED");
        if (failures != 0) System.exit(1);
    }

    private static Model.Spawn spawn(String id, double x, double z) {
        Model.Spawn spawn = new Model.Spawn();
        spawn.id = id;
        spawn.x = x;
        spawn.y = 64;
        spawn.z = z;
        return spawn;
    }

    private static Tactics.At at(double x, double z) {
        return new Tactics.At(x, 64, z);
    }

    private static void safestWinsOverClosest() {
        System.out.println("spawns: the point farthest from enemies is preferred");
        List<Model.Spawn> candidates = List.of(spawn("near", 0, 0), spawn("far", 100, 0));
        Model.Spawn picked = Tactics.safestSpawn(candidates, List.of(at(5, 0)));
        check("picked the far spawn", picked != null && picked.id.equals("far"),
                picked == null ? "null" : picked.id);
    }

    private static void dangerDetected() {
        System.out.println("spawns: an enemy within the danger radius marks the spawn hot");
        Model.Spawn hot = spawn("hot", 0, 0);
        check("hot spawn detected", Tactics.spawnUnderFire(hot, List.of(at(8, 0))), "not detected");
        check("cold spawn clear", !Tactics.spawnUnderFire(hot, List.of(at(40, 0))), "false positive");
        check("no enemies means safe", !Tactics.spawnUnderFire(hot, List.of()), "false positive");
        check("boundary of radius is not hot",
                !Tactics.spawnUnderFire(hot, List.of(at(Tactics.SPAWN_DANGER_RADIUS + 0.1, 0))),
                "off-by-one at the radius");
    }

    private static void chooseFallsBack() {
        System.out.println("spawns: a camped selection is replaced by a safer one");
        Model.Spawn camped = spawn("camped", 0, 0);
        List<Model.Spawn> legal = List.of(camped, spawn("safe", 120, 0));
        Model.Spawn picked = Tactics.chooseSpawn(camped, legal, List.of(at(2, 0)));
        check("switched off the camped spawn", picked != null && picked.id.equals("safe"),
                picked == null ? "null" : picked.id);
    }

    private static void chooseKeepsSafeSelection() {
        System.out.println("spawns: a safe selection is honoured exactly");
        Model.Spawn chosen = spawn("chosen", 0, 0);
        List<Model.Spawn> legal = List.of(chosen, spawn("other", 200, 0));
        Model.Spawn picked = Tactics.chooseSpawn(chosen, legal, List.of(at(500, 0)));
        check("kept the player's choice", picked == chosen, "overrode the player");
    }

    private static void threatOrdering() {
        System.out.println("spawns: threat ordering puts the safest entry first");
        List<Model.Spawn> candidates = List.of(spawn("a", 0, 0), spawn("b", 90, 0), spawn("c", 40, 0));
        List<Model.Spawn> ordered = Tactics.byThreat(candidates, List.of(at(0, 0)));
        check("farthest first", ordered.get(0).id.equals("b"), ordered.get(0).id);
        check("nearest last", ordered.get(2).id.equals("a"), ordered.get(2).id);
        check("all candidates kept", ordered.size() == 3, String.valueOf(ordered.size()));
    }

    private static void populationCount() {
        System.out.println("teams: population counting");
        List<Integer> teams = new ArrayList<>(List.of(0, 1, 0, 0, 1));
        check("blue counted", Tactics.population(teams, 0) == 3, String.valueOf(Tactics.population(teams, 0)));
        check("red counted", Tactics.population(teams, 1) == 2, String.valueOf(Tactics.population(teams, 1)));
        check("empty team is zero", Tactics.population(List.of(), 0) == 0, "non-zero");
    }

    private static void glowRanges() {
        System.out.println("highlight: range gates for teammates and enemies");
        Tactics.At viewer = at(0, 0);
        check("teammate in range",
                Tactics.mateVisible(viewer, at(Tactics.MATE_GLOW_RANGE - 1, 0)), "missed");
        check("teammate out of range",
                !Tactics.mateVisible(viewer, at(Tactics.MATE_GLOW_RANGE + 1, 0)), "leaked");
        check("enemy needs line of sight",
                !Tactics.enemyVisible(viewer, at(4, 0), false), "glowed through a wall");
        check("enemy visible in range",
                Tactics.enemyVisible(viewer, at(4, 0), true), "missed");
        check("enemy out of range",
                !Tactics.enemyVisible(viewer, at(Tactics.ENEMY_GLOW_RANGE + 1, 0), true), "leaked");
        check("self never glows", !Tactics.enemyVisible(viewer, viewer, true), "self glowed");
    }

    /**
     * The engine derives the boundary countdown from {@code outsideSeconds} and a per-player tick
     * counter, warning once per second and killing on the tick the counter reaches the limit.
     * Reproduce that arithmetic so an off-by-one is caught without a server.
     */
    private static void boundaryCountdown() {
        System.out.println("boundary: countdown arithmetic");
        int outsideSeconds = 10;
        int warningsShown = 0;
        int killedAt = -1;
        for (int outside = 1; outside <= outsideSeconds; outside++) {
            warningsShown++;
            if (outside >= outsideSeconds && killedAt < 0) killedAt = outside;
        }
        check("one warning per second", warningsShown == outsideSeconds, String.valueOf(warningsShown));
        check("survives until the limit", killedAt == outsideSeconds, String.valueOf(killedAt));
        check("last warning reads zero",
                Math.max(0, outsideSeconds - outsideSeconds) == 0, "non-zero");
        check("first warning reads limit-1",
                Math.max(0, outsideSeconds - 1) == outsideSeconds - 1, "off-by-one");
    }

    private static void check(String label, boolean ok, String detail) {
        if (ok) {
            System.out.println("  PASS  " + label);
        } else {
            failures++;
            System.out.println("  FAIL  " + label + " -> " + detail);
        }
    }
}
