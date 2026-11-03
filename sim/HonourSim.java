package cn.bfops;

import java.util.List;

/**
 * Headless checks for the kill-honour rules.
 *
 * <p>These matter more than most, because the failure mode of this code is silent: nothing throws when an
 * honour fires for the wrong reason, and nobody notices until a player reports that they got "绝对制空" for
 * shooting a jeep. {@link Honours.judge} is a pure function precisely so it can be pinned down here.
 *
 * <p>The checks are written as pairs — one case that must earn the honour, one that must not — because a
 * rule asserted only in its positive direction would pass for an implementation that awarded everything
 * unconditionally.
 */
public final class HonourSim {
    private static int checks;

    /** A plain kill: mid-range, on foot, full health, no tricks. Everything at its default. */
    private static Honours.Evidence plain() {
        return new Honours.Evidence(
                20.0, 0.0, false, false,
                1, false, false, false, false, true,
                false, 0.0, 1.0, 1.0, false);
    }

    public static void main(String[] args) {
        conditionHonours();
        mutualExclusion();
        streaks();
        scoring();
        namesAndIcons();
        noFalsePositives();
        System.out.println("HonourSim: " + checks + " checks passed");
    }

    /** Each condition honour fires when its threshold is met and stays quiet when it is not. */
    private static void conditionHonours() {
        Honours.Evidence base = plain();
        check(!Honours.judge(base).contains("long_distance"), "a mid-range kill is not long distance");
        check(!Honours.judge(base).contains("close_quarter"), "a mid-range kill is not close quarters");

        Honours.Evidence far = with(base, e -> new Honours.Evidence(
                80.0, e.drop(), e.headshot(), e.explosion(), e.streak(), e.victimWasStreak(),
                e.victimSpotted(), e.attackerSpotted(), e.backstab(), e.attackerOnFoot(),
                e.victimInVehicle(), e.attackerMoving(), e.victimHealthFrac(),
                e.attackerHealthFrac(), e.blind()));
        check(Honours.judge(far).contains("long_distance"), "80 blocks is long distance");

        Honours.Evidence close = new Honours.Evidence(
                2.0, 0, false, false, 1, false, false, false, false, true,
                false, 0.0, 1.0, 1.0, false);
        check(Honours.judge(close).contains("close_quarter"), "2 blocks is close quarters");

        Honours.Evidence above = new Honours.Evidence(
                20.0, 12.0, false, false, 1, false, false, false, false, true,
                false, 0.0, 1.0, 1.0, false);
        check(Honours.judge(above).contains("justice_from_above"),
                "12 blocks above the victim earns justice from above");

        Honours.Evidence behind = new Honours.Evidence(
                2.0, 0, false, false, 1, false, false, false, true, true,
                false, 0.0, 1.0, 1.0, false);
        check(Honours.judge(behind).contains("backstab"), "a rear-arc blow earns backstab");

        Honours.Evidence blind = new Honours.Evidence(
                20.0, 0, false, false, 1, false, false, false, false, true,
                false, 0.0, 1.0, 1.0, true);
        check(Honours.judge(blind).contains("blind_kill"), "a blind attacker earns blind kill");

        Honours.Evidence hurt = new Honours.Evidence(
                20.0, 0, false, false, 1, false, false, false, false, true,
                false, 0.0, 1.0, 0.1, false);
        check(Honours.judge(hurt).contains("desperate"), "killing at 10% health earns desperate");

        Honours.Evidence fast = new Honours.Evidence(
                20.0, 0, false, false, 1, false, false, false, false, true,
                false, 0.5, 1.0, 1.0, false);
        check(Honours.judge(fast).contains("moving_kill"), "killing while moving fast earns moving kill");

        Honours.Evidence boom = new Honours.Evidence(
                20.0, 0, false, true, 1, false, false, false, false, true,
                true, 0.0, 1.0, 1.0, false);
        check(Honours.judge(boom).contains("kill_explosion"), "exploding a vehicle earns kill explosion");
    }

    /**
     * Honours that must not fire together, because they describe the same moment differently.
     *
     * <p>These are the rules that keep the feed readable: a player should get the one label that best
     * describes the kill, not a pile of overlapping ones.
     */
    private static void mutualExclusion() {
        // Above and on foot is "justice from above", never "absolute air control" — that one is for aircraft.
        Honours.Evidence aboveOnFoot = new Honours.Evidence(
                80.0, 15.0, false, false, 1, false, false, false, false, true,
                false, 0.0, 1.0, 1.0, false);
        List<String> onFoot = Honours.judge(aboveOnFoot);
        check(onFoot.contains("justice_from_above"), "on foot from above earns justice from above");
        check(!onFoot.contains("absolute_air_control"),
                "on foot from above does not earn absolute air control");

        // In a vehicle, high, and far: that is air control, not justice from above.
        Honours.Evidence air = new Honours.Evidence(
                80.0, 15.0, false, false, 1, false, false, false, false, false,
                false, 0.0, 1.0, 1.0, false);
        List<String> inAir = Honours.judge(air);
        check(inAir.contains("absolute_air_control"), "long range from a vehicle earns absolute air control");
        check(!inAir.contains("justice_from_above"),
                "a vehicle kill is not justice from above");

        // Close and far cannot both hold.
        Honours.Evidence mid = plain();
        check(!Honours.judge(mid).contains("long_distance")
                && !Honours.judge(mid).contains("close_quarter"),
                "a mid-range kill earns neither distance honour");
    }

