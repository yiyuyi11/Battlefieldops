package cn.bfops;

import java.util.List;
import java.util.UUID;

/**
 * Attribution rules: assists, streaks, headshots and revenge.
 *
 * <p>These are the awards that need memory rather than a single event, so they are exactly where bookkeeping
 * bugs hide: an assist window measured from the wrong end, a streak that never expires, a headshot awarded for a
 * shot in the chest. All four are pure functions over timestamps, so all four can be pinned here.
 */
public final class AwardsSim {
    private static int checks;
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    public static void main(String[] args) {
        assists();
        streaks();
        headshots();
        revenge();
        System.out.println("AwardsSim: " + checks + " checks passed");
    }

    private static void assists() {
        long now = 100_000L;
        long window = 10_000L;

        // Only recent damage counts.
        List<Awards.Hit> hits = List.of(
                new Awards.Hit(A, 5, now - 1_000, 64),
                new Awards.Hit(B, 3, now - 30_000, 64));
        List<UUID> helpers = Awards.assists(hits, C, now, window);
        check(helpers.size() == 1, "only the recent contributor gets an assist (" + helpers.size() + ")");
        check(helpers.contains(A), "and it is the one inside the window");
        check(!helpers.contains(B), "stale damage is not an assist");

        // The killer is never also an assist.
        List<UUID> withKiller = Awards.assists(
                List.of(new Awards.Hit(C, 9, now - 100, 64)), C, now, window);
        check(withKiller.isEmpty(), "the killer does not also get an assist");

        // Each helper is paid once, however many times they hit.
        List<UUID> repeated = Awards.assists(List.of(
                new Awards.Hit(A, 2, now - 900, 64),
                new Awards.Hit(A, 4, now - 400, 64),
                new Awards.Hit(A, 1, now - 100, 64)), C, now, window);
        check(repeated.size() == 1, "repeated damage from one helper is one assist");

        // Zero-damage taps do not count: a hit that dealt nothing did not help.
        check(Awards.assists(List.of(new Awards.Hit(A, 0, now - 100, 64)), C, now, window).isEmpty(),
                "a zero-damage hit is not an assist");

        // Order is first-contribution-first, so the feed is stable between runs.
        List<UUID> ordered = Awards.assists(List.of(
                new Awards.Hit(B, 1, now - 900, 64),
                new Awards.Hit(A, 1, now - 800, 64)), C, now, window);
        check(ordered.equals(List.of(B, A)), "helpers are reported in the order they first dealt damage");

        check(Awards.assists(null, C, now, window).isEmpty(), "no ledger means no assists");
        check(Awards.assists(List.of(new Awards.Hit(A, 5, now, 64)), C, now, 0).isEmpty(),
                "a zero-length window pays nobody");

        // The ledger is bounded and prunes by age. Ages are spread across the window on purpose: a ledger of
        // fresh records would prove nothing about pruning.
        int total = Awards.MAX_RECORDS + 10;
        List<Awards.Hit> grown = List.of();
        for (int i = 0; i < total; i++) {
            // Ascending freshness: later insertions are more recent, which is how a real ledger fills.
            grown = Awards.record(grown, new Awards.Hit(A, 1, now - (total - i) * 1_000L, 64));
        }
        check(grown.size() == Awards.MAX_RECORDS, "the ledger is capped");
        List<Awards.Hit> pruned = Awards.prune(grown, now, window);
        check(pruned.size() < grown.size(), "pruning drops records older than the window");
        check(pruned.stream().allMatch(hit -> now - hit.at() <= window),
                "everything left is inside the window");
        check(pruned.stream().anyMatch(hit -> now - hit.at() == 1_000L), "the freshest record survives");

        // The most recent blow is what a headshot is judged from.
        List<Awards.Hit> latest = Awards.record(List.of(new Awards.Hit(A, 1, now - 500, 63)),
                new Awards.Hit(B, 1, now, 65.2));
        check(Math.abs(Awards.lastHitY(latest) - 65.2) < 1e-9, "the newest blow wins");
        check(Double.isNaN(Awards.lastHitY(List.of())), "no blows means no height");
    }

    private static void streaks() {
        long now = 200_000L;
        long window = 5_000L;

        check(Awards.streak(0, 0, now, window) == 1, "a first kill is a streak of one");
        check(Awards.streak(1, now - 1_000, now, window) == 2, "a kill soon after continues the streak");
        check(Awards.streak(2, now - 1_000, now, window) == 3, "and keeps counting");
        check(Awards.streak(3, now - 60_000, now, window) == 1, "a kill long after starts over");
        check(Awards.streak(3, now - window - 1, now, window) == 1, "just past the window starts over");
        check(Awards.streak(3, now - window, now, window) == 4, "exactly at the window still continues");

        check(Awards.streakLabel(1).equals("击杀"), "one kill is a plain kill");
        check(Awards.streakLabel(2).equals("双杀"), "two is a double");
        check(Awards.streakLabel(3).equals("三杀"), "three is a triple");
        check(Awards.streakLabel(4).equals("四杀"), "four is named");
        check(Awards.streakLabel(5).equals("五杀"), "five is named");
        check(Awards.streakLabel(7).contains("7"), "beyond five falls back to a counter");
        check(Awards.streakLabel(0).equals("击杀"), "a zero streak still reads as a kill");
    }

    private static void headshots() {
        // A standard player is 1.8 blocks tall, feet at y.
        double minY = 64, maxY = 64 + 1.8;

        check(Awards.headshot(65.7, minY, maxY), "a hit at head height is a headshot");
        check(Awards.headshot(maxY, minY, maxY), "a hit at the very top is a headshot");
        check(!Awards.headshot(64.9, minY, maxY), "a hit at the feet is not");
        check(!Awards.headshot(65.0, minY, maxY), "a hit at the waist is not");
        check(!Awards.headshot(65.3, minY, maxY), "a hit at the chest is not");
        // The threshold is documented as the top 22% of the body.
        check(Awards.headshot(minY + 1.8 * 0.78, minY, maxY), "exactly at the threshold counts");
        check(!Awards.headshot(minY + 1.8 * 0.78 - 0.01, minY, maxY), "just below it does not");

        check(!Awards.headshot(65, minY, minY), "a zero-height body can never be hit in the head");
        check(!Awards.headshot(Double.NaN, minY, maxY), "an unknown height is not a headshot");
    }

    private static void revenge() {
        check(Awards.revenge(A, A), "killing the player who killed you is revenge");
        check(!Awards.revenge(A, B), "killing somebody else is not");
        check(!Awards.revenge(null, A), "no previous killer means no revenge");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL  " + message);
            throw new AssertionError(message);
        }
    }
}
