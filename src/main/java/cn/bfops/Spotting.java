package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * Spotting rules: what an enemy contact is allowed to be drawn from, and how a manual mark ages out.
 *
 * <p><b>Why this is separate and pure.</b> These are the decisions that make a tactical map fair: an enemy
 * may only appear where somebody on your team could actually see them, and a manual mark has to expire so it
 * cannot become a permanent wall-hack. Both are arithmetic, so both live here where they can be checked
 * headless instead of only being observable by playing.
 *
 * <p><b>Sharing is deliberate.</b> A contact a player can see appears on their own map automatically — that is
 * their own eyes. It does <em>not</em> appear on a teammate's map, and no automatic team-wide exposure exists:
 * a teammate learns about it only when somebody presses the mark key. Info that spreads for free would make
 * the key pointless and would hand every squad a radar they never had to work for.
 *
 * <p>Line of sight itself is the engine's {@code hasLineOfSight}, so "unobstructed" means what the game means
 * by it — terrain, walls and vehicles all block it.
 */
final class Spotting {
    /**
     * Fallback mark lifetime, used only where no map is available.
     *
     * <p>The live value is {@code Arena#markSeconds}, which the operator sets per map; this constant keeps the
     * "how long is a mark" question answerable in one place for tests and for code that has no map handle.
     */
    static final long MARK_LIFETIME_MS = 5_000L;
    /** Hard cap on marks per team, so a held key cannot fill the map. */
    static final int MAX_MARKS = 16;
    /**
     * How far from the crosshair an enemy may be and still be marked.
     *
     * <p>Generous enough to mark someone you are obviously looking at, tight enough that the key cannot be
     * used to tag something behind you.
     */
    static final double MARK_CONE_DEGREES = 12.0;

    private Spotting() { }

    /**
     * One mark: a position recorded for a team until it expires.
     *
     * @param team      team that may see it, or {@code -1} for both
     * @param expiresAt wall-clock milliseconds
     */
    record Mark(int team, double x, double z, long expiresAt) {
        boolean active(long now) {
            return now < expiresAt;
        }

        /** Whether this mark should be drawn for a viewer on {@code viewerTeam}. */
        boolean visibleTo(int viewerTeam) {
            return team < 0 || team == viewerTeam;
        }
    }

    /**
     * Drops expired marks and enforces the per-team cap.
     *
     * <p>Newest survive when the cap is hit: a fresh mark is the one somebody just called out, while an old
     * one is about to expire anyway.
     */
    static List<Mark> prune(List<Mark> marks, long now) {
        if (marks == null || marks.isEmpty()) return List.of();
        List<Mark> live = new ArrayList<>();
        for (Mark mark : marks) {
            if (mark != null && mark.active(now)) live.add(mark);
        }
        if (live.size() <= MAX_MARKS) return live;
        return new ArrayList<>(live.subList(live.size() - MAX_MARKS, live.size()));
    }

    /** Marks a viewer on {@code viewerTeam} may draw. */
    static List<Mark> visibleTo(List<Mark> marks, int viewerTeam, long now) {
        List<Mark> out = new ArrayList<>();
        if (marks == null) return out;
        for (Mark mark : marks) {
            if (mark != null && mark.active(now) && mark.visibleTo(viewerTeam)) out.add(mark);
        }
        return out;
    }

    /** Whether a point lies inside a view cone, used to decide whether a mark gesture hit anything. */
    static boolean inCone(double headingRad, double halfAngleRad, double dx, double dz) {
        double length = Math.hypot(dx, dz);
        if (length < 1e-6) return true;
        // The world's heading convention: yaw 0 looks towards +Z, increasing yaw turns towards -X.
        double cos = (-Math.sin(headingRad) * dx + Math.cos(headingRad) * dz) / length;
        double angle = Math.acos(Math.max(-1, Math.min(1, cos)));
        return angle <= halfAngleRad;
    }
}
