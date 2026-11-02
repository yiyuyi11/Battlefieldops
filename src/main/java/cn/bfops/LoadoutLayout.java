package cn.bfops;

import java.util.List;

/**
 * Geometry for the loadout screen's card layout, taken from the reference pack's constants.
 *
 * <p>The reference {@code LoadoutConfigScreen} lays out a left class column, a right panel, and — inside
 * that panel — an overview strip plus two weapon cards, each with a two-column grid of attachment buttons.
 * Those positions were previously hand-written into the render code as magic numbers; extracted here so the
 * one question the handover document calls out — "the constants are design evidence, don't copy them
 * unscaled" — has a single, tested answer: the left column is clamped, the cards are a fixed height, and the
 * attachment buttons tile the remaining width in two columns.
 *
 * <p>All values are in GUI (scaled) pixels, which are already resolution- and GUI-scale-normalised, so the
 * reference constants apply directly rather than needing a per-resolution multiplier. The only scaling that
 * matters is the left-column clamp, which reserves a fixed 360 pixels for the right content and lets the
 * left column take the rest, clamped to {@code [128, 170]}.
 */
final class LoadoutLayout {
    static final int LEFT = 8;
    static final int RIGHT = 8;
    static final int PANEL_GAP = 12;
    static final int LEFT_WIDTH_MIN = 128;
    static final int LEFT_WIDTH_MAX = 170;
    /** Width reserved for the right panel's content when deciding how wide the left column may be. */
    static final int FIXED_RIGHT_CONTENT = 360;

    static final int INFO_H = 60;
    static final int CARD_H = 132;
    static final int CARD_GAP = 12;
    static final int CARD_BUTTON_GAP = 8;
    static final int CARD_BUTTON_H = 18;

    static final int VEHICLE_ROW_H = 46;
    static final int VEHICLE_ROW_GAP = 6;
    static final int VEHICLE_BUTTON_W = 86;

    /** The attachment slot order on an infantry weapon card: 6 slots, 2 columns × 3 rows. */
    static final List<String> ATTACHMENT_ORDER =
            List.of("scope", "muzzle", "stock", "grip", "laser", "extended_mag");

    private LoadoutLayout() { }

    /** Left column width: what remains after reserving the right panel, clamped to {@code [128, 170]}. */
    static int leftColumnWidth(int screenWidth) {
        int remaining = screenWidth - LEFT - RIGHT - PANEL_GAP - FIXED_RIGHT_CONTENT;
        return Math.max(LEFT_WIDTH_MIN, Math.min(LEFT_WIDTH_MAX, remaining));
    }

    /** Left edge of the right panel: left column plus its right edge padding plus the panel gap. */
    static int rightPanelLeft(int screenWidth) {
        return LEFT + leftColumnWidth(screenWidth) + PANEL_GAP;
    }

    static int rightPanelRight(int screenWidth) {
        return screenWidth - RIGHT;
    }

    /** Usable width inside a card, never below a 40-pixel floor. */
    static int cardInnerWidth(int screenWidth) {
        return Math.max(40, rightPanelRight(screenWidth) - rightPanelLeft(screenWidth) - 16);
    }

    /** Total scrollable height of the home tab's right pane: overview + two cards + gaps. */
    static int contentHeight() {
        return INFO_H + CARD_GAP + CARD_H + CARD_GAP + CARD_H;
    }

    /** Top of the primary weapon card, relative to the overview top (the caller applies the scroll offset). */
    static int primaryCardTop() {
        return INFO_H + CARD_GAP;
    }

    /** Top of the secondary weapon card, relative to the overview top. */
    static int secondaryCardTop() {
        return primaryCardTop() + CARD_H + CARD_GAP;
    }

    /** Width of one attachment button: half the card width (minus the gap), never below 74. */
    static int attachmentButtonWidth(int screenWidth) {
        return Math.max(74, (cardInnerWidth(screenWidth) - CARD_BUTTON_GAP) / 2);
    }

    /** X of an attachment button column (0 or 1), absolute screen coordinate. */
    static int attachmentButtonX(int screenWidth, int column) {
        return rightPanelLeft(screenWidth) + LEFT + column * (attachmentButtonWidth(screenWidth) + CARD_BUTTON_GAP);
    }

    /** Y of an attachment button row (0..2), relative to the card top. */
    static int attachmentButtonY(int row) {
        return 42 + row * 24;
    }
}
