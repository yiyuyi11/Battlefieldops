package cn.bfops;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * World-space health bars above players' heads, for team and enemy alike.
 *
 * <h2>Where the numbers come from</h2>
 *
 * <p>The reference pack does this with {@code team_healthbar}, whose config exposes the whole design in
 * nine values ({@code healthbar_common.toml}). Those are adopted directly rather than re-derived, because
 * they encode playtesting this project has not done:
 *
 * <pre>
 * width  = 1.3 m, height = 0.1 m   # world-space size
 * fov_degree = 70                  # only draw bars within this cone of the crosshair
 * attack_hold_seconds = 30         # an enemy stays visible this long after being hit
 * show_enemy_healthbar / show_friendly_healthbar
 * enemy = #FF5555, friend = #5555FF, absorb = #CCFF00, background = #80808080
 * enable_team_outline, max_render_distance
 * </pre>
 *
 * <h2>The two rules that keep this from being a wallhack</h2>
 *
 * <p><b>One: the FOV cone.</b> {@code fov_degree = 70} is not a rendering optimisation, it is the entire
 * fairness mechanism — a bar only appears for a player the viewer is actually looking towards. Bars for
 * everything in a radius would turn a fight into a minimap with legs.
 *
 * <p><b>Two: an enemy bar needs a reason.</b> A hostile player's bar is drawn only if this client has been
 * given their position by the server, which for enemies happens solely while they are spotted. The bar
 * follows the server's decision rather than making its own, so it can never reveal more than the match map
 * already shows. Enemies additionally stay for {@link #ATTACK_HOLD_MS} after being hit, which is the
 * reference pack's behaviour and corresponds to "I know where that one is, I just shot them".
 *
 * <h2>Why nothing is cached but recent hits</h2>
 *
 * <p>Bars are recomputed from live entity state every frame; only the "last hit" timestamps are stored,
 * because those have to outlive the entity being visible. Caching positions would immediately be wrong:
 * a player's head moves every tick, and a stale bar is worse than no bar.
 */
final class Nameplates {
    /** World-space bar width in metres, from the reference pack. */
    private static final float BAR_WIDTH = 1.3f;
    /** World-space bar height in metres. */
    private static final float BAR_HEIGHT = 0.1f;

    /** Only players within this cone of the crosshair get a bar. The reference pack's {@code fov_degree}. */
    private static final double FOV_DEGREES = 70.0;

    /** How long an enemy bar persists after that enemy was hit. The reference pack's {@code attack_hold_seconds}. */
    private static final long ATTACK_HOLD_MS = 30_000L;

    /** Beyond this the bar is not drawn; bars at 200 blocks are noise, not information. */
    private static final double MAX_DISTANCE = 64.0;

    /** A bar is skipped entirely rather than drawn sub-pixel. */
    private static final int MIN_PIXELS = 6;

    private static final int ENEMY = 0xFFFF5555;
    private static final int FRIEND = 0xFF65B5FA;
    private static final int ABSORB = 0xFFCCFF00;
    private static final int BACKGROUND = 0x80808080;

    /** When each enemy was last hit, so a bar can outlive the spotting window. */
    private static final Map<UUID, Long> recentHits = new HashMap<>();

    private Nameplates() { }

    /** Records that the viewer damaged {@code target}, keeping its bar alive for a while. */
    static void recordHit(Player target) {
        if (target == null) return;
        recentHits.put(target.getUUID(), System.currentTimeMillis());
    }

    /** Drops timestamps that have aged out, so the map cannot grow across a long session. */
    private static void prune(long now) {
        Iterator<Map.Entry<UUID, Long>> it = recentHits.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > ATTACK_HOLD_MS) it.remove();
        }
    }

    /**
     * Draws every bar that passes the cone, distance and team rules.
     *
     * @param spotted enemy UUIDs the server authorised this client to see, from the snapshot
     */
    static void draw(GuiGraphics graphics, Minecraft mc, java.util.Set<UUID> spotted) {
        var level = mc.level;
        var self = mc.player;
        if (level == null || self == null) return;
        if (mc.options.hideGui) return;

        JsonObject state = Client.state;
        int selfTeam = state != null && state.has("me") ? Engine.num(state.getAsJsonObject("me"), "team", -1) : -1;
        if (selfTeam < 0) return;

        long now = System.currentTimeMillis();
        prune(now);

        var camera = Projector.camera(mc);
        if (camera == null) return;
        Vec3 eye = camera.getPosition();
        org.joml.Vector3f forward = camera.getLookVector();
        int height = graphics.guiHeight();
        double cosLimit = Math.cos(Math.toRadians(FOV_DEGREES * 0.5));

        for (AbstractClientPlayer other : level.players()) {
            if (other == self || !other.isAlive()) continue;
            double distance = eye.distanceTo(other.position());
            if (distance > MAX_DISTANCE) continue;

            int authorisedColor=TeamOutline.color(other);
            if(authorisedColor==0)continue;
            boolean teammate=authorisedColor==0xff65b5fa;
            if(!teammate)continue; // Enemy identity is conveyed only by the authorised red spot marker.

            // Cone test against the camera's own forward vector rather than against the player's body
            // angles: the bar must appear for what the view is pointed at, which during a death or deploy
            // sequence is not where the player is facing at all.
            Vec3 toOther = other.position().subtract(eye).normalize();
            if (toOther.x * forward.x() + toOther.y * forward.y() + toOther.z * forward.z() < cosLimit) {
                continue;
            }

            // Project the head, slightly above the model so the bar clears the helmet. The projection reads
            // the camera's own basis, so a bar stays pinned to its player even while the death or deploy
            // camera is overriding the view.
            Projector.Screen at = Projector.project(mc, camera,
                    other.getX(), other.getY() + other.getBbHeight() + 0.35, other.getZ(), 1.2f);
            if (at == null) continue;

            // Pixel width from the world-space width at this distance, so the bar genuinely shrinks with
            // range instead of being a fixed-size UI element glued to a person.
            float tanHalf = (float) Math.tan(Math.toRadians(mc.options.fov().get()) * 0.5);
            if (tanHalf <= 0) continue;
            int pixelWidth = Math.round((BAR_WIDTH / (float) Math.max(0.5, at.depth())) / tanHalf
                    * (height * 0.5f));
            if (pixelWidth < MIN_PIXELS) continue;
            int pixelHeight = Math.max(2, Math.round(pixelWidth * (BAR_HEIGHT / BAR_WIDTH)));

            TacticalColors.label(graphics, mc.font, (teammate?"◆ ":"◇ ")+other.getGameProfile().getName(),
                    Math.round(at.x()),Math.round(at.y())-12,authorisedColor);
            // The marker glyph in front of the name is the same art the deploy overlay and the HUD map use,
            // read from the same snapshot field — one class, one glyph, everywhere. A custom class with no
            // sheet keeps the plain diamond, which is why this is allowed to fail.
            // Class/downed glyphs are drawn once by WorldMarkers through the native 656 renderer.
            float health = other.getHealth() / Math.max(1f, other.getMaxHealth());
            float absorb = other.getAbsorptionAmount() / Math.max(1f, other.getMaxHealth());
            bar(graphics, Math.round(at.x()), Math.round(at.y()), pixelWidth, pixelHeight,
                    health, absorb, teammate ? FRIEND : ENEMY);
        }
    }

    /**
     * Whether two players are on the same side.
     *
     * <p>Minecraft's own team object is used rather than this mod's own membership table, because the
     * server puts players on a vanilla team for the sidebar and the glow; reading it here means the bar
     * and the nametag colour can never disagree.
     */
    private static boolean sameTeam(Minecraft mc, Player self, Player other) {
        var mine = self.getTeam();
        var theirs = other.getTeam();
        if (mine != null || theirs != null) return mine != null && mine.isAlliedTo(theirs);
        // No vanillateam: fall back to the snapshot's own notion of who is on your side.
        JsonObject state = Client.state;
        if (state == null || !state.has("mates") || !state.get("mates").isJsonArray()) return false;
        JsonArray mates = state.getAsJsonArray("mates");
        for (var element : mates) {
            if (!element.isJsonObject()) continue;
            JsonObject mate = element.getAsJsonObject();
            if (mate.has("hostile") && mate.get("hostile").getAsBoolean()) continue;
            if (mate.has("name") && mate.get("name").getAsString().equals(other.getGameProfile().getName())) {
                return true;
            }
        }
        return false;
    }

    /** Background, damage fill, absorption overlay — three fills, no texture, no allocation. */
    private static void bar(GuiGraphics graphics, int centerX, int y, int width, int height,
                            float health, float absorb, int color) {
        int left = centerX - width / 2;
        graphics.fill(left - 1, y - 1, left + width + 1, y + height + 1, 0xC0000000);
        graphics.fill(left, y, left + width, y + height, BACKGROUND);
        int filled = Math.round(width * Math.max(0f, Math.min(1f, health)));
        if (filled > 0) graphics.fill(left, y, left + filled, y + height, color);
        if (absorb > 0) {
            // Absorption sits on top of the health fill, as in the reference pack, because it is damage the
            // player still has — showing it beside the bar would imply a second health pool.
            int extra = Math.round(width * Math.min(1f, absorb));
            graphics.fill(left, y, left + Math.min(width, filled + extra), y + height, ABSORB);
        }
    }

    /**
     * Projects a world position to GUI pixels.
     *
     * <p>Delegates to {@link Projector}, which reads the camera's own basis. Kept as a one-line forwarder
     * rather than inlined so the two call sites above stay readable.
     */

    /** Clears hit timestamps, e.g. on logout. */
    static void reset() {
        recentHits.clear();
    }
}
