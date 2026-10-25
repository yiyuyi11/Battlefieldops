package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * The floating "+100 击杀" award feed, modelled on the Battlefield-style popup the reference pack uses.
 *
 * <p><b>Why this is reimplemented rather than ported.</b> The pack's own mod for this is
 * {@code GD656Killicon}, a Forge 1.20.1 mod with its own network layer, mixins and a full configuration GUI.
 * It cannot load on 1.21.1 NeoForge at all, so installing it is not an option, and copying another author's
 * implementation is not something to do. What is reproduced here is the <em>behaviour</em>: an award appears,
 * rises, fades, and stacks so several awards read as a list rather than overlapping.
 *
 * <p><b>Why the arithmetic is separate.</b> Timing is the whole feature: too fast and the player never reads
 * it, too slow and the feed becomes clutter over the crosshair. Keeping the curve here means it can be checked
 * without launching the game.
 */
final class Feed {
    /** How long one award stays on screen. */
    static final long LIFETIME_MS = 2_600L;
    /** How far an award rises over its lifetime, in pixels. */
    static final double RISE_PIXELS = 22;
    /** Vertical spacing between stacked awards. */
    static final double LINE_HEIGHT = 11;
    /** Hard cap, so a multi-kill burst cannot fill the screen. */
    static final int MAX_ENTRIES = 5;
    /** Portion of the lifetime spent fading out, at the end. */
    static final double FADE_TAIL = 0.35;

    /**
     * One award.
     *
     * @param label  what was earned, e.g. {@code 击杀}
     * @param points score added, e.g. {@code 100}
     * @param bornAt wall-clock milliseconds
     */
    record Popup(String label, int points, long bornAt, String detail, String icon) {
        Popup(String label, int points, long bornAt) {
            this(label, points, bornAt, "", "kill");
        }

        /** Full text, so the whole feature has one formatting rule. */
        String text() {
            return format(points, label);
        }

        /** The victim or object the award is about, or an empty string when there is none. */
        String detailText() {
            return detail == null ? "" : detail;
        }
    }

    private Feed() { }

    /** "+100 击杀" for a positive award, "击杀" alone when the award carries no points. */
    static String format(int points, String label) {
        String name = label == null || label.isBlank() ? "奖励" : label;
        return points > 0 ? "+" + points + " " + name : name;
    }

    /**
     * Appends an award, keeping the newest {@link #MAX_ENTRIES}.
     *
     * <p>Newest-first, because the item the player most wants to read is the one that just happened.
     */
    static List<Popup> add(List<Popup> feed, Popup popup) {
        List<Popup> out = new ArrayList<>();
        if (popup != null) out.add(popup);
        if (feed != null) {
            for (Popup existing : feed) {
                if (out.size() >= MAX_ENTRIES) break;
                if (existing != null) out.add(existing);
            }
        }
        return out;
    }

    /** Drops finished awards. */
    static List<Popup> prune(List<Popup> feed, long now) {
        List<Popup> out = new ArrayList<>();
        if (feed == null) return out;
        for (Popup popup : feed) {
            if (popup != null && now - popup.bornAt() < LIFETIME_MS) out.add(popup);
        }
        return out;
    }

    /**
     * 0..1 progress through the award's life.
     *
     * <p>Clamped rather than wrapped: a clock that jumps (a lag spike, or a paused window) must not make an
     * old award spring back to full opacity.
     */
    static double progress(Popup popup, long now) {
        if (popup == null) return 1;
        double value = (now - popup.bornAt()) / (double) LIFETIME_MS;
        return Math.max(0, Math.min(1, value));
    }

    /**
     * Opacity multiplier: fully visible until the tail, then linear to zero.
     *
     * <p>A fade over the whole life makes the text unreadable for most of the time it is up, so the award stays
     * solid and only fades at the very end.
     */
    static double alpha(Popup popup, long now) {
        double progress = progress(popup, now);
        double fadeStart = 1 - FADE_TAIL;
        if (progress <= fadeStart) return 1;
        return Math.max(0, (1 - progress) / FADE_TAIL);
    }

    /**
     * Vertical offset for an award: it rises as it ages, and stacked awards sit above the newest.
     *
     * @param index 0 is the newest award
     */
    static int offsetY(Popup popup, long now, int index) {
        double rise = -RISE_PIXELS * progress(popup, now);
        return (int) Math.round(rise + index * LINE_HEIGHT);
    }
}
