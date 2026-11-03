package cn.bfops;

/**
 * Rate-limited slow-path logging.
 *
 * <p>A UI that stutters is almost always one expensive call repeated per frame, and the call is rarely
 * where it looks: parsing state, rebuilding widgets, or a per-pixel fill loop. Rather than guess, the
 * screen shell and the icon cache report any single operation that exceeds a threshold, at most once a
 * second so a permanent problem cannot flood the log. Everything under the threshold stays silent.
 */
final class PerfProbe {
    private static final long THRESHOLD_NANOS = 20_000_000L;
    private static long lastLogged;

    private PerfProbe() {}

    static void mark(String section, long nanos) {
        if (nanos < THRESHOLD_NANOS) return;
        long now = System.currentTimeMillis();
        if (now - lastLogged < 1000) return;
        lastLogged = now;
        BattlefieldOps.LOG.warn("[bfops-perf] {} took {} ms", section, nanos / 1_000_000L);
    }
}
