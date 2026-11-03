package cn.bfops;

/**
 * Headless checks for the reference-derived loadout card geometry.
 *
 * <p>The values are the reference pack's constants (see 参考端-配装与载具界面-结构提取.md), and the
 * assertions pin the one property that matters for "don't copy unscaled": the left column is clamped to
 * {@code [128, 170]}, and the two-column attachment grid tiles the card's inner width exactly, so it can
 * never overflow or leave a ragged right edge at any resolution.
 */
public final class LoadoutLayoutSim {
    private static int checks;

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAIL " + label);
        System.out.println("  PASS  " + label);
    }

    public static void main(String[] args) {
        leftColumnClamp();
        cardStack();
        attachmentGrid();
        System.out.println("LoadoutLayoutSim: " + checks + " checks passed");
    }

    /** The left column reserves 360 for the right panel and clamps to [128, 170]. */
    private static void leftColumnClamp() {
        check(LoadoutLayout.leftColumnWidth(960) == 170, "wide screen -> left column at max 170");
        check(LoadoutLayout.leftColumnWidth(640) == 170, "narrow-but-normal screen -> left column still 170");
        check(LoadoutLayout.leftColumnWidth(500) == 128, "small screen clamps up to the 128 floor");
        check(LoadoutLayout.leftColumnWidth(388) == 128, "exactly-zero remainder clamps to 128");
        check(LoadoutLayout.leftColumnWidth(300) == 128, "negative remainder still clamps to 128");
        check(LoadoutLayout.leftColumnWidth(2000) == 170, "very wide screen clamps down to 170");
    }

    /** The right pane is an overview strip plus two 132-pixel cards, total 348. */
    private static void cardStack() {
        check(LoadoutLayout.contentHeight() == 348, "content height is 60 + 12 + 132 + 12 + 132 = 348");
        check(LoadoutLayout.primaryCardTop() == 72, "primary card starts below the overview (60+12)");
        check(LoadoutLayout.secondaryCardTop() == 216, "secondary card starts below the primary (72+132+12)");
        check(LoadoutLayout.rightPanelLeft(960) == 190, "right panel starts at 8 + 170 + 12");
        check(LoadoutLayout.rightPanelRight(960) == 952, "right panel ends at width - 8");
        check(LoadoutLayout.cardInnerWidth(960) == 746, "card inner width is panel width minus 16");
    }

    /** The two-column attachment grid tiles the card inner width exactly and keeps row spacing. */
    private static void attachmentGrid() {
        check(LoadoutLayout.ATTACHMENT_ORDER.size() == 6, "six attachment slots in reference order");
        check(LoadoutLayout.attachmentButtonWidth(960) == 369, "button width is half the inner width minus half the gap");
        check(LoadoutLayout.attachmentButtonX(960, 0) == 198, "column 0 sits at panel left + 8");
        check(LoadoutLayout.attachmentButtonX(960, 1) == 575, "column 1 sits after column 0 plus the gap");
        check(LoadoutLayout.attachmentButtonY(0) == 42 && LoadoutLayout.attachmentButtonY(1) == 66
                && LoadoutLayout.attachmentButtonY(2) == 90, "rows at 42/66/90 (18 tall + 6 gap)");
        // The whole grid spans exactly the card's inner width: no overflow, no ragged right edge.
        int gridLeft = LoadoutLayout.attachmentButtonX(960, 0);
        int gridRight = LoadoutLayout.attachmentButtonX(960, 1) + LoadoutLayout.attachmentButtonWidth(960);
        check(gridRight - gridLeft == LoadoutLayout.cardInnerWidth(960),
                "the grid tiles the card inner width exactly (got " + (gridRight - gridLeft) + ")");
        // The button width floor (74) applies on a very narrow card.
        check(LoadoutLayout.attachmentButtonWidth(300) == 74, "narrow card clamps button width to the 74 floor");
    }
}