    private static void streaks() {
        // Streaks below six produce no escalation label: 双杀/三杀 etc. are the streak label itself, handled
        // by Awards.streakLabel, and duplicating them here would double the feed entry.
        for (int streak = 1; streak <= 5; streak++) {
            Honours.Evidence e = new Honours.Evidence(
                    20.0, 0, false, false, streak, false, false, false, false, true,
                    false, 0.0, 1.0, 1.0, false);
            check(!Honours.judge(e).contains("bloodthirsty"),
                    "streak " + streak + " does not earn a rampage label");
        }

        // Six and above escalate through five named grades.
        String[] expected = { "bloodthirsty", "merciless", "valiant", "fierce", "savage" };
        for (int i = 0; i < expected.length; i++) {
            int streak = 6 + i;
            Honours.Evidence e = new Honours.Evidence(
                    20.0, 0, false, false, streak, false, false, false, false, true,
                    false, 0.0, 1.0, 1.0, false);
            check(Honours.judge(e).contains(expected[i]),
                    "streak " + streak + " earns " + expected[i]);
        }

        // Past ten it stays on the top grade rather than inventing more.
        Honours.Evidence huge = new Honours.Evidence(
                20.0, 0, false, false, 25, false, false, false, false, true,
                false, 0.0, 1.0, 1.0, false);
        check(Honours.judge(huge).contains("savage"), "a streak past ten stays on the top grade");

        // A defeated streak is a separate honour, and it is about the victim.
        Honours.Evidence broke = new Honours.Evidence(
                20.0, 0, false, false, 1, true, false, false, false, true,
                false, 0.0, 1.0, 1.0, false);
        check(Honours.judge(broke).contains("interrupted_streak"),
                "killing somebody on a streak earns interrupted streak");
    }

    private static void scoring() {
        // Every awardable honour must either be worth points or be deliberately cosmetic; none may be
        // negative, which would make a good play cost score.
        String[] awardable = { "headshot", "kill_explosion", "long_distance", "close_quarter",
                "justice_from_above", "absolute_air_control", "backstab", "blind_kill", "moving_kill",
                "desperate", "interrupted_streak", "effortless", "bloodthirsty", "merciless", "valiant",
                "fierce", "savage", "spotting_kill", "brave_return" };
        for (String honour : awardable) {
            check(Honours.points(honour) >= 0, honour + " is not worth negative points");
        }
        check(Honours.points("absolute_air_control") > Honours.points("headshot"),
                "the rarest honour is worth more than the most common one");
        check(Honours.points(null) == 0, "a null honour is worth nothing");
    }

    private static void namesAndIcons() {
        // Every awardable honour must have a real name, not the generic fallback.
        String[] awardable = { "headshot", "long_distance", "close_quarter", "justice_from_above",
                "absolute_air_control", "backstab", "blind_kill", "moving_kill", "desperate",
                "interrupted_streak", "effortless", "bloodthirsty", "merciless", "valiant", "fierce",
                "savage", "spotting_kill", "brave_return", "revenge", "assist" };
        for (String honour : awardable) {
            String name = Honours.name(honour);
            check(name != null && !name.isBlank() && !name.equals("击杀"),
                    honour + " has its own name, not the generic fallback");
        }
        // Unknown ids must degrade, not throw.
        check("击杀".equals(Honours.name("no_such_honour")), "an unknown honour falls back to 击杀");
        check("击杀".equals(Honours.name(null)), "a null honour falls back to 击杀");

        // Icons must be kinds FeedIcons actually defines. Asking FeedIcons rather than listing the kinds here:
        // a hardcoded list is a copy of the answer, so adding a glyph would fail the test and removing one
        // would pass it. FeedIcons.defined() distinguishes "exists" from "silently became a skull".
        for (String honour : awardable) {
            String icon = Honours.icon(honour);
            check(FeedIcons.defined(icon),
                    honour + "'s icon is a defined glyph, not a fallback (" + icon + ")");
        }
        // And the probe itself must work, or every check above passes vacuously.
        check(FeedIcons.defined("kill"), "the probe recognises a real glyph");
        check(!FeedIcons.defined("no_such_icon"), "the probe rejects an undefined glyph");
        check(!FeedIcons.defined(null), "the probe rejects null");
    }

    /**
     * The default case, asserted in full.
     *
     * <p>A plain kill must earn nothing beyond the base kill itself. This is the check that would catch an
     * implementation that awarded everything unconditionally — which every positive-only test above would
     * happily pass.
     */
    private static void noFalsePositives() {
        List<String> plainHonours = Honours.judge(plain());
        check(plainHonours.isEmpty(),
                "a plain mid-range on-foot kill earns no honours (got " + plainHonours + ")");

        check(Honours.judge(null).isEmpty(), "null evidence earns nothing rather than throwing");

        // A zero-distance kill must not divide by zero in the backstab check.
        Honours.Evidence same = new Honours.Evidence(
                0.0, 0, false, false, 1, false, false, false, true, true,
                false, 0.0, 1.0, 1.0, false);
        check(Honours.judge(same) != null, "a zero-distance kill does not throw");

        // The unreachable list must actually document the gaps rather than be empty.
        check(!Honours.UNREACHABLE.isEmpty(), "unreachable honours are documented");
        check(Honours.UNREACHABLE.size() >= 8, "the documented gaps cover the notable ones");
    }

    // ------------------------------------------------------------------ helpers

    /** Rebuilds an Evidence with one field replaced, so each check changes exactly one thing. */
    private static Honours.Evidence with(Honours.Evidence e,
                                         java.util.function.UnaryOperator<Honours.Evidence> change) {
        return change.apply(e);
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAIL " + label);
        System.out.println("  PASS  " + label);
    }
}
