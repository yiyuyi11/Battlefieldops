package cn.bfops;

import java.util.List;

/**
 * The award feed's timing and formatting.
 *
 * <p>Timing is the whole feature: too short and the player never reads the number, too long and the feed becomes
 * clutter over the crosshair. Pinning the curve here means it can be tuned without launching the game, and that
 * a clock jump cannot make an old award spring back to full opacity.
 */
public final class FeedSim {
    private static int checks;

    public static void main(String[] args) {
        Feed.Popup cosmetic = new Feed.Popup("击杀",0,1000,"僵尸","kill");
        check(cosmetic.text().equals("击杀"), "non-player kill has no points label, including no +0");
        check(cosmetic.detailText().equals("僵尸"), "cosmetic kill names the victim");
        check(Feed.alpha(cosmetic,1100)>0, "zero-point kill still animates");
        check(Feed.prune(java.util.List.of(cosmetic),1000+Feed.LIFETIME_MS).isEmpty(), "cosmetic kill still expires");

        formatting();
        lifecycle();
        stacking();
        System.out.println("FeedSim: " + checks + " checks passed");
    }

    private static void formatting() {
        check(Feed.format(100, "击杀").equals("+100 击杀"), "a positive award shows the points and the label");
        check(Feed.format(0, "占领").equals("占领"), "a zero-point award shows only the label");
        check(Feed.format(-5, "占领").equals("占领"), "a negative award never shows a minus sign");
        check(Feed.format(100, null).equals("+100 奖励"), "a missing label falls back to a placeholder");
        check(Feed.format(100, "  ").equals("+100 奖励"), "a blank label falls back to a placeholder");
        check(Feed.format(1, "命中").equals("+1 命中"), "a single point still reads as an award");

        Feed.Popup popup = new Feed.Popup("击杀", 100, 0);
        check(popup.text().equals("+100 击杀"), "the popup text uses the same formatting rule");
    }

    private static void lifecycle() {
        long born = 1_000_000L;
        Feed.Popup popup = new Feed.Popup("击杀", 100, born);

        check(Feed.progress(popup, born) == 0, "an award starts at zero progress");
        check(Feed.progress(popup, born + Feed.LIFETIME_MS / 2) > 0.4
                && Feed.progress(popup, born + Feed.LIFETIME_MS / 2) < 0.6, "halfway is halfway");
        check(Feed.progress(popup, born + Feed.LIFETIME_MS) == 1, "a finished award is at full progress");

        // A clock that jumps backwards must not un-fade an award.
        check(Feed.progress(popup, born - 5_000) == 0, "a clock jump back clamps to zero progress");
        check(Feed.progress(popup, born + Feed.LIFETIME_MS * 10) == 1, "a far-future clock clamps to one");

        // Alpha holds steady, then fades only at the tail.
        check(Feed.alpha(popup, born) == 1, "an award starts fully opaque");
        check(Feed.alpha(popup, born + (long) (Feed.LIFETIME_MS * 0.5)) == 1,
                "an award is still fully opaque halfway through");
        double nearEnd = Feed.alpha(popup, born + (long) (Feed.LIFETIME_MS * 0.95));
        check(nearEnd > 0 && nearEnd < 0.3, "the tail is where it fades (" + nearEnd + ")");
        check(Feed.alpha(popup, born + Feed.LIFETIME_MS) == 0, "a finished award is invisible");

        // Pruning drops exactly the finished ones.
        Feed.Popup fresh = new Feed.Popup("占领", 200, born + Feed.LIFETIME_MS);
        List<Feed.Popup> pruned = Feed.prune(List.of(popup, fresh), born + Feed.LIFETIME_MS);
        check(pruned.size() == 1, "only the still-live award survives");
        check(pruned.getFirst().label().equals("占领"), "and it is the newer one");
        check(Feed.prune(List.of(popup), born + Feed.LIFETIME_MS * 2).isEmpty(),
                "everything expires eventually");
        check(Feed.prune(null, born).isEmpty(), "pruning null yields nothing");
    }

    private static void stacking() {
        long now = 5_000L;
        Feed.Popup first = new Feed.Popup("击杀", 100, now);

        List<Feed.Popup> one = Feed.add(List.of(), first);
        check(one.size() == 1, "the first award is added");
        check(one.getFirst() == first, "and it is the same entry");

        // Newest first: the award the player most wants to read just happened.
        Feed.Popup second = new Feed.Popup("占领", 200, now + 100);
        List<Feed.Popup> two = Feed.add(one, second);
        check(two.size() == 2, "a second award stacks");
        check(two.getFirst() == second, "the newest award is at the top of the list");
        check(two.get(1) == first, "the older award follows it");

        // The cap keeps the newest, so a burst cannot fill the screen.
        List<Feed.Popup> many = List.of();
        for (int i = 0; i < Feed.MAX_ENTRIES + 3; i++) {
            many = Feed.add(many, new Feed.Popup("击杀", 100 + i, now + i * 10));
        }
        check(many.size() == Feed.MAX_ENTRIES, "the feed is capped");
        check(many.getFirst().points() == 100 + Feed.MAX_ENTRIES + 2, "the newest survives the cap");

        check(Feed.add(List.of(first), null).size() == 1, "a null award is ignored");
        check(Feed.add(null, null).isEmpty(), "null in, nothing out");

        // Vertical layout: newer awards sit lower, and everything rises as it ages.
        int newest = Feed.offsetY(first, now, 0);
        int older = Feed.offsetY(first, now, 1);
        check(older > newest, "stacked awards are spaced downwards by index");
        int laterOn = Feed.offsetY(first, now + Feed.LIFETIME_MS, 0);
        check(laterOn < newest, "an award rises as it ages");
        check(Math.abs(laterOn - newest) <= (int) Math.ceil(Feed.RISE_PIXELS),
                "and rises by at most the configured distance");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL  " + message);
            throw new AssertionError(message);
        }
    }
}
