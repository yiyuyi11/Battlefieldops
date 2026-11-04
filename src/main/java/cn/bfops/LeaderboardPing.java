package cn.bfops;

import net.minecraft.server.level.ServerPlayer;

/**
 * Round-trip time for the leaderboard's ping column.
 *
 * <p>1.20.1 exposes no accessor: the keep-alive sample lives in a private {@code latency} field on
 * {@code ServerGamePacketListenerImpl}. The field is looked up once and cached; if a future minor Forge
 * build renames it the column falls back to zero instead of failing the request.
 */
final class LeaderboardPing {
    private static java.lang.reflect.Field field;
    private static boolean looked;
    private static boolean warned;

    private LeaderboardPing() {}

    static int of(ServerPlayer player) {
        if (player == null || player.connection == null) return 0;
        if (!looked) {
            looked = true;
            try {
                field = player.connection.getClass().getDeclaredField("latency");
                field.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException missing) {
                field = null;
            }
        }
        if (field == null) {
            if (!warned) {
                warned = true;
                BattlefieldOps.LOG.warn("No latency field on the packet listener; leaderboard ping stays 0");
            }
            return 0;
        }
        try {
            return Math.max(0, field.getInt(player.connection));
        } catch (ReflectiveOperationException | RuntimeException readFailure) {
            return 0;
        }
    }
}
