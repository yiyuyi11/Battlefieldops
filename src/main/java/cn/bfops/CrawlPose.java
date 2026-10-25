package cn.bfops;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** NeoForge adds forced-pose methods at runtime; the offline compile jar is unpatched. */
final class CrawlPose {
    private static final Map<Player, Pose> previous = new WeakHashMap<>();
    private static final Method SET = method("setForcedPose", Pose.class);
    private static final Method GET = method("getForcedPose");
    private static Method method(String name, Class<?>... args) {
        try { return Player.class.getMethod(name, args); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("NeoForge forced-pose API missing", e); }
    }
    static synchronized void apply(Player player, boolean downed) {
        if (player == null) return;
        try {
            if (downed) {
                if (!previous.containsKey(player)) previous.put(player, (Pose) GET.invoke(player));
                SET.invoke(player, Pose.SWIMMING);
                player.setPose(Pose.SWIMMING);
                player.setSprinting(false);
            } else if (previous.containsKey(player)) {
                Pose old = previous.remove(player);
                SET.invoke(player, new Object[]{old});
                // Vanilla checks headroom and chooses standing/crouching on its next tick.
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot update downed pose", e);
        }
    }
}
