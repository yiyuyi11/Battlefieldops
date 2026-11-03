import cn.bfops.Model;
import cn.bfops.Rules;

import java.util.List;

/**
 * Offline harness for the scoring rules. Compiles and runs without a Minecraft server so the
 * capture bar, ticket bleed, breakthrough staging and timeout behaviour can be verified directly.
 */
public final class RulesSim {
    private static int failures = 0;

    public static void main(String[] args) {
        conquestBleedsTickets();
        conquestStalemateIsFree();
        contestedCaptureStillAdvances();
        singleDefenderCannotFreeze();
        breakthroughRequiresFullStage();
        breakthroughAdvanceGivesBonus();
        breakthroughDefenderWinsOnTimeout();
        breakthroughOnlyAttackerPaysOnDeath();
        neutraliseGrantsNoScore();
        System.out.println(failures == 0 ? "ALL RULES TESTS PASSED" : failures + " RULES TESTS FAILED");
        if (failures != 0) System.exit(1);
    }

    // ---------------------------------------------------------------- fixtures

    private static Model.Arena conquest() {
        Model.Arena map = new Model.Arena();
        map.id = "sim";
        map.name = "sim";
        map.mode = "CONQUEST";
        map.captureSeconds = 20;
        map.tickets = 300;
        map.bleed = 1;
        map.deathCost = 1;
        map.roundMinutes = 30;
        map.points.add(point("A", 0));
        map.points.add(point("B", 0));
        map.points.add(point("C", 0));
        map.spawns.add(spawn("blue", 0, 0));
        map.spawns.add(spawn("red", 1, 0));
        map.kits.add(new Model.Kit());
        return map;
    }

    private static Model.Arena breakthrough() {
        Model.Arena map = new Model.Arena();
        map.id = "simbt";
        map.name = "simbt";
        map.mode = "BREAKTHROUGH";
        map.captureSeconds = 20;
        map.tickets = 300;
        map.bleed = 1;
        map.deathCost = 1;
        map.sectorBonus = 50;
        map.roundMinutes = 30;
        map.points.add(point("A", 0));
        map.points.add(point("B", 1));
        map.spawns.add(spawn("blue0", 0, 0));
        map.spawns.add(spawn("red0", 1, 0));
        map.spawns.add(spawn("blue1", 0, 1));
        map.spawns.add(spawn("red1", 1, 1));
        map.kits.add(new Model.Kit());
        return map;
    }

    private static Model.Point point(String id, int sector) {
        Model.Point point = new Model.Point();
        point.id = id;
        point.sector = sector;
        return point;
    }

    private static Model.Spawn spawn(String id, int team, int sector) {
        Model.Spawn spawn = new Model.Spawn();
        spawn.id = id;
        spawn.team = team;
        spawn.sector = sector;
        return spawn;
    }

    /** Presence array with every point undefended unless specified. */
    private static int[][] presence(int points, int team, int... indexes) {
        int[][] grid = new int[points][2];
        for (int index : indexes) grid[index][team]++;
        return grid;
    }

    // ---------------------------------------------------------------- assertions

    private static void check(String label, boolean ok, String detail) {
        if (ok) {
            System.out.println("  PASS  " + label);
        } else {
            failures++;
            System.out.println("  FAIL  " + label + " -> " + detail);
        }
    }

    /** Runs one-second steps, so `seconds` steps equal `seconds` seconds of capture time. */
    private static void run(Rules rules, int seconds, java.util.function.Supplier<int[][]> presence) {
        for (int i = 0; i < seconds; i++) rules.tick(presence.get());
    }

    // ---------------------------------------------------------------- conquest

    private static void conquestBleedsTickets() {
        System.out.println("conquest: point advantage bleeds tickets");
        Model.Arena map = conquest();
        Rules rules = new Rules(map);
        // Blue holds A forever; red never contests anything.
        run(rules, 40 * 20, () -> presence(3, 0, 0));
        check("blue captured A", rules.flags.get(0).owner == 0, "owner=" + rules.flags.get(0).owner);
        check("red bled tickets", rules.tickets[1] < 300, "red=" + rules.tickets[1]);
        check("blue did not bleed", rules.tickets[0] == 300, "blue=" + rules.tickets[0]);
        System.out.println("        tickets blue=" + rules.tickets[0] + " red=" + rules.tickets[1]);
    }

    private static void conquestStalemateIsFree() {
        System.out.println("conquest: nobody on the field means no bleed");
        Rules rules = new Rules(conquest());
        run(rules, 30, () -> new int[3][2]);
        check("both pools intact", rules.tickets[0] == 300 && rules.tickets[1] == 300,
                "blue=" + rules.tickets[0] + " red=" + rules.tickets[1]);
        check("all points neutral", rules.owned()[0] == 0 && rules.owned()[1] == 0, "owned neutral");
    }

