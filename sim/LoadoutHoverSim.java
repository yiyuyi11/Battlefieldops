package cn.bfops;

/**
 * Headless checks for the loadout hover panel's placement and content.
 *
 * <p>The reported failure that motivates this panel's geometry is specific: a tooltip placed past the window
 * edge, because the code assumed a window wide enough to hold it. So the assertions here are about the panel
 * staying inside the window at every anchor position, at several window sizes, and about the flip-to-the-other
 * -side behaviour that keeps it from landing under the cursor.
 */
public final class LoadoutHoverSim {
    private static int checks;

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAIL " + label);
        System.out.println("  PASS  " + label);
    }

    public static void main(String[] args) {
        inside();
        flips();
        oversized();
        content();
        System.out.println("LoadoutHoverSim: " + checks + " checks passed");
    }

    /** Every placed panel is fully inside the window, for a sweep of anchors and window sizes. */
    private static void inside() {
        int[][] windows = {{320, 240}, {480, 270}, {640, 360}, {854, 480}, {1280, 720}, {1920, 1080}};
        int[][] panels = {{90, 50}, {160, 60}, {240, 80}};
        int failures = 0;
        for (int[] window : windows) {
            for (int[] panel : panels) {
                for (int x = 0; x <= window[0]; x += Math.max(1, window[0] / 12)) {
                    for (int y = 0; y <= window[1]; y += Math.max(1, window[1] / 12)) {
                        Layout.Rect rect = LoadoutHover.place(x, y, panel[0], panel[1], window[0], window[1]);
                        if (rect.x() < 0 || rect.y() < 0 || rect.right() > window[0]
                                || rect.bottom() > window[1]) {
                            failures++;
                        }
                    }
                }
            }
        }
        check(failures == 0, "no anchor in any swept window leaves the panel outside it ("
                + failures + " failures)");
    }

    /** Near an edge the panel flips to the other side instead of being clamped over the cursor. */
    private static void flips() {
        // 620 + 6 + 120 = 746, well past the 637 usable right edge, so this must flip.
        Layout.Rect right = LoadoutHover.place(620, 100, 120, 50, 640, 360);
        check(right.right() <= 640, "a panel anchored near the right edge stays inside");
        check(right.x() < 620, "and is placed to the LEFT of the anchor, not clamped on top of it");
        check(!right.inside(620, 100), "the flipped panel does not cover the pointer either");

        // Just inside the limit: the preferred below-right placement is used unchanged.
        Layout.Rect fits = LoadoutHover.place(500, 100, 120, 50, 640, 360);
        check(fits.x() == 506 && fits.right() <= 640, "a panel that fits below-right is not flipped");

        Layout.Rect bottom = LoadoutHover.place(100, 340, 120, 50, 640, 360);
        check(bottom.bottom() <= 360, "a panel anchored near the bottom stays inside");
        check(bottom.y() < 340, "and flips above the anchor");

        Layout.Rect middle = LoadoutHover.place(200, 150, 120, 50, 640, 360);
        check(middle.x() == 206 && middle.y() == 156, "away from any edge it sits below-right of the pointer");
        check(!middle.inside(200, 150), "and never covers the pointer it describes");
    }

    /** A panel larger than the window is shrunk, not pushed off-screen. */
    private static void oversized() {
        Layout.Rect rect = LoadoutHover.place(10, 10, 400, 300, 320, 240);
        check(rect.w() <= 320 - 2 * LoadoutHover.MARGIN, "an oversized panel is shrunk to the window width");
        check(rect.h() <= 240 - 2 * LoadoutHover.MARGIN, "and to the window height");
        check(rect.right() <= 320 && rect.bottom() <= 240, "and still fits inside");
        Layout.Rect tiny = LoadoutHover.place(0, 0, 200, 100, 20, 16);
        check(tiny.w() >= 1 && tiny.h() >= 1, "a window smaller than the margins still yields a usable rect");
    }

    /** The panel's text is built from facts, and says which slot and what is installed. */
    private static void content() {
        LoadoutHover.Panel installed = LoadoutHover.build("瞄具", "红点瞄准镜", 7, "伤害 35 · 射速 600");
        check("瞄具".equals(installed.title()), "the title is the slot name");
        check(installed.lines().get(0).contains("7"), "the compatible-part count is shown");
        check(installed.lines().get(1).contains("红点瞄准镜"), "the installed part is named");
        check(installed.lines().get(2).contains("伤害 35"), "the weapon's properties are carried through");
        check(installed.lines().stream().anyMatch(l -> l.contains("不改变装备")),
                "the panel states that hovering does not edit the gun");

        LoadoutHover.Panel empty = LoadoutHover.build("枪口", "", 0, "");
        check(empty.lines().get(1).contains("默认"), "an unset slot reads as default");
        check(empty.lines().stream().noneMatch(l -> l.contains("伤害")),
                "a weapon with no readable stats adds no property line rather than a fake one");
        check(empty.lines().size() == 3, "and the panel is shorter, not padded with blanks");

        check("Red Dot".equals(LoadoutHover.installedLabel(
                java.util.Map.of("SCOPE", "tacz:red_dot"), "SCOPE")),
                "an installed id is prettified for display (namespace dropped, separators spaced)");
        check("".equals(LoadoutHover.installedLabel(java.util.Map.of(), "SCOPE")),
                "an absent slot yields an empty label");
        check("".equals(LoadoutHover.installedLabel(null, "SCOPE")),
                "a null fitting map is not a crash");
    }
}
