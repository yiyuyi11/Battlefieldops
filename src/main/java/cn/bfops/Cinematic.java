package cn.bfops;

import com.google.gson.JsonObject;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Owns the camera pose for the death and deploy sequences.
 *
 * <h2>Why this exists</h2>
 *
 * <p>The reference pack's death flow ({@code ClientDeathFlowState}) is a ten-phase state machine that moves
 * the camera independently of the player: the view looks up off the body, rises, holds over the battlefield,
 * then travels down to wherever the player deploys. Its timing constants are adopted verbatim below, because
 * they encode pacing decisions this project has no way to re-derive by reasoning:
 *
 * <pre>
 * DEATH_LOOK_UP_TICKS  = 14    # look up off the body
 * DEATH_FALL_TICKS     =  8    # drift upward
 * DEATH_WAIT_TICKS     =  4    # hold
 * DEATH_RECOVER_TICKS  = 14    # settle
 * DEPLOY_TRAVEL_TICKS  = 40    # the long travel down to the deploy position
 * </pre>
 *
 * <h2>How the camera is actually moved</h2>
 *
 * <p>Two halves, and they are not symmetrical:
 *
 * <p><b>Angles use a supported API.</b> {@link ViewportEvent.ComputeCameraAngles} fires inside
 * {@code Camera.setup()} with settable yaw, pitch and roll, and its values are adopted by the
 * {@code setRotation} call that follows. Nothing here can break on a version bump.
 *
 * <p><b>Position needs the mixin.</b> Vanilla exposes no public position setter, and neither
 * {@code ComputeCameraAngles} nor {@code RenderLevelStageEvent} is early enough to make a reflective write
 * take effect — the first is overwritten immediately, and by the second the view matrix is already built.
 * {@code cn.bfops.mixin.GameRendererMixin} injects immediately after {@code Camera.setup}, which is the only
 * point where a write is both safe and effective. This class holds all the decisions; the mixin only calls
 * {@link #applyCameraOverride()}.
 *
 * <h2>Why the player entity is not touched</h2>
 *
 * <p>The obvious alternative is spectator mode plus a teleport. This mod did exactly that for the deploy
 * viewpoint, and this class replaces it. Three things are wrong with it:
 *
 * <ol>
 *   <li>It changes server-visible state to achieve a client-side visual. Moving a camera should not alter
 *       what the server thinks the player's gamemode is.</li>
 *   <li>It cannot be smooth. A teleport is instantaneous, so the "camera falls into the deploy position"
 *       motion — most of what the sequence is for — is absent.</li>
 *   <li>Spectator suppresses first-person hands and changes interaction, which leaks into the deploy
 *       screen's own behaviour.</li>
 * </ol>
 *
 * <p>So the entity stays where the server put it and only the camera moves, which is also what makes
 * per-frame interpolation possible at all.
 */
@EventBusSubscriber(modid = "battlefield_ops", value = Dist.CLIENT)
public final class Cinematic {
    // ---- Timings, taken verbatim from ClientDeathFlowState -------------------------------

    /** Ticks for the view to look up off the body, before the body starts rising. */
    private static final int LOOK_UP_TICKS = 14;
    /** Ticks for the view to drift upward off the death position. */
    private static final int FALL_TICKS = 8;
    /** Ticks to hold at the top. */
    private static final int WAIT_TICKS = 4;
    /** Ticks to settle from the hold into the deploy viewpoint. */
    private static final int RECOVER_TICKS = 14;
    /** Ticks for the long travel from the death view down to the deploy position. */
    private static final int DEPLOY_TRAVEL_TICKS = 40;

    /** How far above the death position the view rises, in blocks. */
    private static final double RISE_BLOCKS = 18.0;

    /** How far the view pitches up at the head of the sequence, in degrees. */
    private static final double LOOK_UP_PITCH = 22.0;

    // ---- Phases ---------------------------------------------------------------------------

    private static final int IDLE = 0;
    private static final int LOOK_UP = 1;
    private static final int FALL = 2;
    private static final int WAIT = 3;
    private static final int RECOVER = 4;
    private static final int DEPLOY_TRAVEL = 5;
    private static final int HOLD = 6;
    private static boolean holdAtDestination;

    /**
     * True while the deploy camera is still travelling to the destination.
     *
     * <p>The camera owns the pose during the descent, so player input would fight it: the body would move
     * before the view arrives, which reads as the stutter the operator reported. Movement is held until the
     * travel phase finishes.
     */
    public static boolean deployTravelLocked() {
        return phase == DEPLOY_TRAVEL || phase == LOOK_UP || phase == FALL;
    }

    private static int phase = IDLE;
    private static int phaseTicks;
    private static int phaseTotal;

    /** Where the current phase started and where it is heading. */
    private static Vec3 fromPosition = Vec3.ZERO;
    private static Vec3 toPosition = Vec3.ZERO;
    private static float fromYaw, toYaw;
    private static float fromPitch, toPitch;

    /** The death position, kept for the whole sequence. */
    private static Vec3 deathPosition = Vec3.ZERO;

    // ---- Mixin health --------------------------------------------------------------------

    /**
     * Whether the {@code GameRendererMixin} injection has ever run.
     *
     * <p>Set on the first frame by {@link #markMixinApplied()}. If it is still false when a sequence tries
     * to start, the injection did not apply — which {@code require = 0} would otherwise swallow silently, a
     * "cinematics stopped working" with no clue. The flag turns that into one log line instead.
     */
    private static volatile boolean mixinApplied;
    private static boolean mixinWarned;

    /**
     * Called by the mixin every frame; records that the injection is alive.
     *
     * <p>The volatile write only happens on the transition, so the per-frame cost is one read. It must be a
     * separate method from {@link #applyCameraOverride()} because the override early-returns when no
     * sequence is running — the flag has to be set even then, or the health check could not tell "injection
     * absent" from "no camera work to do".
     */
    public static void markMixinApplied() {
        if (!mixinApplied) {
            mixinApplied = true;
            BattlefieldOps.LOG.debug("Camera mixin applied; death/deploy cinematics available");
        }
    }

    /** True once the mixin has been seen running, i.e. the camera override is actually wired up. */
    static boolean cameraAvailable() {
        return mixinApplied;
    }

    /**
     * One warning, on the first sequence that starts without the mixin ever having run.
     *
     * <p>The injection is {@code require = 0} so a renamed {@code Camera.setup} target does not crash the
     * client — but that same leniency makes a failed injection silent. This is the compensating signal: the
     * first time a death or deploy sequence asks for the camera, it checks the flag and, if the mixin never
     * marked itself, logs exactly once that the cinematic will not move the camera.
     */
    private static void warnIfMixinAbsent() {
        if (mixinApplied || mixinWarned) return;
        mixinWarned = true;
        BattlefieldOps.LOG.warn("Camera mixin has not run; death/deploy camera will not move "
                + "(the rest of the mod is unaffected)");
    }

    private Cinematic() { }

    // ---- Public surface -------------------------------------------------------------------

    /**
     * Starts the death sequence at a position.
     *
     * <p>Idempotent: a second call while a sequence is running is ignored, because the server can resend a
     * death notification and restarting the camera mid-fall would be visible.
     */
    static void startDeath(double x, double y, double z, float yaw, float pitch) {
        if (phase != IDLE) return;
        warnIfMixinAbsent();
        deathPosition = new Vec3(x, y, z);
        fromPosition = deathPosition;
        fromYaw = yaw;
        fromPitch = pitch;
        toPosition = deathPosition.add(0, RISE_BLOCKS, 0);
        toYaw = yaw;
        // Look up, not down. The reference pack's look-up phase exists so the player is looking at the
        // battlefield rather than at the ground their body is lying on.
        toPitch = Math.max(-90f, pitch - (float) LOOK_UP_PITCH);
        begin(LOOK_UP, LOOK_UP_TICKS);
    }

    /**
     * Starts the travel from the current view down to the deploy position.
     *
     * <p>Begins from wherever the camera is now, not from the death position, so a player who opens deploy
     * early or late still gets a continuous move instead of a jump back to the body first.
     */
    static void startDeployTravel(double x, double y, double z, float yaw, float pitch) {
        warnIfMixinAbsent();
        Camera camera = Projector.camera(Minecraft.getInstance());
        fromPosition = camera == null ? new Vec3(x, y, z) : camera.getPosition();
        fromYaw = camera == null ? yaw : camera.getYRot();
        fromPitch = camera == null ? pitch : camera.getXRot();
        toPosition = new Vec3(x, y, z);
        toYaw = yaw;
        toPitch = pitch;
        begin(DEPLOY_TRAVEL, DEPLOY_TRAVEL_TICKS);
    }

    static void entryView(double x,double y,double z,float yaw,float pitch){
        fromPosition=toPosition=new Vec3(x,y,z);fromYaw=toYaw=yaw;fromPitch=toPitch=pitch;
        begin(HOLD,1);holdAtDestination=true;
    }
    static void overview(DeployView.Placement view) {
        startDeployTravel(view.x(),view.y(),view.z(),view.yaw(),view.pitch());
        holdAtDestination = true;
    }

    /**
     * Descends to the body the server just deployed.
     *
     * <p>Reads the server-confirmed deploy target from the snapshot when present, and only falls back to the
     * local player position when it is absent (an old server, or a snapshot that predates the field). The
     * snapshot and the teleport are separate packets, and reading {@code player.getX()} here would bet on
     * the teleport always arriving first — an ordering that is true today and has no reason to be relied on.
     */
    static void descend() {
        JsonObject me = Client.state.has("me") && Client.state.get("me").isJsonObject()
                ? Client.state.getAsJsonObject("me") : null;
        if (me != null && me.has("hasDeployTarget") && me.get("hasDeployTarget").getAsBoolean()) {
            startDeployTravel(me.get("deployX").getAsDouble(),me.get("deployY").getAsDouble(),
                    me.get("deployZ").getAsDouble(),me.get("deployYaw").getAsFloat(),me.get("deployPitch").getAsFloat());
            holdAtDestination = false;
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        startDeployTravel(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot());
        holdAtDestination = false;
    }

    /** Ends any sequence and hands the camera back to vanilla. */
    static void clear() {
        phase = IDLE;
        phaseTicks = 0;
        phaseTotal = 0;
        holdAtDestination=false;
    }

    /** True while this class is moving the camera. */
    static boolean active() {
        return phase != IDLE;
    }

    /** True when the player is dead and the camera is running the death sequence. */
    static boolean inDeathSequence() {
        return phase == LOOK_UP || phase == FALL || phase == WAIT || phase == RECOVER;
    }

    // ---- Tick -----------------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if((phase==HOLD||phase==DEPLOY_TRAVEL&&holdAtDestination)&&MatchView.combat(Client.state))descend();
        if (phase == IDLE || phase == HOLD) return;
        if(phase==DEPLOY_TRAVEL&&!holdAtDestination){
            var player=Minecraft.getInstance().player;
            // Wait until the teleport reaches the confirmed destination before following motion.
            if(player!=null&&player.getEyePosition().distanceToSqr(toPosition)<256){
                toPosition=player.getEyePosition();toYaw=player.getYRot();toPitch=player.getXRot();
            }
        }
        phaseTicks++;
        if (phaseTicks < phaseTotal) return;

        switch (phase) {
            case LOOK_UP -> {
                // The rise starts from where the look-up phase ended, so the two join without a flicker.
                fromPosition = toPosition;
                fromPitch = toPitch;
                toPosition = deathPosition.add(0, RISE_BLOCKS, 0);
                begin(FALL, FALL_TICKS);
            }
            case FALL -> begin(WAIT, WAIT_TICKS);
            case WAIT -> {
                fromPosition = toPosition;
                begin(RECOVER, RECOVER_TICKS);
            }
            case RECOVER -> {
                // RECOVER ends by waiting for the server to name a destination: guessing one would send the
                // camera somewhere wrong, which is worse than holding still. DEPLOY_TRAVEL ends by handing
                // the camera back so the player looks through their own eyes again.
                phase = IDLE;
            }
            case DEPLOY_TRAVEL -> {
                if(holdAtDestination) { fromPosition=toPosition; fromYaw=toYaw; fromPitch=toPitch; begin(HOLD,1); }
                else phase=IDLE;
            }
            default -> phase = IDLE;
        }
    }

    /**
     * Rotates the camera through the supported API.
     *
     * <p>Fired per frame inside {@code Camera.setup()}, and its values are adopted by the
     * {@code setRotation} call that follows immediately — which is exactly why angles work here and position
     * does not.
     */
    @SubscribeEvent
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        if (phase == IDLE) return;
        double eased = eased(event.getPartialTick());
        event.setYaw(fromYaw + Smooth.wrapDegrees(toYaw - fromYaw) * (float) eased);
        event.setPitch(fromPitch + (toPitch - fromPitch) * (float) eased);
    }

    // ---- Called by the mixin --------------------------------------------------------------

    /**
     * Writes the interpolated position.
     *
     * <p>Called from {@code GameRendererMixin} immediately after {@code Camera.setup} returns, which is the
     * only point in the frame where the write both survives and is used: before vanilla builds the
     * projection and view matrices, and before anything is culled against them.
     *
     * <p>Public only because the mixin lives in another package. Nothing else should call it.
     */
    public static void applyCameraOverride() {
        if (phase == IDLE) return;
        Camera camera = Projector.camera(Minecraft.getInstance());
        if (camera == null) return;
        // The reflective write lives in CameraAccess: one small auditable class instead of reflection
        // scattered through the phase machine. A failure there is a quiet no-op, so the worst case is a
        // vanilla camera and a working game.
        CameraAccess.move(camera, interpolate(partialTickForThisFrame()));
    }

    /**
     * Partial tick for this frame.
     *
     * <p>Read from the delta tracker rather than passed in, because the injection point has no access to the
     * {@code DeltaTracker} argument without capturing it, and capturing it would tie this class to the exact
     * local variable ordering of {@code renderLevel} — the most fragile thing in the whole mixin.
     */
    private static float partialTickForThisFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTimer() == null) return 0f;
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        return Math.max(0f, Math.min(1f, partial));
    }

    /** Eased position for the current phase progress. */
    private static Vec3 interpolate(double partialTick) {
        double eased = eased(partialTick);
        return new Vec3(
                fromPosition.x + (toPosition.x - fromPosition.x) * eased,
                fromPosition.y + (toPosition.y - fromPosition.y) * eased,
                fromPosition.z + (toPosition.z - fromPosition.z) * eased);
    }

    /** Eased progress through the current phase, including the fraction of the current tick elapsed. */
    private static double eased(double partialTick) {
        double progress = Math.min(1.0, (phaseTicks + partialTick) / (double) Math.max(1, phaseTotal));
        // Ease-out cubic, matching the reference pack's own easing.
        double inverse = 1.0 - progress;
        return 1.0 - inverse * inverse * inverse;
    }

    private static void begin(int next, int ticks) {
        phase = next;
        phaseTicks = 0;
        phaseTotal = Math.max(1, ticks);
    }
}
