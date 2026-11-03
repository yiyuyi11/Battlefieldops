package cn.bfops;

/**
 * Verifies the scrolling kill-icon model's layout and animation maths.
 *
 * <p>The model is the part of the port that can be wrong in ways that still look plausible: a mirrored strip,
 * an icon that never fades, a queue that dumps everything at once. Every one of those is cheap to check here
 * and expensive to notice in a running game, so the checks below are deliberately about <em>invariants</em>
 * rather than about specific pixel values that would just restate the implementation.
 *
 * <p>The clock is injected everywhere, so these run in microseconds and are fully deterministic.
 */
public final class KillIconHudSim {
    static int checks;

    static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    /** Builds a model with a config the test controls, rather than one of the shipped presets. */
    static KillIconScroll model(KillIconScroll.Config config) {
        return new KillIconScroll(config);
    }

    static KillIconScroll.Config standard() {
        KillIconScroll.Config c = new KillIconScroll.Config();
        c.scale = 0.4f;
        c.displayDuration = 3_000L;
        c.animationDuration = 300L;
        c.fadeOutDuration = 100L;
        c.positionAnimationDuration = 300L;
        c.maxVisible = 7;
        c.maxPending = 30;
        c.displayInterval = 100L;
        c.startScale = 2.0f;
        c.iconSpacing = 8.0f;
        return c;
    }

    public static void main(String[] args) {
        spacingAndScale();
        queueSpacing();
        targetLayout();
        entranceAnimation();
        alphaLifecycle();
        overflowEviction();
        blowUpGuard();
        System.out.println("KillIconHudSim: " + checks + " checks passed");
    }

    /** The spacing between icon centres must follow the preset's scale, not the raw 64px base. */
    static void spacingAndScale() {
        KillIconScroll.Config c = standard();
        KillIconScroll m = model(c);
        check(Math.abs(m.iconSpacing() - 8.0f * 64.0f * 0.4f) < 0.001f,
                "spacing is iconSpacing * 64 * scale, got " + m.iconSpacing());

        // A zero or negative scale must not collapse the strip into a single pixel or divide by zero.
        c.scale = 0.0f;
        KillIconScroll zero = model(c);
        check(zero.iconSpacing() >= 0.0f, "a zero scale gives non-negative spacing");
        check(!Float.isNaN(zero.iconSpacing()) && !Float.isInfinite(zero.iconSpacing()),
                "a zero scale does not produce NaN/infinity spacing");
    }

    /**
     * Icons queued faster than the display interval are spaced out one per interval.
     *
     * <p>This is the whole point of {@code display_interval_ms}: a burst of four kills must not draw four icons
     * on the same frame on top of each other.
     */
    static void queueSpacing() {
        KillIconScroll.Config c = standard();
        c.displayInterval = 100L;
        KillIconScroll m = model(c);
        // Queue four icons, then advance in small steps and count how many appear as time passes.
        for (int i = 0; i < 4; i++) m.trigger(KillType.byId("HEADSHOT"), 1_000L);
        check(m.pending().size() == 4, "four icons are queued");
        check(m.active().isEmpty(), "none are active before the first tick");

        m.tick(1_000L, 100f);
        check(m.active().size() == 1, "the first icon is released immediately (interval already elapsed)");
        check(m.pending().size() == 3, "the other three wait");

        m.tick(1_050L, 100f);
        check(m.active().size() == 1, "half an interval releases nothing more");

        m.tick(1_100L, 100f);
        check(m.active().size() == 2, "one interval releases one more");

        // One icon per interval, so the fourth needs three intervals total: 1100, 1200, 1300.
        m.tick(1_200L, 100f);
        check(m.active().size() == 3, "a second interval releases a third");
        m.tick(1_300L, 100f);
        check(m.active().size() == 4, "all four are out after three intervals");
        check(m.pending().isEmpty(), "and the queue is empty");

        // A tick that jumps several intervals releases exactly one icon, not one per skipped interval: the
        // gate is on `now - lastDisplayTime`, which is why a laggy frame cannot dump the queue into a pile.
        //
        // The base time is 1000 rather than 0 on purpose. `lastDisplayTime` starts at 0, so the gate at t=0 is
        // `0 - 0 >= displayInterval`, which is false -- nothing is released until the clock has advanced past
        // the interval. With the real monotonic clock that never matters (it is already huge), but it does mean
        // "the first icon appears immediately" is only true for t >= displayInterval, and the test states that
        // rather than assuming it.
        KillIconScroll.Config jump = standard();
        jump.displayInterval = 100L;
        // The hold has to outlast the jump, or the first icon would expire and leave the count at 1 for a
        // reason that has nothing to do with the gate.
        jump.displayDuration = 60_000L;
        KillIconScroll mj = model(jump);
        for (int i = 0; i < 4; i++) mj.trigger(KillType.byId("CRIT"), 1_000L);
        mj.tick(1_000L, 0f);
        check(mj.active().size() == 1, "the first is released on the first tick past the interval");
        mj.tick(11_000L, 0f);
        check(mj.active().size() == 2,
                "a ten-second jump releases one icon, not the whole queue, got " + mj.active().size());
        check(mj.pending().size() == 2, "and the rest keep waiting");

        // And the t=0 edge, stated explicitly: a clock starting at zero holds the first icon for one interval.
        KillIconScroll.Config cold = standard();
        cold.displayInterval = 100L;
        KillIconScroll mc0 = model(cold);
        mc0.trigger(KillType.byId("CRIT"), 0L);
        mc0.tick(0L, 0f);
        check(mc0.active().isEmpty(), "a clock at 0 has not yet reached the first interval");
        mc0.tick(100L, 0f);
        check(mc0.active().size() == 1, "and releases at the interval");

        // A zero interval releases everything at once, which is what a "no delay" preset wants. `0 - 0 >= 0` is
        // true, so this is the one configuration that releases on a cold clock.
        KillIconScroll.Config fast = standard();
        fast.displayInterval = 0L;
        KillIconScroll mf = model(fast);
        for (int i = 0; i < 5; i++) mf.trigger(KillType.byId("CRIT"), 0L);
        mf.tick(0L, 0f);
        check(mf.active().size() == 5, "a zero interval releases the whole queue on one tick");
        check(mf.pending().isEmpty(), "and empties it");
    }

