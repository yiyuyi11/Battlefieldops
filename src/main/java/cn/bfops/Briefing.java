package cn.bfops;

/**
 * Laying out the map and the scoreboard side by side on one screen.
 *
 * <p>Reading a match is a two-question job: <em>who is winning</em> and <em>where is everyone</em>.
 * Splitting them across two overlays means the player has to hold one key, read it, release it, hold
 * another, and remember the first answer while looking at the second. So one key shows both, and this
 * class decides how the space is split.
 *
 * <p>The split is width-dependent, which is why it lives here rather than inline in the renderer: a
 * narrow window cannot hold two columns without the map becoming too small to read, so it stacks them
 * instead, and the decision is worth checking without a running game.
 */
final class Briefing {
    /** Which way the two panes are arranged. */
    enum Split {
        /** Map left, scoreboard right. The usual case on a normal window. */
        SIDE_BY_SIDE,
        /** Map on top, scoreboard underneath, for tall-and-narrow windows. */
        STACKED,
        /** No room for a map: the scoreboard takes everything, as it did before this existed. */
        SCORE_ONLY
    }

    /** Below this width two columns make both panes unreadable. */
    static final int MIN_SIDE_BY_SIDE = 420;
    /** Below this height a map plus two scoreboard columns cannot both fit. */
    static final int MIN_STACKED_HEIGHT = 260;
    /** The map never gets smaller than this; a map you cannot read is worse than no map. */
    static final int MIN_MAP = 96;

    private Briefing() { }

    /**
     * Whether the always-on HUD minimap should be drawn, given the server's compatibility decisions.
     *
     * <p>The two Xaero flags are not independent, and the combination that matters is easy to get
     * wrong: an operator can reasonably say "the pack has Xaero, so let it keep the corner" <em>and</em>
     * "hide Xaero during matches". Honouring both literally leaves the player with no minimap at all
     * for the whole match — the worst possible outcome, and one no single-flag reading catches.
     *
     * <p>So yielding the corner only counts while Xaero is actually visible. Once it is hidden for the
     * match, the corner is ours again.
     *
     * @param allowed what the compatibility policy alone decided
     * @param xaeroInPack whether the server believes Xaero is installed
     * @param xaeroHidden whether Xaero is being hidden for the duration of this match
     */
    static boolean showHudMinimap(boolean allowed, boolean xaeroInPack, boolean xaeroHidden) {
        if (allowed) return true;
        return xaeroInPack && xaeroHidden;
    }

    /**
     * Which corner the HUD minimap takes.
     *
     * <p>Xaero defaults to the top-right, so while it is on screen we move out of its way. When it is
     * hidden we take the top-right back, because that is where players already look for a minimap.
     */
    static int hudMinimapX(boolean xaeroVisible, int guiWidth, int size) {
        return xaeroVisible ? 8 : guiWidth - size - 8;
    }

    /** Every corner the minimap can be placed in, in the order the editor cycles through them. */
    static final java.util.List<String> MINIMAP_CORNERS =
            java.util.List.of("TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT");

    /** Chinese labels, so the settings row reads as a choice rather than as an enum dump. */
    static String minimapCornerName(String corner) {
        return switch (normaliseCorner(corner)) {
            case "TOP_RIGHT" -> "右上";
            case "BOTTOM_LEFT" -> "左下";
            case "BOTTOM_RIGHT" -> "右下";
            default -> "左上";
        };
    }