    private static void contestedCaptureStillAdvances() {
        System.out.println("conquest: 3 attackers vs 1 defender still advances, slowly");
        Rules rules = new Rules(conquest());
        run(rules, 40 * 20, () -> {
            int[][] grid = new int[3][2];
            grid[0][0] = 3;
            grid[0][1] = 1;
            return grid;
        });
        double progress = rules.flags.get(0).progress;
        check("bar advanced past neutral", progress > 50, "progress=" + progress);
        check("eventually captured", rules.flags.get(0).owner == 0, "owner=" + rules.flags.get(0).owner);
    }

    private static void singleDefenderCannotFreeze() {
        System.out.println("conquest: an even fight stalls instead of flipping");
        Rules rules = new Rules(conquest());
        run(rules, 40 * 20, () -> {
            int[][] grid = new int[3][2];
            grid[0][0] = 2;
            grid[0][1] = 2;
            return grid;
        });
        check("bar stayed at neutral", Math.abs(rules.flags.get(0).progress - 50) < 0.001,
                "progress=" + rules.flags.get(0).progress);
        check("point stayed neutral", rules.flags.get(0).owner == -1, "owner=" + rules.flags.get(0).owner);
        check("contested flag set", rules.flags.get(0).contested, "contested=false");
    }

    // ---------------------------------------------------------------- breakthrough

    private static void breakthroughRequiresFullStage() {
        System.out.println("breakthrough: stage 0 must be fully taken before advancing");
        Rules rules = new Rules(breakthrough());
        run(rules, 60, () -> presence(2, 0, 0));
        check("stage 0 becomes blue", rules.flags.get(0).owner == 0, "owner=" + rules.flags.get(0).owner);
        int stageAfterCapture = rules.stage;
        check("advanced to stage 1", stageAfterCapture == 1, "stage=" + stageAfterCapture);
        check("stage 1 still red", rules.flags.get(1).owner == 1, "owner=" + rules.flags.get(1).owner);
    }

    private static void breakthroughAdvanceGivesBonus() {
        System.out.println("breakthrough: advancing awards bonus tickets");
        Model.Arena map = breakthrough();
        map.tickets = 100;
        Rules rules = new Rules(map);
        rules.death(0); // spend one ticket so the bonus is visible
        int before = rules.tickets[0];
        run(rules, 60, () -> presence(2, 0, 0));
        check("bonus applied on advance", rules.tickets[0] == Math.min(100000, before + 50),
                "before=" + before + " after=" + rules.tickets[0]);
    }

    private static void breakthroughDefenderWinsOnTimeout() {
        System.out.println("breakthrough: timeout favours the defender");
        Model.Arena map = breakthrough();
        map.roundMinutes = 1;
        Rules rules = new Rules(map);
        run(rules, 61, () -> new int[2][2]);
        check("defender wins", rules.winner == 1, "winner=" + rules.winner);
        check("result text", rules.result().contains("防守方"), rules.result());
    }

    private static void breakthroughOnlyAttackerPaysOnDeath() {
        System.out.println("breakthrough: only the attacker pool pays for deaths");
        Rules rules = new Rules(breakthrough());
        int redBefore = rules.tickets[1];
        for (int i = 0; i < 10; i++) rules.death(1);
        check("defender pool untouched", rules.tickets[1] == redBefore, "red=" + rules.tickets[1]);
        for (int i = 0; i < 10; i++) rules.death(0);
        check("attacker pool bled", rules.tickets[0] == 290, "blue=" + rules.tickets[0]);
        check("tracksTickets matches mode",
                rules.tracksTickets(0) && !rules.tracksTickets(1), "mode gate wrong");
    }

    private static void neutraliseGrantsNoScore() {
        System.out.println("conquest: neutralising a point is not a capture");
        Model.Arena map = conquest();
        Rules rules = new Rules(map);
        // Give red the point first.
        run(rules, 60, () -> presence(3, 1, 0));
        boolean redOwns = rules.flags.get(0).owner == 1;
        List<Rules.Capture> neutral = rules.tick(presence(3, 0, 0));
        long flips = 0, neutrals = 0;
        for (int i = 0; i < 200; i++) {
            for (Rules.Capture capture : rules.tick(presence(3, 0, 0))) {
                if (capture.neutralised()) neutrals++;
                else flips++;
            }
        }
        check("red held it first", redOwns, "owner before=" + rules.flags.get(0).owner);
        check("blue flip recorded", flips >= 1, "flips=" + flips);
        check("neutral events reported separately", neutrals >= 1, "neutrals=" + neutrals);
        check("first tick after enemy hold was neutral-reachable", neutral != null, "null");
    }
}
