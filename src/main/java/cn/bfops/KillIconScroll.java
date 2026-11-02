package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code scrolling} kill-icon mode: a strip of icons that slide sideways as new ones arrive.
 *
 * <p>Ported from the reference's {@code ScrollingIconRenderer}. What is reproduced here is the <em>model</em>
 * — which icons exist, where each one sits, how big it is and how opaque — and not the drawing. Splitting it
 * that way is deliberate: the layout arithmetic is the part with the edge cases (a full strip, an icon being
 * pushed off the visible window, the forced fade of an overflowing icon, the blink fade), and it is the part
 * that can be verified to the pixel without a running game. {@link KillIconHud} does the blitting.
 *
 * <p><b>The two behaviours that are easy to get backwards.</b>
 *
 * <ul>
 *   <li><b>{@code pinNewestIcon}</b>: when true the newest icon stays at the centre and older icons march
 *       away from it; when false the whole strip re-centres, so every icon shifts by half a slot as the count
 *       changes. Both are legitimate preset choices and the reference ships both, so the flag is honoured
 *       exactly.</li>
 *   <li><b>{@code scrollDirection}</b>: {@code +1} puts newer icons to the right, {@code -1} to the left. The
 *       reference stores this as an int and multiplies it into every position, which is why it appears in
 *       three places below rather than being normalised once.</li>
 * </ul>
 *
 * <p><b>Timing is in milliseconds and monotonic.</b> Every duration in a preset is in ms and every comparison
 * is on "now minus start", so the model works identically against the real clock in game and against an
 * injected clock in the offline tests. Nothing here reads {@code System.currentTimeMillis}.
 */
final class KillIconScroll {

    // Defaults, matching the reference's own constants. They are used when a preset omits a key rather than
    // when it is malformed: a preset that names a key always wins.
    static final long DEFAULT_DISPLAY_DURATION = 3_000L;
    static final long DEFAULT_ANIMATION_DURATION = 300L;
    static final long DEFAULT_FADE_OUT_DURATION = 100L;
    static final long DEFAULT_POSITION_ANIMATION_DURATION = 300L;
    static final float DEFAULT_START_SCALE = 2.0f;
    static final float BASE_ICON_SIZE = 64.0f;
    static final float DEFAULT_ICON_SPACING = 8.0f;
    static final int DEFAULT_MAX_VISIBLE = 7;
    static final int DEFAULT_MAX_PENDING = 30;
    static final long DEFAULT_DISPLAY_INTERVAL = 100L;

    /**
     * One icon in the strip.
     *
     * <p>Mutable on purpose: the reference mutates icons in place as they age, and a record-per-frame would
     * allocate seven small objects per frame per icon for no benefit. The fields are package-visible so the
     * renderer can read them without a getter for each.
     */
    static final class Icon {
        final KillType kind;
        long startTime;
        long displayDuration;
        /** Negative means "not forced to fade"; set when the icon is pushed out of the visible window. */
        long forcedFadeStart = -1L;
        float prevX;
        float currentX;
        float targetX;
        long positionAnimationStart;

        Icon(KillType kind, long startTime, long displayDuration) {
            this.kind = kind;
            this.startTime = startTime;
            this.displayDuration = displayDuration;
        }
    }

    /** A preset's scrolling configuration, already resolved against its defaults. */
    static final class Config {
        float scale = 1.0f;
        int xOffset;
        int yOffset;
        String anchor = ScreenAnchor.DEFAULT;
        int direction = 1;
        boolean pinNewest;
        boolean blinkFade;
        long iconDelayMs;
        long displayDuration = DEFAULT_DISPLAY_DURATION;
        long animationDuration = DEFAULT_ANIMATION_DURATION;
        long fadeOutDuration = DEFAULT_FADE_OUT_DURATION;
        long positionAnimationDuration = DEFAULT_POSITION_ANIMATION_DURATION;
        float startScale = DEFAULT_START_SCALE;
        float iconSpacing = DEFAULT_ICON_SPACING;
        int maxVisible = DEFAULT_MAX_VISIBLE;
        int maxPending = DEFAULT_MAX_PENDING;
        long displayInterval = DEFAULT_DISPLAY_INTERVAL;
    }

    private final Config config;
    private final List<Icon> active = new ArrayList<>();
    private final List<Icon> pending = new ArrayList<>();
    private long lastDisplayTime;

    KillIconScroll(Config config) {
        this.config = config;
    }

    KillIconScroll() {
        this(new Config());
    }

    Config config() {
        return config;
    }

    /** Icons currently on screen, oldest first. Exposed for tests and for the renderer's iteration. */
    List<Icon> active() {
        return active;
    }

    /** Icons waiting for their turn, oldest first. */
    List<Icon> pending() {
        return pending;
    }

    boolean hasVisibleIcons() {
        return !active.isEmpty() || !pending.isEmpty();
    }

