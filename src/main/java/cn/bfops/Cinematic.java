package cn.bfops;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Client-only camera poses. A new transition always begins at the last rendered pose. */
@EventBusSubscriber(modid = "battlefield_ops", value = Dist.CLIENT)
public final class Cinematic {
    private enum Mode {IDLE, DOWNED, DEATH, OVERVIEW, HOLD, DESCEND, RETURN}
    record Aim(float yaw, float pitch) {}
    private record Pose(Vec3 position, float yaw, float pitch) {}
    private static Mode mode = Mode.IDLE;
    private static Pose from, to;
    private static int ticks, duration;
    private static Vec3 target;
    private static float inputYaw, inputPitch;
    private static boolean mixinApplied;

    private Cinematic() {}
    public static void markMixinApplied() {mixinApplied = true;}
    static boolean cameraAvailable() {return mixinApplied;}
    static boolean active() {return mode != Mode.IDLE;}
    static boolean inDeathSequence() {return mode == Mode.DEATH;}
    public static boolean deployTravelLocked() {return mode == Mode.DESCEND;}
    static void clear() {mode = Mode.IDLE; target = null; from = to = null;}

    private static Pose visible(Pose fallback) {
        var camera = Projector.camera(Minecraft.getInstance());
        return camera == null ? fallback : new Pose(camera.getPosition(), camera.getYRot(), camera.getXRot());
    }

    static Aim lookAt(Vec3 from, Vec3 target, float fallbackYaw, float fallbackPitch) {
        if (target == null) return new Aim(fallbackYaw, fallbackPitch);
        Vec3 delta = target.subtract(from);
        if (delta.lengthSqr() < 1.0e-6) return new Aim(fallbackYaw, fallbackPitch);
        double horizontal = Math.hypot(delta.x, delta.z);
        float yaw = horizontal < 1.0e-4 ? fallbackYaw : (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
        return new Aim(yaw, (float)-Math.toDegrees(Math.atan2(delta.y, horizontal)));
    }

    static double ease(double progress) {
        double t = Math.max(0, Math.min(1, progress));
        return t * t * (3 - 2 * t);
    }

    static Vec3 focusTarget(JsonObject payload) {
        if (!payload.has("focusX") || !payload.has("focusY") || !payload.has("focusZ")) return null;
        var position = new Vec3(payload.get("focusX").getAsDouble(), payload.get("focusY").getAsDouble(),
                payload.get("focusZ").getAsDouble());
        return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z) ? position : null;
    }

    static void downed(Vec3 killer) {
        var player = Minecraft.getInstance().player;
        if (player == null || killer == null || mode == Mode.DOWNED || mode == Mode.DEATH) return;
        var view = visible(new Pose(player.getEyePosition(), player.getYRot(), player.getXRot()));
        target = killer;
        inputYaw = player.getYRot(); inputPitch = player.getXRot();
        begin(Mode.DOWNED, view, view, 8);
    }

    static void recoverDowned() {
        if (mode != Mode.DOWNED) return;
        var view = visible(from);
        begin(Mode.RETURN, view, view, 6);
        target = null;
    }

    static void startDeath(double x, double y, double z, float yaw, float pitch) {
        startDeath(x, y, z, yaw, pitch, null);
    }

    static void startDeath(double x, double y, double z, float yaw, float pitch, Vec3 killer) {
        if (mode == Mode.DEATH) {
            if (target == null && killer != null) {
                Pose start = visible(from);
                target = killer;
                Aim aim = lookAt(start.position, target, start.yaw, start.pitch);
                begin(Mode.DEATH, start, new Pose(start.position, aim.yaw, aim.pitch), 8);
            }
            return;
        }
        var start = visible(new Pose(new Vec3(x, y, z), yaw, pitch));
        if (killer != null || mode != Mode.DOWNED) target = killer;
        Aim aim = lookAt(start.position, target, start.yaw, start.pitch);
        begin(Mode.DEATH, start, new Pose(start.position, aim.yaw, aim.pitch), 8);
    }

    static void entryView(double x, double y, double z, float yaw, float pitch) {
        var pose = new Pose(new Vec3(x, y, z), yaw, pitch);
        begin(Mode.HOLD, pose, pose, 1);
    }

    static void overview(DeployView.Placement view) {
        var destination = new Pose(new Vec3(view.x(), view.y(), view.z()), view.yaw(), view.pitch());
        var start = visible(destination);
        int travel = mode == Mode.OVERVIEW || mode == Mode.HOLD ? 5 : 18;
        target = null;
        begin(Mode.OVERVIEW, start, destination, travel);
    }

    static void descend() {
        var player = Minecraft.getInstance().player;
        if (player == null || mode == Mode.DESCEND) return;
        Pose destination = new Pose(player.getEyePosition(), player.getYRot(), player.getXRot());
        JsonObject member = Client.state.has("me") ? Client.state.getAsJsonObject("me") : null;
        if (member != null && member.has("hasDeployTarget") && member.get("hasDeployTarget").getAsBoolean()) {
            destination = new Pose(new Vec3(member.get("deployX").getAsDouble(), member.get("deployY").getAsDouble(),
                    member.get("deployZ").getAsDouble()), member.get("deployYaw").getAsFloat(), member.get("deployPitch").getAsFloat());
        }
        Pose start = visible(destination);
        target = null;
        if (start.position.distanceToSqr(destination.position) < .01
                && Math.abs(Smooth.wrapDegrees(destination.yaw - start.yaw)) < 1
                && Math.abs(destination.pitch - start.pitch) < 1) {clear();return;}
        begin(Mode.DESCEND, start, destination, 16);
    }

    private static void begin(Mode next, Pose start, Pose end, int length) {
        mode = next; from = start; to = end; ticks = 0; duration = length;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (mode == Mode.IDLE || mode == Mode.HOLD) return;
        if (Minecraft.getInstance().player == null) {clear();return;}
        if (ticks < duration) ticks++;
        if (ticks < duration) return;
        if (mode == Mode.OVERVIEW) begin(Mode.HOLD, to, to, 1);
        else if (mode == Mode.DESCEND) {
            Pose start = visible(to);
            begin(Mode.RETURN, start, start, 5);
        } else if (mode == Mode.RETURN) clear();
    }

    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event) {
        if (mode == Mode.IDLE) return;
        double progress = ease((ticks + event.getPartialTick()) / duration);
        Aim end = new Aim(to.yaw, to.pitch);
        if (mode == Mode.RETURN) end = new Aim(event.getYaw(), event.getPitch());
        if (mode == Mode.DOWNED) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            Aim aim = lookAt(player.getEyePosition(), target, from.yaw, from.pitch);
            end = new Aim(aim.yaw + Smooth.wrapDegrees(player.getYRot() - inputYaw),
                    Math.max(-89, Math.min(89, aim.pitch + player.getXRot() - inputPitch)));
        }
        event.setYaw(from.yaw + Smooth.wrapDegrees(end.yaw - from.yaw) * (float)progress);
        event.setPitch(from.pitch + (end.pitch - from.pitch) * (float)progress);
    }

    public static void applyCameraOverride() {
        if (mode == Mode.IDLE || mode == Mode.DOWNED) return;
        var minecraft = Minecraft.getInstance();
        var camera = Projector.camera(minecraft);
        if (camera == null) return;
        Vec3 end = mode == Mode.RETURN ? camera.getPosition() : to.position;
        double progress = ease((ticks + minecraft.getTimer().getGameTimeDeltaPartialTick(true)) / duration);
        CameraAccess.move(camera, from.position.lerp(end, progress));
    }
}
