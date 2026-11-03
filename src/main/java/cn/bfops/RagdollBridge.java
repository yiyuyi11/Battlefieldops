package cn.bfops;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Leaves a real physics ragdoll behind when a player dies, if Sable's ragdoll addon is installed.
 *
 * <h2>Why a dummy session and not the player</h2>
 *
 * <p>{@code RagdollAPI.launch(player, impulse)} turns the <em>player</em> into the ragdoll, which fights
 * this mod's death flow: the player is moved to spectator and the deploy camera takes over the same frame,
 * so the session would be torn down immediately. {@code spawnPlayerless} leaves a standalone dummy with
 * the player's own profile (skin included) and an impulse, while the player continues into spectator and
 * redeploy as usual. The equipment is copied onto the dummy first, so the body you walk past afterwards is
 * wearing what you were wearing.
 *
 * <p>All reflective: the addon is optional, so this class must load whether or not it is present.
 */
final class RagdollBridge {
    private static Boolean available;
    /** Long enough to be seen after the deploy screen opens, short enough not to litter the map. */
    private static final int DESPAWN_TICKS = 240;

    private RagdollBridge() {}

    static boolean available() {
        if (available == null) {
            boolean found = false;
            try {
                found = net.neoforged.fml.ModList.get().isLoaded("sable_player_ragdoll")
                        && Class.forName("dev.leo.sableplayerragdoll.api.RagdollAPI") != null;
            } catch (ClassNotFoundException | LinkageError | RuntimeException ex) {
                BattlefieldOps.LOG.debug("Sable ragdoll API unavailable", ex);
            }
            available = found;
            if (found) BattlefieldOps.LOG.info("[bfops] Sable ragdoll API found; death bodies are physics ragdolls");
        }
        return available;
    }

    /**
     * Drops a dummy ragdoll at {@code at}, pushed slightly away from the killer.
     *
     * @param push direction the body should fall toward, normally the killer's bearing; may be zero
     */
    static void corpse(ServerPlayer player, Vec3 at, Vec3 push) {
        if (!available()) return;
        try {
            Class<?> api = Class.forName("dev.leo.sableplayerragdoll.api.RagdollAPI");
            Class<?> ruleType = Class.forName("dev.leo.sableplayerragdoll.api.PlayerlessDespawnRule");
            Class<?> sessionType = Class.forName("dev.leo.sableplayerragdoll.api.PlayerlessRagdollSession");
            Object despawn = ruleType.getMethod("afterTicks", int.class).invoke(null, DESPAWN_TICKS);

            Vec3 impulse = push.lengthSqr() > 1e-4 ? push.normalize().scale(0.45).add(0, 0.18, 0)
                    : new Vec3(0, 0.18, 0);
            Method spawn = api.getMethod("spawnPlayerless", ServerLevel.class, Vec3.class, double.class,
                    com.mojang.authlib.GameProfile.class, Vec3.class, ruleType);
            Object session = spawn.invoke(null, player.serverLevel(), at, 1.0d, player.getGameProfile(), impulse, despawn);
            if (session == null) return;

            UUID id = (UUID) sessionType.getMethod("id").invoke(session);
            // Copy the kit onto the dummy before the death path clears the real inventory.
            api.getMethod("copyEquipmentFrom", ServerLevel.class, UUID.class, net.minecraft.world.entity.player.Player.class)
                    .invoke(null, player.serverLevel(), id, player);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            BattlefieldOps.LOG.warn("Cannot leave a ragdoll body; falling back to no body", ex);
            available = false;
        }
    }
}
