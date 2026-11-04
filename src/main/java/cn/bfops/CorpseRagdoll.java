package cn.bfops;

import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Ragdoll death bodies.
 *
 * <h2>Why this exists</h2>
 *
 * <p>The reference pack's death animation lives in Havok ragdoll data that this project's asset dump does
 * not contain (the animation tree of the dump is empty), and the instance has no ragdoll mod to defer to —
 * only GeckoLib and a player animation library, neither of which ships a soldier skeleton. So the body is
 * posed here: the vanilla player model, driven by a small settle simulation.
 *
 * <p>It is deliberately <b>not</b> a physics engine. What a viewer actually reads in a death animation is
 * the first half second — the body tips, the limbs lag behind, then everything stops — so that is what is
 * modelled: a damped tip-over around the feet, limbs that splay and overshoot slightly before settling, and
 * a ground offset so the body rests on the surface instead of sinking into it.
 *
 * <h2>Where the bodies come from</h2>
 *
 * <p>Purely client-side: every player is tracked while alive, and the moment their health reaches zero the
 * body is dropped at their <em>last alive position</em>. Using the current position would be wrong — the
 * server teleports a dead player to the deploy camera almost immediately — so the last standing position is
 * kept per player and consumed on death.
 */
final class CorpseRagdoll {
    private static final long LIFE_MS = 12_000L;
    private static final float FALL_SECONDS = 0.55f;

    /** A body. All pose values are derived from {@link #age()}, so nothing needs per-frame state. */
    private record Body(UUID owner, Vec3 at, float yaw, long born, int variant, boolean squad,
                        ResourceLocation skin, boolean slim) {
        float age() {
            return (System.currentTimeMillis() - born) / 1000f;
        }
    }

    private record LastSpot(Vec3 at, float yaw) {}

    private static final List<Body> BODIES = new ArrayList<>();
    private static final Map<UUID, LastSpot> LAST_ALIVE = new HashMap<>();
    private static final Set<UUID> DEAD = new HashSet<>();
    private static PlayerModel<AbstractClientPlayer> normal, slim;

    private CorpseRagdoll() {}

    static void reset() {
        BODIES.clear();
        LAST_ALIVE.clear();
        DEAD.clear();
    }

    /** Called from the client tick: remembers live positions and drops a body on the frame a player dies. */
    static void track(Minecraft mc) {
        // A real ragdoll mod owns the bodies when it is installed; this is only the fallback.
        if (RagdollCompat.installed()) {
            BODIES.clear();
            return;
        }
        if (mc.level == null) {
            reset();
            return;
        }
        long now = System.currentTimeMillis();
        BODIES.removeIf(body -> now - body.born() > LIFE_MS);
        for (AbstractClientPlayer player : mc.level.players()) {
            UUID id = player.getUUID();
            boolean dead = player.isDeadOrDying() || player.getHealth() <= 0f;
            if (dead) {
                if (DEAD.add(id)) {
                    LastSpot spot = LAST_ALIVE.get(id);
                    Vec3 at = spot == null ? player.position() : spot.at();
                    float yaw = spot == null ? player.getYRot() : spot.yaw();
                    boolean squad = isSquadmate(mc, player);
                    BODIES.add(new Body(id, at, yaw, now, Math.abs(Objects.hash(id, now)) % VARIANT_COUNT, squad,
                            skinOf(player), usesSlimModel(player)));
                }
            } else {
                DEAD.remove(id);
                LAST_ALIVE.put(id, new LastSpot(player.position(), player.getYRot()));
            }
        }
    }

    private static boolean isSquadmate(Minecraft mc, AbstractClientPlayer player) {
        var state = Client.state;
        if (state == null || !state.has("me") || !state.has("mates")) return false;
        String self = Engine.str(state.getAsJsonObject("me"), "name", "");
        if (self.equals(player.getGameProfile().getName())) return false;
        for (var element : state.getAsJsonArray("mates")) {
            if (!element.isJsonObject()) continue;
            var mate = element.getAsJsonObject();
            if (Engine.str(mate, "name", "").equals(player.getGameProfile().getName())) return Engine.num(mate, "squad", 0) > 0;
        }
        return false;
    }

    private static ResourceLocation skinOf(AbstractClientPlayer player) {
        try {
            return player.getSkinTextureLocation();
        } catch (RuntimeException | LinkageError ex) {
            return null;
        }
    }

