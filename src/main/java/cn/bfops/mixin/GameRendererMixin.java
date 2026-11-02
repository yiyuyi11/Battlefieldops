package cn.bfops.mixin;

import cn.bfops.Cinematic;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets {@link Cinematic} move the camera.
 *
 * <h2>Why a mixin, and what was tried first</h2>
 *
 * <p>Three approaches were tried in order, and only this one works. The reasons matter because each looked
 * plausible:
 *
 * <ol>
 *   <li><b>{@code ViewportEvent.ComputeCameraAngles}.</b> Supported, fires every frame, has settable yaw and
 *       pitch — and cannot move the camera. Its handler runs <em>inside</em> {@code Camera.setup()}, which
 *       afterwards computes the eye position from the entity's interpolated coordinates and writes it
 *       itself. Anything written during the event is overwritten a few instructions later, silently.</li>
 *   <li><b>{@code RenderLevelStageEvent.AFTER_SKY} plus a reflective {@code setPosition}.</b> Fires after
 *       {@code setup()} returns, so the write is not overwritten — but by then {@code LevelRenderer} has
 *       already copied the camera position into local variables and built the model-view matrix from it.
 *       The world renders from the old position; only overlays that re-read {@code getPosition()} see the
 *       new one, which produces markers floating over terrain that did not move with them.</li>
 *   <li><b>This.</b> Inject immediately after {@code Camera.setup} inside {@code GameRenderer.renderLevel},
 *       which is before the projection matrix and the view matrix are built and before anything is culled.
 *       The moved camera is then the camera, with no inconsistency between lighting, culling and overlays.</li>
 * </ol>
 *
 * <p>The reference pack solves the same problem the same way on 1.20.1 Forge, where its own
 * {@code RenderLevelStageEvent} fires at a point that made the reflective route viable. That ordering is
 * not the same in 1.21.1 NeoForge, which is why copying its technique directly does not port.
 *
 * <h2>Where the state lives, and why not here</h2>
 *
 * <p>This class holds no state and makes no decisions. Every question — is a sequence running, where should
 * the camera be, has the reflective handle been resolved — belongs to {@link Cinematic}, which is ordinary
 * Java and can be reasoned about and tested. A mixin that accumulated logic would be logic that cannot be
 * unit-tested, in the one place where a mistake is hardest to diagnose.
 *
 * <h2>Failure containment</h2>
 *
 * <p>{@code require = 0}: if a future version moves or renames {@code Camera.setup}, the injection is simply
 * not applied and the game runs with a vanilla camera. Mixin's default ({@code require = 1}) would instead
 * throw during class transformation, which turns "the cinematics stopped working" into "the client will not
 * start".
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /**
     * Moves the camera immediately after vanilla positions it.
     *
     * <p>Injected {@code AFTER} the {@code Camera.setup} invocation rather than at {@code HEAD} of
     * {@code renderLevel}: at the head the camera has not been positioned yet for this frame, so reading it
     * to build the interpolation would read last frame's pose.
     */
    @Inject(method="renderLevel",at=@At("RETURN"),require=0)
    private void battlefieldOps$boundary(CallbackInfo info){cn.bfops.BoundaryEffect.render();}

    @Inject(method="renderLevel",at=@At("HEAD"),require=0)
    private void battlefieldOps$firstPerson(CallbackInfo info){cn.bfops.MatchView.lock();}

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;"
                    + "Lnet/minecraft/world/entity/Entity;ZZF)V",
            shift = At.Shift.AFTER), require = 0)
    private void battlefieldOps$afterCameraSetup(CallbackInfo info) {
        // The angles were already applied inside setup() by the vanilla event; this adds the position, at
        // the one point in the frame where vanilla has finished writing it and has not yet read it. The
        // projection matrix, the view matrix, the frustum and the chunk selection are all computed after
        // this returns, so they all see the cinematic pose.
        //
        // markMixinApplied first, every frame but cheap: it flips a flag on its first call so Cinematic can
        // distinguish "this injection never ran" (a target rename swallowed by require=0) from "it ran and
        // the camera genuinely should not move yet". Without that flag a silent injection failure would be
        // indistinguishable from a camera that simply has nothing to do.
        Cinematic.markMixinApplied();
        Cinematic.applyCameraOverride();
    }
}