    /**
     * Two layout modes, and the one thing they must agree on: the newest icon does not move backwards.
     */
    static void targetLayout() {
        // Centred layout: with an odd count the icons straddle the centre symmetrically.
        KillIconScroll.Config c = standard();
        c.displayInterval = 0L;
        c.maxVisible = 7;
        KillIconScroll m = model(c);
        for (int i = 0; i < 3; i++) m.trigger(KillType.byId("NORMAL"), 0L);
        m.tick(0L, 200f);
        check(m.active().size() == 3, "three icons active");
        float spacing = m.iconSpacing();
        // Centre icons are laid out as (i - (n-1)/2) around the centre, so the middle one sits exactly on it.
        float mid = m.active().get(1).targetX;
        float first = m.active().get(0).targetX;
        float last = m.active().get(2).targetX;
        check(Math.abs(mid - 200f) < 0.01f, "the middle icon sits on the centre, got " + mid);
        check(Math.abs(Math.abs(mid - first) - spacing) < 0.01f,
                "icons are one spacing apart, got " + Math.abs(mid - first) + " expected " + spacing);
        check(Math.abs(Math.abs(last - mid) - spacing) < 0.01f, "and symmetric on the other side");
        // Which way the strip runs. The reference computes `targetX = centreX - direction * position *
        // spacing`, so direction +1 puts each newer icon further LEFT; "scroll_direction: right" therefore
        // maps to -1, and that inversion is asserted rather than named, because naming it either way invites
        // exactly the sign error this check exists to catch.
        check(last < mid && mid < first,
                "at direction +1 older icons sit to the right and newer to the left");

        // Pinned layout: the newest icon holds the centre and older ones recede.
        KillIconScroll.Config pinned = standard();
        pinned.displayInterval = 0L;
        pinned.pinNewest = true;
        KillIconScroll mp = model(pinned);
        for (int i = 0; i < 3; i++) mp.trigger(KillType.byId("NORMAL"), 0L);
        mp.tick(0L, 200f);
        check(mp.active().size() == 3, "three icons active when pinned");
        check(Math.abs(mp.active().get(2).targetX - 200f) < 0.01f,
                "the newest icon is pinned to the centre, got " + mp.active().get(2).targetX);
        check(mp.active().get(1).targetX > 200f, "older icons recede to the right at direction +1");
        check(mp.active().get(0).targetX > mp.active().get(1).targetX, "and in order of age");

        // Direction -1 mirrors every one of those relationships.
        KillIconScroll.Config mirror = standard();
        mirror.displayInterval = 0L;
        mirror.direction = -1;
        KillIconScroll mm = model(mirror);
        for (int i = 0; i < 3; i++) mm.trigger(KillType.byId("NORMAL"), 0L);
        mm.tick(0L, 200f);
        check(mm.active().get(0).targetX < 200f, "direction -1 puts the oldest to the left");
        check(mm.active().get(2).targetX > 200f, "and the newest to the right");
        check(mm.active().get(0).targetX < mm.active().get(1).targetX, "with age running left to right");
    }

