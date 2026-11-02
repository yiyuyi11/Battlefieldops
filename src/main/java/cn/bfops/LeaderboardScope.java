package cn.bfops;

/**
 * Which roster the 656 leaderboard is allowed to draw when the player is <em>not</em> in a match.
 *
 * <p>The reported problem was that the leaderboard refused to open outside a live match, and the message
 * it gave ("只有在当前对局内才可以查看计分板") was wrong twice over: it described a display limitation as a
 * permission rule, and it threw away data the client already held. The fix is to keep the screen
 * available at all times and be explicit about where the rows on it came from, because the two
 * available sources are not equally trustworthy:
 *
 * <ul>
 *   <li>{@link Source#LIVE_ROOM} — the current server snapshot. Authoritative: this is exactly what the
 *       room's own scorer reports, refreshed on every state push.</li>
 *   <li>{@link Source#LOCAL_CACHE} — rows this client wrote to disk the last time it saw a room on this
 *       server. Real numbers, but frozen at the moment that room closed, and possibly from a session
 *       days old. It must never be presented as a live table.</li>
 *   <li>{@link Source#NONE} — nothing has ever been seen here. Asking the server would be the
 *       alternative, but the parent mod deliberately exposes only the current room, so the honest answer
 *       is "no record yet" rather than a fabricated empty table that looks like everybody scored zero.</li>
 * </ul>
 *
 * <p>Kept as pure arithmetic with no Minecraft imports so the decision — which is the part that can be
 * wrong in a way players notice — is pinned by {@code LeaderboardAccessSim} on a bare JVM. The rendering
 * around it still needs an in-game look.
 */
public final class LeaderboardScope {
    private LeaderboardScope() { }

    /** Where the rows currently on screen came from. */
    public enum Source {
        /** Authoritative: the live server snapshot for the room this client is in. */
        LIVE_ROOM,
        /** This client's own record of the last room it saw on this server. */
        LOCAL_CACHE,
        /** Nothing to show yet on this server. */
        NONE;

        public boolean hasRows() {
            return this != NONE;
        }

        /** True when the table may be out of date, i.e. it needs a "cached" badge. */
        public boolean stale() {
            return this == LOCAL_CACHE;
        }
    }

    /**
     * Chooses the source for one open of the leaderboard.
     *
     * <p>A live roster always wins even when it is shorter than the cache: a room with three players in
     * it must show three, not last week's forty. The cache is only consulted when the live snapshot has
     * no rows at all, which is the out-of-match case this exists to serve.
     *
     * @param liveRows   rows the current server snapshot offers, 0 when there is no snapshot
     * @param cachedRows rows read back from this server's cache file
     */
    public static Source select(int liveRows, int cachedRows) {
        if (liveRows > 0) return Source.LIVE_ROOM;
        if (cachedRows > 0) return Source.LOCAL_CACHE;
        return Source.NONE;
    }

    /** Translation key for the badge that must appear whenever the table is not live. */
    public static String badgeKey(Source source) {
        return switch (source) {
            case LIVE_ROOM -> "battlefield_ops.client.gui.config.tab.scoreboard.source.live";
            case LOCAL_CACHE -> "battlefield_ops.client.gui.config.tab.scoreboard.source.cache";
            case NONE -> "battlefield_ops.client.gui.config.tab.scoreboard.source.empty";
        };
    }

    /** Translation key explaining the empty table, shown in the panel body when there is nothing to list. */
    public static String emptyHintKey(Source source) {
        return source == Source.LOCAL_CACHE
                ? "battlefield_ops.client.gui.config.tab.scoreboard.empty.cache"
                : "battlefield_ops.client.gui.config.tab.scoreboard.empty.none";
    }

    /**
     * Whether the board may be opened right now.
     *
     * <p>Always true. It stays a function rather than being deleted because "the leaderboard needs no
     * match" is the requirement being pinned by the test suite, and a single named place for the answer
     * survives future edits better than the absence of a check does.
     */
    public static boolean openable() {
        return true;
    }

    /**
     * Whether a page request to the server is worth sending.
     *
     * <p>Only when a connection exists. The request itself is answered with the current room's roster,
     * which is legitimately empty outside a match, so there is no match-state condition here: gating it
     * on {@code inMatch} is what made the tab show nothing at all while the player sat in a room between
     * rounds.
     */
    public static boolean requestable(boolean connected) {
        return connected;
    }
}
