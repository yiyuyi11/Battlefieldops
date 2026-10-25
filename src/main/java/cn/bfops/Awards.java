package cn.bfops;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * The combat awards that need attribution: headshot, multi-kill, assist and revenge.
 *
 * <p><b>Why attribution is needed at all.</b> A kill is easy to award — the server knows who landed the killing
 * blow. The other four are not: an assist requires remembering who else put damage in recently, revenge requires
 * remembering who killed you last, and a multi-kill requires remembering when your previous kill was. All of that
 * is bookkeeping, and bookkeeping is exactly the kind of thing that goes subtly wrong, so it lives here as pure
 * arithmetic over timestamps instead of being woven through the damage handler.
 *
 * <p><b>Windows, not scores.</b> Everything here is decided by two windows: how recently damage counts as an
 * assist, and how recently a kill counts as part of a streak. Both are per-map settings, because a slow
 * long-range map and a knife-fight map disagree about what "recently" means.
 */
final class Awards {
    /** Damage records kept per victim. Bounded so a long match cannot grow without limit. */
    static final int MAX_RECORDS = 32;

    /**
     * One damage contribution.
     *
     * @param attacker who dealt it
     * @param damage   how much, used only to skip zero-damage taps
     * @param at       wall-clock milliseconds
     */
    record Hit(UUID attacker, float damage, long at, double hitY) {}

    private Awards() { }

    /**
     * Who deserves an assist for a kill.
     *
     * <p>Everyone who damaged the victim inside the window except the killer, in the order they first landed
     * damage. The killer is excluded because they are already getting the kill: paying them twice for the same
     * death would make assists a worse deal than they look.
     */
    static List<UUID> assists(List<Hit> hits, UUID killer, long now, long windowMs) {
        LinkedHashSet<UUID> out = new LinkedHashSet<>();
        if (hits == null || windowMs <= 0) return List.of();
        for (Hit hit : hits) {
            if (hit == null || hit.attacker() == null) continue;
            if (hit.attacker().equals(killer)) continue;
            if (hit.damage() <= 0) continue;
            if (now - hit.at() > windowMs) continue;
            out.add(hit.attacker());
        }
        return new ArrayList<>(out);
    }

    /**
     * Height of the most recent blow on a victim, or {@code NaN} when nothing landed.
     *
     * <p>Used to judge a headshot from the damage that actually killed, rather than from where the attacker
     * happened to be standing.
     */
    static double lastHitY(List<Hit> hits) {
        if (hits == null || hits.isEmpty()) return Double.NaN;
        return hits.getFirst().hitY();
    }

    /** Appends a hit, dropping the oldest beyond {@link #MAX_RECORDS}. */
    static List<Hit> record(List<Hit> hits, Hit hit) {
        List<Hit> out = new ArrayList<>();
        if (hit != null && hit.attacker() != null && hit.damage() > 0) out.add(hit);
        if (hits != null) {
            for (Hit existing : hits) {
                if (out.size() >= MAX_RECORDS) break;
                if (existing != null) out.add(existing);
            }
        }
        return out;
    }

    /** Drops records older than the assist window, so the ledger does not grow across a whole match. */
    static List<Hit> prune(List<Hit> hits, long now, long windowMs) {
        List<Hit> out = new ArrayList<>();
        if (hits == null) return out;
        for (Hit hit : hits) {
            if (hit != null && now - hit.at() <= windowMs) out.add(hit);
        }
        return out;
    }

    /**
     * Whether a kill continues a streak.
     *
     * @param previousKillAt when this player last killed someone, or {@code 0} for never
     * @return the new streak length: 1 for an isolated kill, 2+ for a continuing one
     */
    static int streak(int previousStreak, long previousKillAt, long now, long windowMs) {
        if (previousKillAt <= 0 || windowMs <= 0) return 1;
        if (now - previousKillAt > windowMs) return 1;
        return Math.max(1, previousStreak) + 1;
    }

    /**
     * Chinese label for a streak length, matching how shooters name them.
     *
     * <p>Named counts up to five and then a bare counter: inventing a word for the seventh kill reads worse than
     * saying "连杀 x7".
     */
    static String streakLabel(int count) {
        // Anything at or below one is simply a kill; a caller that never tracked a streak must not get a
        // "连杀 x6" for its lack of data.
        if (count <= 1) return "击杀";
        return switch (count) {
            case 2 -> "双杀";
            case 3 -> "三杀";
            case 4 -> "四杀";
            case 5 -> "五杀";
            default -> "连杀 x" + Math.max(6, count);
        };
    }

    /**
     * Whether a killing blow landed on the head.
     *
     * <p>Decided by where the blow connected relative to the victim's height: the top portion of the bounding box
     * counts. This is an approximation, not a real hitbox test — damage events do not carry a body part — but it
     * is a stable one: it uses the actual impact position when the engine provides one, and the attacker's eye
     * height as the melee fallback, so it never reports a headshot for a shot into the chest.
     */
    static boolean headshot(double hitY, double minY, double maxY) {
        double height = maxY - minY;
        if (height <= 0) return false;
        // Top 22% of the body: above the shoulder line for a standard player, and never true for a hit at the
        // feet or waist.
        return hitY >= minY + height * 0.78;
    }

    /** Whether the kill is revenge: this player's last death was caused by the player they just killed. */
    static boolean revenge(UUID lastKillerOfKiller, UUID victim) {
        return lastKillerOfKiller != null && lastKillerOfKiller.equals(victim);
    }
}
