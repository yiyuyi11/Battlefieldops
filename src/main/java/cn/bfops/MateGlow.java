package cn.bfops;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The always-on blue halo around living teammates in the world.
 *
 * <h2>What this is for</h2>
 *
 * <p>The reference behaviour is straightforward to state: a friendly is <em>always</em> visible as a soft blue
 * glow, and an enemy is only visible when marked. This class draws the friendly half.
 *
 * <h2>Why it is an overlay and not a glow flag</h2>
 *
 * <p>Vanilla's glowing flag is a single shared entity flag — every client in the world sees the same thing.
 * Using it for teammates would therefore mean a player who is somebody's teammate glows for their enemies too,
 * which is the opposite of the intent. So the halo is drawn by the client, per viewer, from the client's own
 * team information, and it is <em>additive</em>: it never hides vanilla name tags, never changes the model, and
 * disappears the moment the player is not on your team.
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <p>It does not draw through walls as a filled silhouette. A solid outline visible through terrain would be a
 * wall-hack with a friendly colour, and the range limit exists for the same reason: {@link Tactics#MATE_GLOW_RANGE}
 * blocks is the squad's own radius, matching the marking range, so nothing is haloed that could not also be
 * marked. Past that distance a teammate is represented by the minimap blip, which is the correct place for
 * long-range positional information.
 *
 * <p>It also does not fade by line of sight. Knowing where your squad is, through a wall, is the point of the
 * feature — that is what makes a squad feel like a squad — and it is not an advantage over the enemy because the
 * information is already on the map.
 */
final class MateGlow {
    /** Radius of the halo core in GUI pixels at close range. */
    private static final int CORE = 3;
    /** Radius of the soft outer ring, which is what makes it read as a glow rather than a dot. */
    private static final int RING = 9;

    /** Built once per snapshot rather than per frame: the lookup is a string comparison per player. */
    private static JsonObject lastState;
    private static final Set<String> mates = new HashSet<>();

    private MateGlow() { }

    /**
     * Draws a halo behind every living teammate in view.
     *
     * <p>Called from the HUD pass after the world has been rendered, so the halos sit on top of terrain. That is
     * a deliberate choice over a real emissive glow: a post-processing outline would need core shader access and
     * would not be per-viewer, and "slightly in front of the wall" is what the reference pack's own
     * {@code team_outline} does too.
     *
     * @param now current time, for the slow pulse
     */
    static void draw(GuiGraphics graphics, Minecraft mc, long now) {
        if (mc == null || mc.level == null || mc.player == null) return;
        if (!allowed(Client.state)) return;
        JsonObject state = Client.state;

        refresh(state);
        if (mates.isEmpty()) return;

        var camera = Projector.camera(mc);
        if (camera == null) return;
        Vec3 eye = camera.getPosition();
        int tint = pulse(now);

        for (AbstractClientPlayer other : mc.level.players()) {
            if (other == mc.player || !other.isAlive()) continue;
            if (!mates.contains(other.getGameProfile().getName())
                    && !mates.contains(other.getUUID().toString())) {
                continue;
            }
            double distance = eye.distanceTo(other.position());
            if (distance > Tactics.MATE_GLOW_RANGE) continue;
            // Chest height rather than feet: a halo at the boots disappears into the ground on a slope, and
            // the point of the marker is to be findable.
            var at = Projector.project(mc, camera, other.getX(), other.getY() + other.getBbHeight() * 0.5,
                    other.getZ(), 1.05f);
            if (at == null) continue;
            int x = Math.round(at.x()), y = Math.round(at.y());
            // Two rings and a core, drawn outward-in so the brightest part is the innermost. Alpha is scaled by
            // depth only to stop a distant halo from being as loud as a nearby one.
            float far = (float) Math.min(1.0, distance / Tactics.MATE_GLOW_RANGE);
            ring(graphics, x, y, RING, TacticalColors.withAlpha(tint, 0.20f - 0.10f * far));
            ring(graphics, x, y, RING * 2 / 3, TacticalColors.withAlpha(tint, 0.34f - 0.16f * far));
            TacticalColors.dot(graphics, x, y, CORE, TacticalColors.withAlpha(tint, 0.85f - 0.25f * far));
        }
    }

    /**
     * A soft ring, drawn as a filled circle with the middle punched out.
     *
     * <p>Not a one-pixel outline: at ninety blocks a single-pixel circle is a handful of unconnected dots, and
     * the halo reads as debris rather than as a marker. A thick translucent band survives the downscale.
     */
    private static void ring(GuiGraphics g, int x, int y, int radius, int color) {
        // Draws from ringBands rather than recomputing the circle, so the geometry the tests assert is the
        // geometry that ships. A second copy of this arithmetic is exactly how a halo ends up leaking outside
        // the radius in one place and not the other.
        for (int[] band : ringBands(radius)) {
            int dy = band[0];
            int outer = band[1];
            int hole = band[2];
            if (hole > 0) {
                // Two side bands: everything outside the hole on this row.
                g.fill(x - outer, y + dy, x - hole, y + dy + 1, color);
                g.fill(x + hole, y + dy, x + outer + 1, y + dy + 1, color);
            } else {
                g.fill(x - outer, y + dy, x + outer + 1, y + dy + 1, color);
            }
        }
    }

    /**
     * Whether the halo should be drawn at all for this snapshot.
     *
     * <p>Three separate reasons it can be off, and they must not be confused: not in a match, the map rule
     * switched it off, or there is no snapshot yet. Only the middle one is the operator's decision; treating an
     * absent rule as "off" would mean a server that does not send the field silently loses the feature, which is
     * how a working feature gets reported as broken.
     *
     * @return true when the halo may be drawn
     */
    static boolean allowed(JsonObject state) {
        if (state == null) return false;
        if (!state.has("inMatch") || !state.get("inMatch").getAsBoolean()) return false;
        if (!state.has("arena") || !state.get("arena").isJsonObject()) return true;
        JsonObject arena = state.getAsJsonObject("arena");
        // `glowTeammates` was written to govern the world-space outline, and this is that outline: an operator
        // who switched it off wanted a match without persistent friendly markers.
        JsonObject rules = arena.has("rules") && arena.get("rules").isJsonObject()
                ? arena.getAsJsonObject("rules") : arena;
        if (!rules.has("glowTeammates")) return true;
        return rules.get("glowTeammates").getAsBoolean();
    }

    /**
     * Rebuilds the teammate name set from an explicit snapshot.
     *
     * <p>Separate from {@link #refresh} so the rule can be checked headless: {@code refresh} takes the live
     * {@link Client#state} and its identity caching, which a test cannot drive.
     */
    static void remember(JsonObject state) {
        mates.clear();
        if (state == null) return;
        if (!state.has("mates") || !state.get("mates").isJsonArray()) return;
        for (var element : state.getAsJsonArray("mates")) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            if (!row.has("hostile") || row.get("hostile").getAsBoolean()) continue;
            if (row.has("alive") && !row.get("alive").getAsBoolean()) continue;
            String name = Engine.str(row, "name", "");
            if (!name.isEmpty()) mates.add(name);
            String uuid = Engine.str(row, "uuid", "");
            if (!uuid.isEmpty()) mates.add(uuid);
        }
    }

    /**
     * The horizontal extent of one ring row: {@code {dy, outerRun, holeRun}}.
     *
     * <p>Pulled out of the draw so the geometry can be asserted without a graphics context. The invariant that
     * matters is that no run escapes its own radius, and that {@code holeRun} never exceeds {@code outerRun} —
     * a halo that leaks past its radius is a solid disc, and a solid disc over a teammate is a black box over
     * their model.
     *
     * <p>A {@code holeRun} of zero means the row is solid: near the top and bottom of the circle the chord is
     * narrower than the band is thick, so there is nothing left to punch out. Stating that as "zero means solid"
     * rather than as a boolean keeps the row to three numbers.
     */
    static int[][] ringBands(int radius) {
        int r = Math.max(1, radius);
        int inner = Math.max(1, r - 3);
        int rows = r * 2 + 1;
        int[][] bands = new int[rows][];
        int index = 0;
        for (int dy = -r; dy <= r; dy++) {
            int outerRun = (int) Math.sqrt((double) r * r - (double) dy * dy);
            int holeRun = 0;
            if (dy >= -inner && dy <= inner) {
                // The hole is only punched where the inner circle actually has a chord on this row.
                holeRun = (int) Math.sqrt((double) inner * inner - (double) dy * dy);
            }
            bands[index++] = new int[] {dy, outerRun, holeRun};
        }
        return bands;
    }

    /**
     * Alpha of the pulse at a moment, as a byte.
     *
     * <p>Exposed so the period and amplitude can be checked: a pulse that runs at four hertz is a strobe, and one
     * that never varies is a static dot that reads as a rendering artefact.
     */
    static int pulseAlpha(long now) {
        double phase = Math.floorMod(now, PULSE_MS) / (double) PULSE_MS;
        double wave = 0.5 + 0.5 * Math.sin(phase * Math.PI * 2);
        return (int) Math.round(0x88 + wave * 0x30);
    }

    /**
     * A slow pulse so the halo reads as "alive and friendly" rather than as a static UI dot.
     *
     * <p>Period {@link #PULSE_MS}, amplitude small: this is a background reassurance, and anything faster or
     * larger would compete with the enemy marks that are supposed to draw the eye.
     */
    private static int pulse(long now) {
        return (pulseAlpha(now) << 24) | (TacticalColors.FRIEND & 0xFFFFFF);
    }

    /** How long one pulse takes. Package-visible so the pulse period can be asserted in a test. */
    static final long PULSE_MS = 2_400L;

    /**
     * Rebuilds the teammate name set when the snapshot changes.
     *
     * <p>Keyed on object identity: {@link Client#state} is replaced wholesale when a new snapshot arrives, so an
     * identity check is both correct and free. Comparing contents would cost far more than the set it guards.
     */
    private static void refresh(JsonObject state) {
        if (state == lastState) return;
        remember(state);
        lastState = state;
    }

    /** Forgets the cached set; called on logout so a new server starts clean. */
    static void reset() {
        lastState = null;
        mates.clear();
    }

    /** Whether {@code name} is currently known to be a teammate, for the tests and the debug overlay. */
    static boolean isMate(String name) {
        return name != null && mates.contains(name);
    }
}