    /**
     * Queues an award.
     *
     * <p>Bounded by {@code maxPending}: a preset that allows thirty queued icons and gets more will silently
     * drop the excess, exactly as the reference does. Dropping is the right call here because the queue exists
     * to space out icons that would otherwise overlap, and an unbounded queue turns a burst of kills into a
     * strip that keeps drawing for a minute after the fight ended.
     */
    void trigger(KillType kind, long now) {
        if (pending.size() >= Math.max(0, config.maxPending)) return;
        pending.add(new Icon(kind, now, config.displayDuration));
    }

    /**
     * Advances the model to {@code now}.
     *
     * <p>Order matters and matches the reference: release due pending icons first, then recompute every icon's
     * target slot, then ease current positions toward those targets, then drop expired icons. Recomputing
     * targets after releasing — rather than before — is what makes a newly released icon take its place in the
     * strip on the same frame instead of one frame late.
     *
     * @param centerX where the strip's centre sits, in GUI pixels
     */
    void tick(long now, float centerX) {
        processPending(now, centerX);
        updateAllTargets(now, centerX);
        for (Icon icon : active) updatePosition(icon, now);
        active.removeIf(icon -> shouldRemove(icon, now));
    }

    /**
     * Releases pending icons that are due.
     *
     * <p>The interval gate is {@code now - lastDisplayTime >= displayInterval}. When icons arrive faster than
     * {@link #DEFAULT_DISPLAY_INTERVAL} ms apart they are spaced out; when they arrive slower, each is released
     * on the tick after it was queued. A {@code displayInterval} of zero disables the spacing entirely and the
     * {@code while} releases everything at once, which is what a "no delay" preset wants.
     */
    private void processPending(long now, float centerX) {
        while (!pending.isEmpty() && now - lastDisplayTime >= config.displayInterval) {
            Icon next = pending.remove(0);
            next.startTime = now;
            lastDisplayTime = now;
            addIcon(next, now, centerX);
        }
    }

    private void addIcon(Icon icon, long now, float centerX) {
        active.add(icon);
        updateAllTargets(now, centerX);
        // A new icon starts at its target rather than sliding in from somewhere: the entrance animation is
        // scale and alpha, not position. The reference sets both to the target for exactly this reason.
        icon.prevX = icon.targetX;
        icon.currentX = icon.targetX;
        icon.positionAnimationStart = now;
    }

    /**
     * Recomputes every icon's target x.
     *
     * <p>Two layouts, as described on the class. In the pinned layout the newest icon holds the centre and the
     * others recede by index; in the centred layout the visible window is centred and the icons are spread
     * symmetrically around it. Either way, icons that no longer fit in {@code maxVisible} are pushed further
     * out and marked for a forced fade.
     */
    private void updateAllTargets(long now, float centerX) {
        if (active.isEmpty()) return;
        int direction = config.direction;
        float spacing = iconSpacing();
        int size = active.size();
        int visibleStart = Math.max(0, size - config.maxVisible);
        int visibleCount = size - visibleStart;

        if (config.pinNewest) {
            float newestX = centerX;
            int newestIndex = size - 1;
            for (int i = 0; i < visibleStart; i++) {
                Icon icon = active.get(i);
                float overflowX = newestX + (float) (direction * (newestIndex - i)) * spacing;
                updateTarget(icon, overflowX, now);
                if (icon.forcedFadeStart < 0L) icon.forcedFadeStart = now;
            }
            for (int i = visibleStart; i < size; i++) {
                Icon icon = active.get(i);
                float targetX = newestX + (float) (direction * (newestIndex - i)) * spacing;
                updateTarget(icon, targetX, now);
            }
        } else {
            float rightmostSlot = centerX + (float) direction * ((float) (visibleCount - 1) / 2.0f) * spacing;
            for (int i = 0; i < visibleStart; i++) {
                Icon icon = active.get(i);
                float overflowX = rightmostSlot + (float) (direction * (visibleStart - i)) * spacing;
                updateTarget(icon, overflowX, now);
                if (icon.forcedFadeStart < 0L) icon.forcedFadeStart = now;
            }
            for (int i = visibleStart; i < size; i++) {
                Icon icon = active.get(i);
                float position = (float) (i - visibleStart) - (float) (visibleCount - 1) / 2.0f;
                float targetX = centerX - (float) direction * position * spacing;
                updateTarget(icon, targetX, now);
            }
        }
    }

    /**
     * Retargets an icon, but only when the target actually moved.
     *
     * <p>The {@code > 0.1} threshold is the reference's, and it matters: without it, floating-point drift
     * between frames would restart the position animation every frame and the strip would never settle.
     */
    private void updateTarget(Icon icon, float newTargetX, long now) {
        if (Math.abs(icon.targetX - newTargetX) > 0.1f) {
            icon.prevX = icon.currentX;
            icon.targetX = newTargetX;
            icon.positionAnimationStart = now;
        }
    }