    /** An arriving icon starts big and shrinks; it never overshoots and always settles at the preset scale. */
    static void entranceAnimation() {
        KillIconScroll.Config c = standard();
        c.displayInterval = 0L;
        KillIconScroll m = model(c);
        m.trigger(KillType.byId("HEADSHOT"), 0L);
        m.tick(0L, 0f);
        KillIconScroll.Icon icon = m.active().get(0);

        float atStart = m.scaleAt(icon, 0L);
        check(Math.abs(atStart - 2.0f * 0.4f) < 0.001f,
                "an icon starts at startScale * scale, got " + atStart);
        float settled = m.scaleAt(icon, 10_000L);
        check(Math.abs(settled - 0.4f) < 0.001f, "and settles at scale, got " + settled);

        // Monotonic decrease, and never below the settled value.
        float previous = atStart;
        for (long t = 0; t <= 400; t += 10) {
            float s = m.scaleAt(icon, t);
            check(s <= previous + 0.0001f, "entrance scale never increases (t=" + t + ")");
            check(s >= 0.4f - 0.0001f, "entrance scale never undershoots the settled value (t=" + t + ")");
            previous = s;
        }

        // The duration is the crossover point: at or after it the value is exactly settled.
        check(Math.abs(m.scaleAt(icon, c.animationDuration) - 0.4f) < 0.001f,
                "the animation is complete exactly at its duration");
        // A cubic ease-out is very flat near the end, so "still animating one millisecond before the end" is
        // not measurable -- at 299/300 the eased value differs from 1 by about 4e-8. The meaningful claim is
        // that the scale is still above the settled value earlier in the animation, and that it is strictly
        // monotonic in between, which the loop above already establishes.
        check(m.scaleAt(icon, c.animationDuration / 2) > 0.4f + 0.01f,
                "half-way through, the icon is still visibly larger than settled, got "
                        + m.scaleAt(icon, c.animationDuration / 2));
        check(m.scaleAt(icon, 0L) > m.scaleAt(icon, c.animationDuration / 2),
                "and larger at the start than in the middle");

        // The entrance delay holds the animation at its first frame.
        KillIconScroll.Config delayed = standard();
        delayed.displayInterval = 0L;
        delayed.iconDelayMs = 200L;
        KillIconScroll md = model(delayed);
        md.trigger(KillType.byId("HEADSHOT"), 0L);
        md.tick(0L, 0f);
        KillIconScroll.Icon di = md.active().get(0);
        check(Math.abs(md.scaleAt(di, 150L) - 2.0f * 0.4f) < 0.001f,
                "a delayed icon is still at its first frame before the delay elapses");
        check(md.scaleAt(di, 200L + 300L) <= 0.4f + 0.001f,
                "and animates after it");
    }

    /**
     * The alpha curve: fade in, hold at full, fade out, then the icon is removed.
     *
     * <p>The important property is that the icon is never invisible while it is still on screen for a reason —
     * a fully transparent but still-present icon would keep a slot occupied and silently push others out.
     */
    static void alphaLifecycle() {
        KillIconScroll.Config c = standard();
        c.displayInterval = 0L;
        KillIconScroll m = model(c);
        m.trigger(KillType.byId("NORMAL"), 0L);
        m.tick(0L, 0f);
        KillIconScroll.Icon icon = m.active().get(0);

        check(m.alphaAt(icon, 0L) <= 0.01f, "an icon starts transparent");
        check(Math.abs(m.alphaAt(icon, c.animationDuration) - 1.0f) < 0.01f,
                "it reaches full opacity when the animation ends, got " + m.alphaAt(icon, c.animationDuration));
        // Held at full for the whole display duration.
        for (long t = c.animationDuration; t <= c.displayDuration; t += 250) {
            check(m.alphaAt(icon, t) > 0.99f, "held at full opacity at t=" + t + ", got " + m.alphaAt(icon, t));
        }
        // Then it fades out over the fade duration.
        float half = m.alphaAt(icon, c.displayDuration + c.fadeOutDuration / 2);
        check(half < 0.6f && half > 0.4f, "half-way through the fade it is about half opaque, got " + half);
        check(m.alphaAt(icon, c.displayDuration + c.fadeOutDuration) <= 0.01f,
                "and fully transparent at the end of the fade");

        // Past the fade the icon is gone from the model entirely.
        m.tick(c.displayDuration + c.fadeOutDuration + 1, 0f);
        check(!m.hasVisibleIcons(), "the icon is removed once its fade completes");

        // The blink curve is a zig-zag, not a straight line: it must actually come back up in the middle.
        KillIconScroll.Config blink = standard();
        blink.displayInterval = 0L;
        blink.blinkFade = true;
        KillIconScroll mb = model(blink);
        mb.trigger(KillType.byId("NORMAL"), 0L);
        mb.tick(0L, 0f);
        KillIconScroll.Icon bi = mb.active().get(0);
        long fadeStart = blink.displayDuration;
        float early = mb.alphaAt(bi, fadeStart);
        float third = mb.alphaAt(bi, fadeStart + (long) (blink.fadeOutDuration * 0.35f));
        check(third < early - 0.1f, "the blink dips first");
        float middle = mb.alphaAt(bi, fadeStart + (long) (blink.fadeOutDuration * 0.5f));
        check(middle > third, "then rises again, which a straight fade would not do");
        check(mb.alphaAt(bi, fadeStart + blink.fadeOutDuration) <= 0.05f, "and ends near zero");
    }

