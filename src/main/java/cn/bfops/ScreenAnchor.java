package cn.bfops;

import java.util.List;

/**
 * The reference's nine screen anchors and the offset maths that goes with them.
 *
 * <p><b>Where the anchor point is.</b> Each anchor is a {@code (xFraction, yFraction)} of the framebuffer,
 * so {@code top_left} is {@code (0,0)} and {@code bottom_center} is {@code (0.5, 1)}. The anchor point is the
 * <em>pivot</em>: it is where an element is centred and what it rotates about.
 *
 * <p><b>{@code y_offset} is subtracted, not added.</b> This is the single most common way to get this port
 * wrong. The lookup table uses top-down screen coordinates, but a user thinks of the offset as "how far up
 * from the bottom edge". So {@code resolveCenterY} computes {@code anchorY - yOffset}: a positive offset
 * moves the element <em>up</em>. Implementing it as addition puts every configured HUD element on the wrong
 * side of its anchor by twice the offset.
 *
 * <p><b>Rebasing.</b> {@link #translateXOffset} / {@link #translateYOffset} convert an offset expressed
 * against one anchor into the equivalent offset for another, so changing an element's anchor in the config
 * UI keeps it where the user put it instead of teleporting it to the mirrored corner.
 */
final class ScreenAnchor {
    /** The anchor used whenever a config value is missing or unrecognised. */
    static final String DEFAULT = "bottom_center";

    /** Anchor ids in the order the config UI offers them: a 3x3 grid, read left to right, top to bottom. */
    static final List<String> ANCHOR_IDS = List.of(
            "top_left", "top_center", "top_right",
            "middle_left", "center", "middle_right",
            "bottom_left", "bottom_center", "bottom_right");

    private ScreenAnchor() { }

    /** Whether {@code anchor} is one of the nine known ids. */
    static boolean isValid(String anchor) {
        return anchor != null && ANCHOR_IDS.contains(anchor);
    }

    /** The nine anchor ids. */
    static List<String> ids() {
        return ANCHOR_IDS;
    }

    /** {@code anchor}, or {@link #DEFAULT} when it is missing or unknown. */
    static String resolveOrDefault(String anchor) {
        return isValid(anchor) ? anchor : DEFAULT;
    }

    /** The anchor's horizontal fraction of the screen: 0, 0.5, or 1. */
    static float fractionX(String anchor) {
        String id = resolveOrDefault(anchor);
        if (id.endsWith("_left")) return 0f;
        if (id.endsWith("_right")) return 1f;
        return 0.5f;
    }

    /** The anchor's vertical fraction of the screen: 0, 0.5, or 1. */
    static float fractionY(String anchor) {
        String id = resolveOrDefault(anchor);
        if (id.startsWith("top_")) return 0f;
        if (id.startsWith("bottom_")) return 1f;
        return 0.5f;
    }

    /** The anchor point's x in pixels. */
    static float resolveAnchorX(String anchor, int screenWidth) {
        return fractionX(anchor) * (float) screenWidth;
    }

    /** The anchor point's y in pixels. */
    static float resolveAnchorY(String anchor, int screenHeight) {
        return fractionY(anchor) * (float) screenHeight;
    }

    /** {@code anchor}, or {@link #DEFAULT} when it is missing or unknown. */
    static String orDefault(String anchor) {
        return resolveOrDefault(anchor);
    }

    /** The anchor ids, for the config UI's anchor picker. */
    static List<String> getAnchorIds() {
        return ANCHOR_IDS;
    }

    /** The element's centre x: the anchor plus the horizontal offset. */
    static int resolveCenterX(String anchor, int xOffset, int screenWidth) {
        return Math.round(resolveAnchorX(anchor, screenWidth) + (float) xOffset);
    }

    /** Alias for {@link #resolveCenterX}, used by the older call sites. */
    static int centerX(String anchor, int xOffset, int screenWidth) {
        return resolveCenterX(anchor, xOffset, screenWidth);
    }

    /**
     * The element's centre y: the anchor <em>minus</em> the vertical offset.
     *
     * <p>Subtraction is deliberate — see the class comment. A positive {@code yOffset} moves the element up.
     */
    static int resolveCenterY(String anchor, int yOffset, int screenHeight) {
        return Math.round(resolveAnchorY(anchor, screenHeight) - (float) yOffset);
    }

    /** Alias for {@link #resolveCenterY}, used by the older call sites. */
    static int centerY(String anchor, int yOffset, int screenHeight) {
        return resolveCenterY(anchor, yOffset, screenHeight);
    }

    /** Converts an x offset measured against {@code fromAnchor} into one measured against {@code toAnchor}. */
    static int translateXOffset(String fromAnchor, String toAnchor, int xOffset, int screenWidth) {
        float from = resolveAnchorX(fromAnchor, screenWidth);
        float to = resolveAnchorX(toAnchor, screenWidth);
        return Math.round((float) xOffset + (from - to));
    }

    /** Converts a y offset measured against {@code fromAnchor} into one measured against {@code toAnchor}. */
    static int translateYOffset(String fromAnchor, String toAnchor, int yOffset, int screenHeight) {
        float from = resolveAnchorY(fromAnchor, screenHeight);
        float to = resolveAnchorY(toAnchor, screenHeight);
        // Note the direction: the offset is subtracted at resolve time, so rebasing adds the difference.
        return Math.round((float) yOffset + (to - from));
    }
}
