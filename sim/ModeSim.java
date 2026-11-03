package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end checks of the conquest and breakthrough rule engines.
 *
 * <p>Written because the previous state of these two modes was reviewed by reading rather than by
 * simulating, and reading does not catch a bleed that uses the wrong side, a stage that advances when it
 * should not, or a timeout that declares the wrong winner. Every assertion here drives the real
 * {@link Rules} class with constructed presence data.
 *
 * <p>The two modes are checked against what the rules claim in {@code Rules}' own documentation, so the
 * doc and the behaviour cannot drift apart silently.
 */
public final class ModeSim {
    private static int checks;

    public static void main(String[] args) {
        conquestCapture();
        conquestBleed();
        conquestEnds();
        breakthroughStartsHeld();
        breakthroughAdvancesStage();
        breakthroughWins();
        breakthroughTimesOut();
        captureIsSymmetric();
        System.out.println("ModeSim: " + checks + " checks passed");
    }

    private static Model.Arena arena(String mode, int points, int captureSeconds) {
        Model.Arena map = new Model.Arena();
        map.mode = mode;
        map.captureSeconds = captureSeconds;
        map.tickets = 300;
        map.bleed = 1;
        map.deathCost = 1;
        map.roundMinutes = 30;
        map.sectorBonus = 50;
        map.points = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            Model.Point point = new Model.Point();
            point.id = "P" + i;
            point.sector = 0;
            map.points.add(point);
        }
        return map;
    }

    /** One second of presence: {@code {a, b}} per point. */
    private static int[][] presence(int... pairs) {
        int[][] out = new int[pairs.length / 2][2];
        for (int i = 0; i < out.length; i++) {
            out[i][0] = pairs[i * 2];
            out[i][1] = pairs[i * 2 + 1];
        }
        return out;
    }

    /** Repeats a presence pattern for {@code seconds} seconds. */
    private static void run(Rules rules, int seconds, int[][]... pattern) {
        for (int i = 0; i < seconds; i++) {
            rules.tick(pattern[i % pattern.length]);
        }
    }

    // ------------------------------------------------------------------ conquest

    private static void conquestCapture() {
        Model.Arena map = arena("CONQUEST", 1, 10);
        Rules rules = new Rules(map);
        check(rules.flags.get(0).progress == 50, "conquest starts neutral at 50");

        // Ten seconds of uncontested presence must walk the bar the full way: 100/10 per second.
        run(rules, 10, presence(1, 0));
        check(rules.flags.get(0).progress == 100, "uncontested team 0 fills the bar in captureSeconds");
        check(rules.flags.get(0).owner == 0, "and takes ownership");

        // The midpoint must neutralise before the flip: at exactly 50 from a team-0 hold the point is
        // no longer team 0's, and it is not team 1's either.
        Model.Arena map2 = arena("CONQUEST", 1, 10);
        Rules rules2 = new Rules(map2);
        run(rules2, 20, presence(1, 0));       // hold it
        run(rules2, 5, presence(0, 1));        // 50 points back, to exactly the midpoint
        check(rules2.flags.get(0).owner == -1,
                "crossing the midpoint neutralises rather than flipping");
        checks++;
    }

    private static void conquestBleed() {
        // Holding more points than the opponent must bleed the opponent, and only the opponent.
        Model.Arena map = arena("CONQUEST", 3, 5);
        map.bleed = 2;
        Rules rules = new Rules(map);
        // Take point 0 outright.
        run(rules, 5, presence(1, 0, 0, 0, 0, 0));
        check(rules.flags.get(0).owner == 0, "team 0 takes one point");
        int blueBefore = rules.tickets[0], redBefore = rules.tickets[1];
        rules.tick(presence(0, 0, 0, 0, 0, 0));
        check(rules.tickets[0] == blueBefore, "the side holding more points does not bleed");
        check(rules.tickets[1] < redBefore,
                "the side holding fewer points bleeds (was " + redBefore + ", now " + rules.tickets[1] + ")");

        // Equal holdings must bleed nobody: a neutral map should not be a slow clock.
        Model.Arena even = arena("CONQUEST", 2, 5);
        Rules evenRules = new Rules(even);
        int start0 = evenRules.tickets[0], start1 = evenRules.tickets[1];
        for (int i = 0; i < 10; i++) evenRules.tick(presence(0, 0, 0, 0));
        check(evenRules.tickets[0] == start0 && evenRules.tickets[1] == start1,
                "equal holdings bleed nobody");
        checks++;
    }

    private static void conquestEnds() {
        // A pool at zero decides the match, and the deciding side is the one that still has tickets.
        Model.Arena map = arena("CONQUEST", 1, 5);
        map.tickets = 3;
        map.bleed = 1;
        Rules rules = new Rules(map);
        run(rules, 5, presence(1, 0));
        check(rules.flags.get(0).owner == 0, "point taken");
        // Red holds nothing, so red bleeds one per second and hits zero.
        for (int i = 0; i < 5 && rules.winner == -2; i++) rules.tick(presence(0, 0));
        check(rules.winner == 0, "team 0 wins when team 1 runs out of tickets");

        // Timeout with unequal tickets awards the side with more.
        Model.Arena timeout = arena("CONQUEST", 1, 5);
        timeout.roundMinutes = 1;
        Rules timeoutRules = new Rules(timeout);
        timeoutRules.tickets[0] = 100;
        timeoutRules.tickets[1] = 50;
        for (int i = 0; i < 60; i++) timeoutRules.tick(presence(0, 0));
        check(timeoutRules.winner == 0, "timeout awards the ticket leader");

        // Timeout with equal tickets is a draw, not a coin flip.
        Model.Arena drawn = arena("CONQUEST", 1, 5);
        drawn.roundMinutes = 1;
        Rules drawRules = new Rules(drawn);
        for (int i = 0; i < 60; i++) drawRules.tick(presence(0, 0));
        check(drawRules.winner == -1, "timeout with equal tickets is a draw");
        checks++;
    }

    // ------------------------------------------------------------------ breakthrough

    private static void breakthroughStartsHeld() {
        Model.Arena map = arena("BREAKTHROUGH", 2, 5);
        Rules rules = new Rules(map);
        check(rules.flags.get(0).owner == 1, "the defender starts holding every point");
        check(rules.flags.get(0).progress == 0, "and the bar starts fully with the defender");
        check(!rules.tracksTickets(1), "only the attacker owns a ticket pool");
        check(rules.tracksTickets(0), "the attacker does");
        checks++;
    }

    private static void breakthroughAdvancesStage() {
        Model.Arena map = arena("BREAKTHROUGH", 2, 5);
        map.points.get(0).sector = 0;
        map.points.get(1).sector = 1;
        Rules rules = new Rules(map);
        int bonusBefore = rules.tickets[0];

        // Only stage 0 is active; taking it must advance the stage, not end the match.
        run(rules, 5, presence(1, 0, 0, 0));
        check(rules.stage == 1, "clearing the active stage advances it (stage=" + rules.stage + ")");
        check(rules.winner == -2, "the match does not end on a non-final stage");
        check(rules.tickets[0] == bonusBefore + map.sectorBonus,
                "advancing grants the sector bonus");

        // Presence on the now-inactive stage 0 must not matter any more.
        check(!rules.active(0), "the cleared stage is no longer active");
        check(rules.active(1), "the new stage is active");

        // Take the final stage: that wins.
        run(rules, 5, presence(0, 0, 1, 0));
        check(rules.winner == 0, "clearing the final stage wins (winner=" + rules.winner + ")");
        checks++;
    }

    private static void breakthroughWins() {
        // A single-stage breakthrough must end on the first capture, not advance into nothing.
        Model.Arena map = arena("BREAKTHROUGH", 1, 5);
        Rules rules = new Rules(map);
        run(rules, 5, presence(1, 0));
        check(rules.winner == 0, "a one-stage breakthrough ends when the stage is cleared");
        check(rules.stage == 0, "and does not advance past its only stage");
        checks++;
    }

    private static void breakthroughTimesOut() {
        Model.Arena map = arena("BREAKTHROUGH", 1, 5);
        map.roundMinutes = 1;
        Rules rules = new Rules(map);
        for (int i = 0; i < 60; i++) rules.tick(presence(0, 0));
        check(rules.winner == 1, "the defender wins breakthrough on timeout");
        checks++;
    }

    private static void captureIsSymmetric() {
        // Three attackers against one defender must advance the bar, just slower: (3-1)/(3+1) = 0.5.
        Model.Arena map = arena("CONQUEST", 1, 10);
        Rules rules = new Rules(map);
        rules.tick(presence(3, 1));
        double expected = 50 + (3 - 1.0) / (3 + 1) * (100.0 / 10);
        check(Math.abs(rules.flags.get(0).progress - expected) < 1e-9,
                "outnumbering advances proportionally (was " + rules.flags.get(0).progress
                        + ", expected " + expected + ")");
        check(rules.flags.get(0).contested, "both sides present is contested");

        // An even fight stalls: net ratio zero.
        Model.Arena stall = arena("CONQUEST", 1, 10);
        Rules stallRules = new Rules(stall);
        stallRules.tick(presence(2, 2));
        check(stallRules.flags.get(0).progress == 50, "an even fight does not move the bar");

        // An empty point does not decay on its own.
        Model.Arena idle = arena("CONQUEST", 1, 10);
        Rules idleRules = new Rules(idle);
        idleRules.tick(presence(0, 0));
        check(idleRules.flags.get(0).progress == 50, "an empty point holds its progress");
        checks++;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