    /** The gap between icon centres, in GUI pixels: the configured spacing times the scaled base size. */
    float iconSpacing() {
        return config.iconSpacing * (BASE_ICON_SIZE * config.scale);
    }

    /** Eases an icon's x toward its target with a quadratic ease-out. */
    private void updatePosition(Icon icon, long now) {
        if (Math.abs(icon.currentX - icon.targetX) <= 0.1f) return;
        long elapsed = now - icon.positionAnimationStart;
        float progress = config.positionAnimationDuration <= 0L ? 1.0f
                : Math.min((float) elapsed / (float) config.positionAnimationDuration, 1.0f);
        float eased = 1.0f - (1.0f - progress) * (1.0f - progress);
        icon.currentX = icon.prevX + (icon.targetX - icon.prevX) * eased;
    }

    /**
     * The scale an icon should be drawn at, honouring the preset's entrance delay.
     *
     * <p>Cubic ease-out from {@code startScale} to 1, both multiplied by the preset's scale, then settling at
     * the final value for the rest of the icon's life. The overshoot-free easing is why an arriving icon
     * appears to slam in and stop rather than bounce.
     *
     * <p>{@code iconDelayMs} holds an icon at its first animation frame while the one before it finishes
     * sliding into place. The delay is applied to the <em>animation clock</em> rather than to the icon's start
     * time, which is what the reference does: delaying the start would push the icon's whole life later, while
     * delaying only the animation leaves its hold and fade timing untouched.
     */
    float scaleAt(Icon icon, long now) {
        float endScale = 1.0f * config.scale;
        long elapsed = Math.max(0L, now - icon.startTime - config.iconDelayMs);
        if (elapsed >= config.animationDuration) return endScale;
        float initialScale = config.startScale * config.scale;
        float progress = config.animationDuration <= 0L ? 1.0f
                : (float) elapsed / (float) config.animationDuration;
        progress = 1.0f - (float) Math.pow(1.0f - progress, 3.0);
        return initialScale + (endScale - initialScale) * progress;
    }

    /** {@link #scaleAt}, for callers that do not care about the distinction. */
    float scale(Icon icon, long now) {
        return scaleAt(icon, now);
    }

    /**
     * The alpha an icon should be drawn at, from fade-in, hold, and fade-out.
     *
     * <p>A forced fade (the icon was pushed out of the window) overrides the hold time entirely: it starts
     * fading at {@code forcedFadeStart} regardless of how long the icon still had left, which is what makes an
     * overflowing strip look like it is sliding away into the edge rather than abruptly vanishing.
     *
     * <p>The delay applies to the fade-in only. The hold and the fade-out are measured on the undelayed clock,
     * so a preset that sets both a delay and a duration does not show its icons for longer than it asked.
     */
    float alphaAt(Icon icon, long now) {
        long rawElapsed = now - icon.startTime;
        long elapsed = Math.max(0L, rawElapsed - config.iconDelayMs);
        long fadeInDuration = Math.max(1L, config.animationDuration);
        float fadeInProgress = Math.min((float) elapsed / (float) fadeInDuration, 1.0f);
        float easedIn = 1.0f - (float) Math.pow(1.0f - fadeInProgress, 3.0);
        float baseAlpha = clamp01(easedIn);
        if (icon.forcedFadeStart >= 0L) {
            float fadeProgress = (float) (now - icon.forcedFadeStart) / (float) config.fadeOutDuration;
            return clamp01(baseAlpha * fadeAlpha(fadeProgress));
        }
        if (rawElapsed <= icon.displayDuration) return baseAlpha;
        float fadeProgress = (float) (rawElapsed - icon.displayDuration) / (float) config.fadeOutDuration;
        return clamp01(baseAlpha * fadeAlpha(fadeProgress));
    }

    /** {@link #alphaAt}, for callers that do not care about the distinction. */
    float alpha(Icon icon, long now) {
        return alphaAt(icon, now);
    }

    /**
     * The fade curve.
     *
     * <p>{@code blinkFade} reproduces the reference's three-segment zig-zag: down to zero by a third, back up
     * to zero-crossing in the middle, then down again. It is a blink, not an easing, and the three constant
     * multipliers are the reference's own — reproducing them exactly is the only way a preset that enables it
     * looks the same.
     */
    private float fadeAlpha(float progress) {
        if (!config.blinkFade) return 1.0f - progress;
        if (progress < 0.33333334f) return 1.0f - 2.4f * progress;
        if (progress < 0.6666667f) return 2.4f * progress - 0.6f;
        return 3.0f * (1.0f - progress);
    }

    private boolean shouldRemove(Icon icon, long now) {
        if (icon.forcedFadeStart >= 0L) return now - icon.forcedFadeStart >= config.fadeOutDuration;
        return now - icon.startTime >= icon.displayDuration + config.fadeOutDuration;
    }

    /** The {@code display_interval_ms} gate's current phase, exposed so a test can prove spacing works. */
    long lastDisplayTime() {
        return lastDisplayTime;
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }
}
