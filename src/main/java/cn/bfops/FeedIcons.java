package cn.bfops;

import net.minecraft.client.gui.GuiGraphics;

import java.util.Map;

/**
 * The little glyphs shown beside each award in the feed.
 *
 * <p><b>Why hand-drawn bitmaps and not a texture.</b> These are seven pixels wide. Served from a texture sheet
 * they would be cropped and resampled into mush at exactly the size they are needed, which is the same reason
 * the map markers are built from fills. Defined as strings, each glyph is legible, reviewable in the source, and
 * costs nothing to add.
 *
 * <p><b>Why shapes differ per award.</b> Colour alone cannot carry the distinction — red/green is the most common
 * colour blindness — and the feed is read in a fraction of a second while something is trying to kill you.
 *
 * <p><b>Why the shapes are not one-per-honour.</b> {@link Honours} names twenty-odd awards, but at seven pixels
 * twenty distinct shapes are indistinguishable from each other. They collapse onto nine shapes that differ in
 * silhouette rather than detail, and the honour's own name is printed beside the glyph — the icon's job is to
 * tell the eye "something good happened, of this broad kind", and the text says which.
 */
final class FeedIcons {
    /** A skull: the shape every elimination gets. The feed colours it white for a kill, red for a headshot. */
    private static final String[] SKULL = {
            "...#####...", "..#######..", ".#########.", ".#########.",
            "##..###..##", "##..###..##", ".####.####.", "..##...##..",
            "..#######..", "...#.#.#...", "...#.#.#..."
    };

    /** A kill: a white skull. */
    private static final String[] KILL = SKULL;

    /** A headshot: the same skull, drawn in red by the feed. */
    private static final String[] HEADSHOT = SKULL;

    /** A diamond: a contribution, not the decisive blow. */
    private static final String[] ASSIST = {
            "...#...",
            "..###..",
            ".#####.",
            "..###..",
            "...#...",
    };

    /** An upward triangle: a comeback. */
    private static final String[] REVENGE = {
            "...#...",
            "..###..",
            ".#####.",
            "#######",
    };

    /** A ring: a marked position. */
    private static final String[] MARK = {
            ".#####.",
            "#.....#",
            "#.....#",
            "#.....#",
            ".#####.",
    };

    /** A flag: ground taken. */
    private static final String[] CAPTURE = {
            "##.....",
            "##.....",
            "##.....",
            "####...",
            "#####..",
            "####...",
            "##.....",
    };

    /** A burst: an explosion, and by extension a destroyed vehicle. */
    private static final String[] EXPLOSION = {
            "...#...",
            "#..#..#",
            ".#.#.#.",
            "..###..",
            "#######",
            "..###..",
            ".#.#.#.",
            "#..#..#",
            "...#...",
    };

    /** A chevron stack: a streak, climbing. */
    private static final String[] STREAK = {
            "..###..",
            ".##.##.",
            "##...##",
            "..###..",
            ".##.##.",
            "##...##",
    };

    /** A downward arrow: a kill from above, or a blow from behind — both read as "struck from off-axis". */
    private static final String[] AERIAL = {
            "#######",
            ".#####.",
            "..###..",
            "...#...",
            "...#...",
            "...#...",
    };

    private static final Map<String, String[]> GLYPHS = Map.of(
            "kill", KILL,
            "headshot", HEADSHOT,
            "assist", ASSIST,
            "revenge", REVENGE,
            "mark", MARK,
            "capture", CAPTURE,
            "explosion", EXPLOSION,
            "streak", STREAK,
            "aerial", AERIAL);

    private FeedIcons() { }

    /** Draws the glyph for {@code kind} with its top-left near {@code x,y}, one pixel per dot. */
    static void draw(GuiGraphics g, String kind, int x, int y, int color) {
        draw(g, kind, x, y, color, 1);
    }

    /**
     * Draws the glyph at an integer pixel scale.
     *
     * <p>Used by the entrance animation: the glyph starts larger and settles, which for a bitmap means each
     * lit dot becomes a small square rather than a single pixel. Integer scaling rather than a scaled blit,
     * because at these sizes a fractional offset turns the shapes into mush — growing by whole pixels keeps
     * every dot a crisp square at every step.
     *
     * @param scale lit-pixel size in screen pixels; 1 is the natural size
     */
    static void draw(GuiGraphics g, String kind, int x, int y, int color, int scale) {
        String[] rows = GLYPHS.get(kind == null ? "" : kind);
        if (rows == null) rows = KILL;
        int cell = Math.max(1, scale);
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            for (int column = 0; column < line.length(); column++) {
                if (line.charAt(column) != '#') continue;
                int left = x + column * cell;
                int top = y + row * cell;
                g.fill(left, top, left + cell, top + cell, color);
            }
        }
    }

    /** Width of the widest glyph, for laying text out beside it. */
    static int width(String kind) {
        String[] rows = GLYPHS.get(kind == null ? "" : kind);
        if (rows == null) rows = KILL;
        int widest = 0;
        for (String line : rows) widest = Math.max(widest, line.length());
        return widest;
    }

    /**
     * Whether a glyph kind is defined, as opposed to falling back to the skull.
     *
     * <p>Exists so the honour tests can tell "this icon exists" from "this icon silently became a skull".
     * Without it the only way to check would be to compare against a hardcoded list, which is a copy of the
     * answer rather than a check on it.
     */
    static boolean defined(String kind) {
        return kind != null && GLYPHS.containsKey(kind);
    }

    static int height(String kind) {
        String[] rows = GLYPHS.get(kind == null ? "" : kind);
        return rows == null ? KILL.length : rows.length;
    }
}