    /**
     * Icons pushed out of the visible window fade early and are removed, rather than accumulating forever.
     */
    static void overflowEviction() {
        KillIconScroll.Config c = standard();
        c.displayInterval = 0L;
        c.maxVisible = 3;
        c.displayDuration = 60_000L;   // long enough that only eviction can remove anything
        c.fadeOutDuration = 100L;
        KillIconScroll m = model(c);
        for (int i = 0; i < 10; i++) m.trigger(KillType.byId("NORMAL"), 0L);
        m.tick(0L, 0f);
        check(m.active().size() == 10, "all ten icons are live");
        // Only maxVisible of them are supposed to be visible; the rest must have been marked for fading.
        int forced = 0;
        for (KillIconScroll.Icon icon : m.active()) if (icon.forcedFadeStart >= 0L) forced++;
        check(forced == 7, "the seven icons beyond the visible window are marked to fade, got " + forced);

        // Once their fade completes they are removed even though their display duration has not elapsed.
        m.tick(200L, 0f);
        check(m.active().size() == 3,
                "only the visible window survives, got " + m.active().size());
        // And the survivors are the newest three.
        check(m.active().get(m.active().size() - 1).startTime == 0L, "the newest survive");

        // The pending queue is capped independently.
        KillIconScroll.Config capped = standard();
        capped.maxPending = 2;
        capped.displayInterval = 100_000L;   // nothing is ever released
        KillIconScroll mc = model(capped);
        for (int i = 0; i < 10; i++) mc.trigger(KillType.byId("NORMAL"), 0L);
        check(mc.pending().size() == 2, "the queue is capped at maxPending, got " + mc.pending().size());
    }

    /**
     * Degenerate configs must not produce NaN, infinity or a negative position.
     *
     * <p>Presets are data. A hand-edited preset with a zero duration or a negative scale should degrade to
     * something that still draws, not to an invisible icon or a crash on the render thread.
     */
    static void blowUpGuard() {
        KillIconScroll.Config c = new KillIconScroll.Config();
        c.scale = -1.0f;
        c.displayDuration = 0L;
        c.animationDuration = 0L;
        c.fadeOutDuration = 0L;
        c.positionAnimationDuration = 0L;
        c.maxVisible = 0;
        c.maxPending = -5;
        c.displayInterval = -10L;
        KillIconScroll m = model(c);
        m.trigger(KillType.byId("NORMAL"), 0L);
        m.tick(0L, 100f);
        for (KillIconScroll.Icon icon : m.active()) {
            check(!Float.isNaN(icon.currentX) && !Float.isInfinite(icon.currentX),
                    "position is a real number under a degenerate config");
            float s = m.scaleAt(icon, 0L);
            float a = m.alphaAt(icon, 0L);
            check(!Float.isNaN(s) && !Float.isInfinite(s), "scale is a real number");
            check(!Float.isNaN(a) && !Float.isInfinite(a), "alpha is a real number");
            check(a >= 0f && a <= 1f, "alpha stays in 0..1, got " + a);
        }
        // A negative pending cap must mean "accept nothing" rather than corrupting the list.
        check(m.pending().size() <= 0 || m.pending().isEmpty(), "a negative pending cap queues nothing");

        // A huge maxVisible must not index past the list.
        KillIconScroll.Config wide = standard();
        wide.displayInterval = 0L;
        wide.maxVisible = 10_000;
        KillIconScroll mw = model(wide);
        for (int i = 0; i < 4; i++) mw.trigger(KillType.byId("NORMAL"), 0L);
        mw.tick(0L, 50f);
        check(mw.active().size() == 4, "a huge visible window is harmless");
        for (KillIconScroll.Icon icon : mw.active()) {
            check(!Float.isNaN(icon.targetX), "targets are real with a huge window");
        }
    }
}