    private static boolean usesSlimModel(AbstractClientPlayer player) {
        try {
            return "slim".equals(player.getModelName());
        } catch (RuntimeException | LinkageError ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------ pose

    private static final int VARIANT_COUNT = 4;

    /** Damped tip-over: overshoots a little past 90°, then settles. */
    private static float fallAngle(float age) {
        float t = Math.min(1f, age / FALL_SECONDS);
        double settle = 1 - Math.exp(-5.5 * t) * Math.cos(6.2 * t);
        return (float) (92f * settle);
    }

    /** Limb splay: starts at the standing pose and lags behind the body, with a small overshoot. */
    private static float limb(float age, float target, float lag) {
        float t = Math.max(0f, Math.min(1f, (age - lag) / FALL_SECONDS));
        double settle = 1 - Math.exp(-4.2 * t) * Math.cos(5.0 * t);
        return (float) (target * settle);
    }

    // ------------------------------------------------------------------ rendering

    private static PlayerModel<AbstractClientPlayer> model(boolean slimModel) {
        Minecraft mc = Minecraft.getInstance();
        if (slimModel) {
            if (slim == null) slim = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
            return slim;
        }
        if (normal == null) normal = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        return normal;
    }

    static void render(RenderLevelStageEvent event) {
        if (BODIES.isEmpty() || RagdollCompat.installed()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        for (Body body : BODIES) {
            if (body.skin() == null) continue;
            float age = body.age();
            if (age < 0) continue;
            float fade = age > LIFE_MS / 1000f - 2f ? Math.max(0f, (LIFE_MS / 1000f - age) / 2f) : 1f;
            if (fade <= 0.02f) continue;
            float fall = fallAngle(age);
            PlayerModel<AbstractClientPlayer> model = model(body.slim());
            resetPose(model, body);

            pose.pushPose();
            try {
                pose.translate(body.at().x - camera.x, body.at().y + 0.05 - camera.y, body.at().z - camera.z);
                pose.mulPose(Axis.YP.rotationDegrees(-body.yaw()));
                // Tip around the feet so the body rotates onto the ground rather than through it.
                pose.mulPose(Axis.ZP.rotationDegrees(fall));
                pose.translate(0, 0.08, 0);
                pose.scale(-1f, -1f, 1f);
                pose.translate(0, -1.5f, 0);
                var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(body.skin()));
                int alpha = (int) (fade * 255f) << 24;
                model.renderToBuffer(pose, consumer, 0xF000F0, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, fade);
            } finally {
                pose.popPose();
            }
        }
        buffers.endBatch();
    }

    /**
     * Poses the model for this body: a variant-specific "collapsed" stance, laid over the tip-over that the
     * pose stack already applied. Squadmates get the variant that lands face-down with an arm out, which
     * makes a squad wipe readable at a glance without needing another marker on the map.
     */
    private static void resetPose(PlayerModel<AbstractClientPlayer> model, Body body) {
        float age = body.age();
        int variant = body.squad() ? 0 : 1 + body.variant() % Math.max(1, VARIANT_COUNT - 1);
        model.head.xRot = 0;
        model.head.yRot = 0;
        model.head.zRot = 0;
        model.body.xRot = 0;
        model.body.yRot = 0;
        model.body.zRot = 0;
        model.leftArm.xRot = 0; model.leftArm.yRot = 0; model.leftArm.zRot = 0;
        model.rightArm.xRot = 0; model.rightArm.yRot = 0; model.rightArm.zRot = 0;
        model.leftLeg.xRot = 0; model.leftLeg.yRot = 0; model.leftLeg.zRot = 0;
        model.rightLeg.xRot = 0; model.rightLeg.yRot = 0; model.rightLeg.zRot = 0;

        // Arms: flung out and slightly back, the way a body that lost its footing lands.
        float armSpread = switch (variant) {
            case 0 -> 70f;   // squad: one arm out ahead
            case 1 -> 55f;
            case 2 -> 85f;
            default -> 40f;
        };
        model.rightArm.zRot = (float) Math.toRadians(armSpread);
        model.leftArm.zRot = (float) -Math.toRadians(armSpread * 0.45f);
        model.rightArm.xRot = (float) Math.toRadians(limb(age, 18f, 0.05f));
        model.leftArm.xRot = (float) Math.toRadians(limb(age, 32f, 0.08f));
        // Legs: one bent, one trailing, so the body does not read as a plank.
        model.leftLeg.xRot = (float) Math.toRadians(limb(age, 22f, 0.10f));
        model.rightLeg.xRot = (float) Math.toRadians(limb(age, 6f, 0.12f));
        model.leftLeg.zRot = (float) Math.toRadians(limb(age, 7f, 0.12f));
        model.rightLeg.zRot = (float) -Math.toRadians(limb(age, 5f, 0.14f));
        // Head lolls to the side the body fell toward.
        model.head.zRot = (float) Math.toRadians(limb(age, variant % 2 == 0 ? 16f : -14f, 0.06f));
        model.head.xRot = (float) Math.toRadians(limb(age, variant == 0 ? 12f : 6f, 0.07f));
        model.hat.copyFrom(model.head);
    }
}
