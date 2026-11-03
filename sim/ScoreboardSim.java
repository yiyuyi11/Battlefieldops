package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline tests for the full scoreboard layout.
 *
 * <p>Ranking order is the part users notice first and the part that is easy to get wrong: an
 * unstable sort makes the list reshuffle between refreshes while nothing has changed, which looks
 * like a bug even though every number is correct.
 */
public final class ScoreboardSim {
    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        rankingTests();
        tieBreakTests();
        limitTests();
        colorTests();
        labelTests();
        aggregateTests();

        System.out.println();
        if (failures.isEmpty()) {
            System.out.println("ALL SCOREBOARD TESTS PASSED");
            System.out.println("passed: " + passed);
        } else {
            System.out.println("FAILURES: " + failures.size());
            for (String failure : failures) System.out.println("  FAIL " + failure);
            System.exit(1);
        }
    }

    private static Scoreboard.Row row(String name, int team, int kills, int deaths, int score) {
        return new Scoreboard.Row(name, team, kills, deaths, score, 0, true, false);
    }

    private static void rankingTests() {
        List<Scoreboard.Row> all = List.of(
                row("blue-low", 0, 1, 2, 100),
                row("red-high", 1, 5, 1, 900),
                row("blue-high", 0, 9, 0, 800),
                row("red-low", 1, 0, 4, 50));

        List<Scoreboard.Row> blue = Scoreboard.ranked(all, 0);
        check("blue has two rows", blue.size() == 2);
        check("blue sorted by score desc", blue.get(0).name().equals("blue-high"));
        check("blue second is the low scorer", blue.get(1).name().equals("blue-low"));
        check("blue excludes red", blue.stream().noneMatch(r -> r.team() == 1));

        List<Scoreboard.Row> red = Scoreboard.ranked(all, 1);
        check("red sorted by score desc", red.get(0).name().equals("red-high"));

        // Spectators (team -1) must never appear in a team column.
        List<Scoreboard.Row> withSpectator = new ArrayList<>(all);
        withSpectator.add(new Scoreboard.Row("spec", -1, 0, 0, 0, 0, true, false));
        check("spectator excluded from blue", Scoreboard.ranked(withSpectator, 0).size() == 2);
        check("spectator excluded from red", Scoreboard.ranked(withSpectator, 1).size() == 2);
    }

    private static void tieBreakTests() {
        // Equal score: kills decide.
        List<Scoreboard.Row> byKills = List.of(
                row("fewer", 0, 2, 0, 500),
                row("more", 0, 7, 0, 500));
        check("equal score ranks by kills", Scoreboard.ranked(byKills, 0).get(0).name().equals("more"));

        // Equal score and kills: name decides, so the order is stable across refreshes.
        List<Scoreboard.Row> byName = List.of(
                row("zulu", 0, 0, 0, 0),
                row("alpha", 0, 0, 0, 0));
        List<Scoreboard.Row> first = Scoreboard.ranked(byName, 0);
        check("equal score and kills order by name", first.get(0).name().equals("alpha"));

        // Re-ranking the same data in a different input order must give the same output; without a
        // stable total order the panel would reshuffle every refresh.
        List<Scoreboard.Row> reversed = List.of(
                row("alpha", 0, 0, 0, 0),
                row("zulu", 0, 0, 0, 0));
        check("ranking is input-order independent",
                Scoreboard.ranked(reversed, 0).get(0).name().equals(first.get(0).name()));
    }

    private static void limitTests() {
        List<Scoreboard.Row> many = new ArrayList<>();
        for (int i = 0; i < 30; i++) many.add(row("p" + i, 0, i, 0, i * 10));

        check("top respects limit", Scoreboard.top(many, 10).size() == 10);
        // top() only caps; ranked() sorts. Prod code calls top(ranked(...)), so exercise that pair.
        check("top takes the best after ranking",
                Scoreboard.top(Scoreboard.ranked(many, 0), 3).get(0).name().equals("p29"));
        check("top alone preserves input order", Scoreboard.top(many, 3).get(0).name().equals("p0"));
        check("overflow counted", Scoreboard.overflow(many, 10) == 20);
        check("no overflow when it all fits", Scoreboard.overflow(many, 30) == 0);
        check("overflow never negative", Scoreboard.overflow(many, 99) == 0);

        // Degenerate limits must not throw, because the limit is derived from window height.
        check("zero limit yields nothing", Scoreboard.top(many, 0).isEmpty());
        check("negative limit yields nothing", Scoreboard.top(many, -5).isEmpty());
        check("zero limit counts all as overflow", Scoreboard.overflow(many, 0) == 30);
        check("empty list stays empty", Scoreboard.top(List.of(), 10).isEmpty());
    }

    private static void colorTests() {
        check("blue row is blue", Scoreboard.color(row("a", 0, 0, 0, 0)) == Scoreboard.BLUE);
        check("red row is red", Scoreboard.color(row("a", 1, 0, 0, 0)) == Scoreboard.RED);
        check("spectator is neutral", Scoreboard.color(row("a", -1, 0, 0, 0)) == Scoreboard.NEUTRAL);

        // Dead players stay in the list but dim, so the count and the roster still read correctly.
        Scoreboard.Row dead = new Scoreboard.Row("dead", 0, 3, 1, 200, 0, false, false);
        check("dead row is dimmed", Scoreboard.color(dead) == Scoreboard.DEAD);
        check("dead row keeps its stats", Scoreboard.stats(dead).equals("3/1  200"));
    }

    private static void labelTests() {
        Scoreboard.Row other = new Scoreboard.Row("bob", 0, 0, 0, 0, 0, true, false);
        check("other players are plain", Scoreboard.label(other).equals("bob"));

        Scoreboard.Row self = new Scoreboard.Row("bob", 0, 0, 0, 0, 0, true, true);
        check("self is marked", Scoreboard.label(self).startsWith("▶"));
        check("self marker keeps the name", Scoreboard.label(self).endsWith("bob"));
    }

    private static void aggregateTests() {
        List<Scoreboard.Row> rows = List.of(
                new Scoreboard.Row("a", 0, 1, 0, 100, 2, true, false),
                new Scoreboard.Row("b", 0, 1, 0, 250, 0, false, false),
                new Scoreboard.Row("c", 1, 1, 0, 500, 0, true, false));

        check("team score sums that team only", Scoreboard.teamScore(rows, 0) == 350);
        check("red team score excluded", Scoreboard.teamScore(rows, 1) == 500);
        check("team score of empty team is zero", Scoreboard.teamScore(rows, 5) == 0);

        check("alive count counts only the living", Scoreboard.teamAlive(rows, 0) == 1);
        check("red alive count", Scoreboard.teamAlive(rows, 1) == 1);
        check("alive count of empty team is zero", Scoreboard.teamAlive(rows, 9) == 0);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failures.add(name);
        }
    }
}
