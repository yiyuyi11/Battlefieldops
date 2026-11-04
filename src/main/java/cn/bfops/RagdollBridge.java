package cn.bfops;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Ragdoll corpses are a Sable feature and Sable has no 1.20.1 build, so this port keeps the call site and
 * does nothing here.
 *
 * <p>Deliberately a no-op rather than a stand-in body: a fake corpse entity that cannot be posed or
 * simulated would look worse than the vanilla death animation it replaces, and it would need its own
 * despawn, collision and cleanup rules.
 */
final class RagdollBridge {
    private RagdollBridge() {}

    static void corpse(ServerPlayer player, Vec3 at, Vec3 push) {
        // Intentionally empty on 1.20.1.
    }
}
