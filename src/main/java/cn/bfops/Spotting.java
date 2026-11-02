package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * Marking rules: what can be marked, how a mark is drawn, and how it ages out.
 *
 * <p><b>Two kinds, on purpose.</b> The mark key marks an enemy — that is the useful callout, and it is why the
 * key exists. But the same key is the natural way to say "come here" to a teammate, and the reference pack
 * shares one key for both. So a mark carries a {@link Kind}, and the two are drawn differently: an enemy mark
 * is a red inverted triangle, a teammate mark is the teammate's own blue. That is a shape difference and a
 * colour difference, which matters because colour alone fails for the most common form of colour blindness.
 *
 * <p><b>Why this is separate and pure.</b> These are the decisions that make a tactical map fair: an enemy may
 * only appear where somebody on your team could actually see them, and a manual mark has to expire so it cannot
 * become a permanent wall-hack. Both are arithmetic, so both live here where they can be checked headless
 * instead of only being observable by playing.
 *
 * <p><b>Sharing is deliberate.</b> A contact a player can see appears on their own map automatically — that is
 * their own eyes. It does <em>not</em> appear on a teammate's map, and no automatic team-wide exposure exists:
 * a teammate learns about it only when somebody presses the mark key. Info that spreads for free would make the
 * key pointless and would hand every squad a radar they never had to work for.
 *
 * <p>Line of sight itself is the engine's {@code hasLineOfSight}, so "unobstructed" means what the game means by
 * it — terrain, walls and vehicles all block it. A teammate mark is exempt: you do not need to see a friendly
 * to tell the squad where they are, and a friendly is not secret information.
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
    static final double MARK_RANGE = 150.0;
    /**
     * How far from the crosshair an enemy may be and still be marked.
     *
     * <p>Generous enough to mark someone you are obviously looking at, tight enough that the key cannot be
     * used to tag something behind you.
     */
    static final double MARK_CONE_DEGREES = 12.0;
    /**
     * How far a teammate may be and still be marked.
     *
     * <p>Wider than the enemy cone and not restricted to the crosshair: a teammate mark is a request, and
     * "look at roughly where they are and press the key" is a coarser gesture than "put the crosshair on that
     * enemy". Deliberately bounded by {@link Tactics#MATE_GLOW_RANGE} so the key cannot reach across the map.
     */
    static final double MATE_MARK_RANGE = Tactics.MATE_GLOW_RANGE;

    private Spotting() { }

    /** Which side of the relationship a mark is about. */
    enum Kind {
        /** An enemy position somebody called out. Drawn as a red inverted triangle. */
        ENEMY,
        /** A teammate asking to be joined or supported. Drawn in the teammate blue. */
        MATE, POSITION, OBJECTIVE, VEHICLE, REQUEST_AMMO, REQUEST_HEAL;

        static Kind of(String name) {
            try{return valueOf(name==null?"ENEMY":name.toUpperCase(java.util.Locale.ROOT));}catch(IllegalArgumentException ignored){return ENEMY;}
        }

        String id() {
            return name();
        }
    }

    /**
     * One mark: a position recorded for a team until it expires.
     *
     * @param team      team that may see it, or {@code -1} for both
     * @param expiresAt wall-clock milliseconds
     * @param kind      enemy callout or teammate request
     * @param name      display name of whoever was marked, or an empty string when unknown
     */
    record Mark(int team, double x, double z, long expiresAt, Kind kind, String name, double y,
                String target, String kit, String action) {
        Mark(int team,double x,double z,long expiresAt,Kind kind,String name,double y){this(team,x,z,expiresAt,kind,name,y,"","","");}
        Mark(int team, double x, double z, long expiresAt, Kind kind, String name) {
            this(team,x,z,expiresAt,kind,name,64);
        }
        Mark(int team, double x, double z, long expiresAt) {
            this(team, x, z, expiresAt, Kind.ENEMY, "");
        }

        Mark {
            if (kind == null) kind = Kind.ENEMY;
            if (name == null) name = "";
        }

        boolean active(long now) {
            return now < expiresAt;
        }

        /** Whether this mark should be drawn for a viewer on {@code viewerTeam}. */
        boolean visibleTo(int viewerTeam) {
            return team < 0 || team == viewerTeam;
        }

        /** Milliseconds left before this mark expires; never negative. */
        long leftMs(long now) {
            return Math.max(0, expiresAt - now);
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

    /**
     * Whether a teammate is close enough to be marked, and how the mark key picks between candidates.
     *
     * <p>Nearest wins, because the key means "this person". No cone test: a teammate mark is a request to
     * regroup, and requiring the crosshair to be on them would make it hard to use while running.
     *
     * @param dx      candidate minus viewer, x
     * @param dz      candidate minus viewer, z
     * @param inRange whether the candidate is already inside {@link #MATE_MARK_RANGE}
     */
    static boolean markableMate(double dx, double dz, boolean inRange) {
        if (!inRange) return false;
        return Math.hypot(dx, dz) <= MATE_MARK_RANGE;
    }
}
