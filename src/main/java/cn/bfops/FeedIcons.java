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

    private static final Map<String, String[]> GLYPHS = Map.of(
            "kill", KILL,
            "headshot", HEADSHOT,
            "assist", ASSIST,
            "revenge", REVENGE,
            "mark", MARK,
            "capture", CAPTURE);

    private FeedIcons() { }

    /** Draws the glyph for {@code kind} with its top-left near {@code x,y}, one pixel per dot. */
    static void draw(GuiGraphics g, String kind, int x, int y, int color) {
        String[] rows = GLYPHS.get(kind == null ? "" : kind);
        if (rows == null) rows = KILL;
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            for (int column = 0; column < line.length(); column++) {
                if (line.charAt(column) != '#') continue;
                g.fill(x + column, y + row, x + column + 1, y + row + 1, color);
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

    static int height(String kind) {
        String[] rows = GLYPHS.get(kind == null ? "" : kind);
        return rows == null ? KILL.length : rows.length;
    }
}
