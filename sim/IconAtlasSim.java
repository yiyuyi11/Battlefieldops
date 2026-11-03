package cn.bfops;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pins that the shipped icon atlas matches the constants the code blits with.
 *
 * <p>This existed as an eye check before, and an eye check is exactly what does not happen when the art
 * is regenerated months later. The failure is silent in the worst way: if {@code COUNT} is smaller than
 * the sheet, nothing breaks and the extra cells are dead weight; if it is larger, {@code draw} happily
 * reads past the sheet and the icon shows whatever is in the atlas's unused region — usually nothing,
 * occasionally a slice of the neighbouring glyph. Either way it looks like a rendering glitch rather
 * than a mismatch, so the numbers are compared here.
 *
 * <p>The per-cell ink check is the other half. A glyph authored on the wrong row count still produces a
 * correctly sized PNG, and an empty cell is invisible in code review but shows as a blank space where a
 * button's icon should be.
 */
public final class IconAtlasSim {
    private static int checks;
    private static final Path ATLAS =
            Path.of("src/main/resources/assets/battlefield_ops/textures/gui/tactical_icons.png");

    public static void main(String[] args) throws IOException {
        atlasMatchesTheConstants();
        everyCellHasInk();
        glyphsAreCrispNotAntialiased();
        System.out.println("IconAtlasSim: " + checks + " checks passed");
    }

    /** Width is a whole number of cells and the height is exactly one cell. */
    private static void atlasMatchesTheConstants() throws IOException {
        check(Files.isRegularFile(ATLAS), "the atlas is present at " + ATLAS);
        BufferedImage image = ImageIO.read(ATLAS.toFile());
        check(image != null, "the atlas decodes as an image");
        check(image.getWidth() == UiIcons.CELL * UiIcons.COUNT,
                "atlas width is CELL * COUNT: got " + image.getWidth() + ", expected "
                        + (UiIcons.CELL * UiIcons.COUNT));
        check(image.getHeight() == UiIcons.CELL,
                "atlas height is one cell: got " + image.getHeight());
        check(UiIcons.COUNT == 17, "the sheet holds seventeen glyphs, got " + UiIcons.COUNT);
        checks++;
    }

    /**
     * No cell is blank, and the three glyphs added for standings/statistics/maps are actually drawn.
     *
     * <p>A blank cell is not a crash: {@code draw} blits transparent pixels and the row simply has no
     * icon. That reads as a layout bug ("the icon is missing") rather than as a missing asset.
     */
    private static void everyCellHasInk() throws IOException {
        BufferedImage image = ImageIO.read(ATLAS.toFile());
        for (int cell = 0; cell < UiIcons.COUNT; cell++) {
            int ink = 0;
            for (int y = 0; y < UiIcons.CELL; y++) {
                for (int x = 0; x < UiIcons.CELL; x++) {
                    if ((image.getRGB(cell * UiIcons.CELL + x, y) >>> 24) != 0) ink++;
                }
            }
            // 20 is the floor the chevrons set; anything less would be an indistinguishable speck.
            check(ink >= 20, "cell " + cell + " actually draws something, ink=" + ink);
        }
        // The named constants must point at drawn cells, not just be in range.
        for (int id : new int[]{UiIcons.RANK, UiIcons.CHART, UiIcons.COMPASS}) {
            check(id >= 0 && id < UiIcons.COUNT, "new glyph " + id + " is inside the sheet");
        }
        // And they must be distinct cells, or two unrelated controls draw the same picture.
        check(UiIcons.RANK != UiIcons.CHART && UiIcons.CHART != UiIcons.COMPASS
                        && UiIcons.RANK != UiIcons.COMPASS,
                "the three new glyphs occupy three cells");
        checks++;
    }

    /**
     * Every opaque pixel is one of the palette colours.
     *
     * <p>This is the anti-aliasing check. Drawing the sheet with PIL primitives and then scaling it —
     * what the first version did — leaves hundreds of intermediate colours along every edge, and those
     * are precisely what renders as fuzz. Integer-authored pixel art has a palette of five or six exact
     * values; anything else means something reintroduced soft edges.
     */
    private static void glyphsAreCrispNotAntialiased() throws IOException {
        BufferedImage image = ImageIO.read(ATLAS.toFile());
        java.util.Set<Integer> inks = new java.util.HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) != 0) inks.add(argb);
            }
        }
        check(!inks.isEmpty(), "the sheet has ink");
        // The authoring palette has six entries; a little slack allows a future seventh without making
        // this assertion vacuous. A soft-edged sheet lands in the hundreds.
        check(inks.size() <= 8, "the sheet uses a small fixed palette, found " + inks.size()
                + " distinct colours (soft edges mean the art was resampled)");
        // Fully transparent and fully opaque only: no half-alpha pixels, which are the other signature
        // of resampling.
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                check(alpha == 0 || alpha == 255, "no partial alpha at " + x + "," + y + " (was " + alpha + ")");
            }
        }
        checks++;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