    /** Whether the text names a real corner, as opposed to being absent or garbage. */
    static boolean isCorner(String corner) {
        if (corner == null) return false;
        return MINIMAP_CORNERS.contains(corner.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /** Unknown or blank corner names fall back to the default instead of drawing off-screen. */
    static String normaliseCorner(String corner) {
        if (corner == null) return "TOP_LEFT";
        String upper = corner.trim().toUpperCase(java.util.Locale.ROOT);
        return MINIMAP_CORNERS.contains(upper) ? upper : "TOP_LEFT";
    }

    /** The next corner in the cycle, for the settings row. */
    static String nextCorner(String corner) {
        int index = MINIMAP_CORNERS.indexOf(normaliseCorner(corner));
        return MINIMAP_CORNERS.get((index + 1) % MINIMAP_CORNERS.size());
    }

    /** Margin from the screen edge, matching the existing HUD inset. */
    static final int MINIMAP_MARGIN = 8;
    /**
     * How far down a top corner must start to clear the ticket bar and the objective cards.
     *
     * <p>Both are drawn across the top centre, so a minimap flush against the top edge lands on top of
     * them on any window narrow enough for the two bands to meet.
     */
    static final int MINIMAP_TOP_INSET = 34;
    /** How far up a bottom corner must start to clear the capture bar and the objective hint. */
    static final int MINIMAP_BOTTOM_INSET = 42;

    /**
     * Screen position of the minimap's top-left corner.
     *
     * <p>Pure arithmetic so it can be checked headlessly: the failure modes are a minimap half off the
     * screen, or one sitting on top of the ticket bar, and neither is visible until a player with a
     * particular window size complains.
     *
     * @return {@code {x, y}}
     */
    static int[] minimapOrigin(String corner, int guiWidth, int guiHeight, int size) {
        String which = normaliseCorner(corner);
        boolean right = which.endsWith("RIGHT");
        boolean bottom = which.startsWith("BOTTOM");
        int x = right ? guiWidth - size - MINIMAP_MARGIN : MINIMAP_MARGIN;
        int y = bottom ? guiHeight - size - MINIMAP_BOTTOM_INSET : MINIMAP_TOP_INSET;
        // Clamp so a small window with a large minimap keeps it on screen rather than clipping it.
        x = Math.max(0, Math.min(Math.max(0, guiWidth - size), x));
        y = Math.max(0, Math.min(Math.max(0, guiHeight - size), y));
        return new int[]{x, y};
    }

    /**
     * Chooses an arrangement for the available space.
     *
     * <p>Side-by-side is preferred because the two panes are both wide (a map wants a square, a
     * scoreboard wants two columns); stacking is the fallback for narrow windows, and giving up the map
     * entirely is the last resort — the scoreboard is the part that has no alternative.
     */
    static Split split(int width, int height) {
        if (width >= MIN_SIDE_BY_SIDE && width - MIN_MAP >= 180 && height >= MIN_MAP) {
            return Split.SIDE_BY_SIDE;
        }
        if (height >= MIN_STACKED_HEIGHT && width >= MIN_MAP) return Split.STACKED;
        return Split.SCORE_ONLY;
    }

    /**
     * The two panes for a split, in a rect already inset from the screen edge.
     *
     * @return {@code [map, scoreboard]}; the map is null for {@link Split#SCORE_ONLY}
     */
    static Layout.Rect[] panes(Split split, Layout.Rect area) {
        if (split == Split.SCORE_ONLY || area.w() <= 0 || area.h() <= 0) {
            return new Layout.Rect[]{null, area};
        }
        if (split == Split.STACKED) {
            int mapHeight = Math.max(MIN_MAP, area.h() * 45 / 100);
            Layout.Rect map = new Layout.Rect(area.x(), area.y(), area.w(), mapHeight);
            Layout.Rect scores = new Layout.Rect(area.x(), area.y() + mapHeight + 4,
                    area.w(), Math.max(0, area.h() - mapHeight - 4));
            return new Layout.Rect[]{map, scores};
        }
        // Side by side: the map takes a square-ish share, the scoreboard gets the wider remainder
        // because it holds two team columns.
        int mapWidth = Math.max(MIN_MAP, Math.min(area.h(), area.w() * 42 / 100));
        Layout.Rect map = new Layout.Rect(area.x(), area.y(), mapWidth, area.h());
        Layout.Rect scores = new Layout.Rect(area.x() + mapWidth + 6, area.y(),
                Math.max(0, area.w() - mapWidth - 6), area.h());
        return new Layout.Rect[]{map, scores};
    }
}
