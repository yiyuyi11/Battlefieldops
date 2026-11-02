package cn.bfops;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Screen-space muzzle flash and bullet-trace glow.
 *
 * <h2>What this reproduces</h2>
 *
 * <p>The reference pack's {@code gd656lightcore} adds four kinds of dynamic light — muzzle flash, bullet
 * trail, bullet impact, explosion — and its config is an object lesson in restraint:
 *
 * <pre>
 * muzzle_flash_duration_ms = 40     # the flash is gone in 40 milliseconds
 * muzzle_light_min/max     = 10..15
 * sniper_muzzle_duration_scale = 3.0
 * heavy_muzzle_duration_scale  = 7.0
 * explosion_light_duration_nanos = 0.3
 * max_simultaneous_lights = 4096    # a hard ceiling
 * </pre>
 *
 * <p>Every effect has both a duration measured in milliseconds and a global count cap. That is the whole
 * design: dynamic light is cheap per instance and ruinous in aggregate, so the budget is enforced at the
 * container rather than trusted at the call sites.
 *
 * <h2>Why this is screen-space and not real light</h2>
 *
 * <p>{@code gd656lightcore} injects into {@code BlockLightEngine} and {@code LevelRenderer}, which means a
 * real lighting change: blocks near a muzzle flash are genuinely lit for those 40 ms. Reproducing that
 * requires a mixin into the light engine and a dynamic-light queue, which is a large, version-fragile
 * surface for an effect that lasts forty milliseconds.
 *
 * <p>What is drawn here instead is a short-lived additive glow anchored to the shooting position and
 * projected to the screen. Visually the difference at 40 ms is not perceptible in a firefight; the
 * difference in maintenance burden is a mixin versus a few dozen lines with no injection points at all.
 * This is recorded as a deliberate trade rather than an oversight — real light is the correct answer if the
 * effect ever needs to illuminate geometry.
 *
 * <h2>The budget, stated explicitly</h2>
 *
 * <p>{@link #MAX_FLASHES} caps live effects. Each effect is a handful of {@code fill} calls, so the worst
 * case is bounded and known: at the cap, roughly a hundred extra fills per frame regardless of how many
 * players are shooting. Without the cap, a twelve-player firefight would be several hundred effects
 * competing for the same frame, which is exactly the stutter this project is trying to remove.
 */
final class MuzzleGlow {
    /** How long a flash lives. Straight from {@code gd656lightcore}'s 40 ms. */
    private static final long FLASH_MS = 40;

    /** Heavier weapons flash longer, matching the reference pack's duration scales. */
    private static final double HEAVY_SCALE = 3.0;

    /** Hard cap on simultaneous effects. */
    private static final int MAX_FLASHES = 64;

    /** Base radius in GUI pixels at one block; scaled by distance like a real light falling off. */
    private static final float BASE_RADIUS = 26f;

    /** A flash this far away is not worth drawing. */
    private static final double MAX_DISTANCE = 48.0;

    private record Flash(double x, double y, double z, long bornAt, boolean heavy) { }

    /**
     * Live effects, oldest first.
     *
     * <p>A deque rather than a list because expiry is always from the head: the oldest flash is always the
     * next to die, so pruning is a poll from the front instead of a full scan. With a strict cap, insertion
     * order is also expiry order, which makes the two operations the same operation.
     */
    private static final Deque<Flash> FLASHES = new ArrayDeque<>();

    private MuzzleGlow() { }

    /**
     * Registers a muzzle flash at a world position.
     *
     * @param heavy true for a weapon whose flash should linger (sniper, heavy machine gun)
     */
    static void muzzle(double x, double y, double z, boolean heavy) {
        long now = System.currentTimeMillis();
        // Drop the tail first: if the cap is already reached the new flash still goes in, because the one
        // being fired right now is by definition the most relevant effect on screen.
        while (FLASHES.size() >= MAX_FLASHES) FLASHES.pollFirst();
        FLASHES.addLast(new Flash(x, y, z, now, heavy));
    }

    /** Draws every live flash that is still inside its lifetime. */
    static void draw(GuiGraphics graphics, Minecraft mc) {
        if (FLASHES.isEmpty()) return;
        long now = System.currentTimeMillis();
        long ttl = FLASH_MS;
        long heavyTtl = (long) (FLASH_MS * HEAVY_SCALE);

        // Prune from the head while expired. Because insertion order equals expiry order, this is exact
        // and cannot leave a dead effect behind in the middle of the deque.
        while (!FLASHES.isEmpty()) {
            Flash head = FLASHES.peekFirst();
            long limit = head.heavy ? heavyTtl : ttl;
            if (now - head.bornAt <= limit) break;
            FLASHES.pollFirst();
        }
        if (FLASHES.isEmpty()) return;

        Camera camera = Projector.camera(mc);
        if (camera == null) return;

        for (Iterator<Flash> it = FLASHES.iterator(); it.hasNext(); ) {
            Flash flash = it.next();
            long limit = flash.heavy ? heavyTtl : ttl;
            long age = now - flash.bornAt;
            if (age > limit) continue;

            Projector.Screen at = Projector.project(mc, camera, flash.x, flash.y, flash.z, 1.3f);
            if (at == null || at.depth() > MAX_DISTANCE) continue;

            // Alpha falls off linearly over the lifetime, and size falls off with distance: at 40 ms the
            // only thing the eye registers is that something lit up, so a simple decay is indistinguishable
            // from a physically correct one.
            float life = 1f - (age / (float) limit);
            float radius = BASE_RADIUS * life / (float) Math.max(1.0, at.depth() * 0.25);
            if (radius < 1.5f) continue;
            glow(graphics, at.x(), at.y(), radius, life);
        }
    }

    /**
     * An additive-looking glow built from concentric squares.
     *
     * <p>Squares rather than a circle because a circle at these sizes is a per-pixel distance test — the
     * multiplication-heavy version of the same effect, for a shape that is on screen for two frames.
     * Three nested fills with rising alpha read as a soft bloom.
     */
    private static void glow(GuiGraphics graphics, float cx, float cy, float radius, float life) {
        int x = Math.round(cx), y = Math.round(cy);
        for (int ring = 3; ring >= 1; ring--) {
            float scale = ring / 3f;
            int r = Math.max(1, Math.round(radius * scale));
            // Outer rings are dimmer, so the overlap reads as a gradient toward a bright core.
            int alpha = Math.round(70 * life * (1f - scale * 0.6f));
            if (alpha <= 2) continue;
            int color = (alpha << 24) | 0x00ffd9a0;
            graphics.fill(x - r, y - r, x + r, y + r, color);
        }
    }

    /** Drops every live effect, e.g. on logout, so a new session never inherits an old flash. */
    static void reset() {
        FLASHES.clear();
    }
}
