package cn.bfops;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Layout logic for the full (Battlefield-style) scoreboard, kept free of every Minecraft type so it
 * can be tested with a bare JDK.
 *
 * <p>The screen itself is deliberately <b>not</b> a {@code Screen}: Battlefield's scoreboard is an
 * overlay you hold a key to read while still moving. Opening a Screen would steal the mouse, pause
 * input and force the player to release it before they could shoot again — the opposite of what the
 * overlay is for.
 *
 * <p>Ranking is by personal score, then kills, then name. The name tiebreak matters: two players on
 * 0 score and 0 kills are common at match start, and without it the list order would change between
 * refreshes for no visible reason.
 */
final class Scoreboard {
    /**
     * One player row.
     *
     * @param self whether this is the viewing player, marked so they can find themselves instantly
     */
    record Row(String name, int team, int kills, int deaths, int score, int captures,
               boolean alive, boolean self) {
    }

    static final int BLUE = 0xff5599ff;
    static final int RED = 0xffff6666;
    static final int NEUTRAL = 0xff9aa4ad;
    /** Dead players are drawn dim rather than removed: seeing who is down is part of the picture. */
    static final int DEAD = 0xff4a5259;

    /** Rows of one team, best first. */
    static List<Row> ranked(List<Row> rows, int team) {
        List<Row> out = new ArrayList<>();
        for (Row row : rows) {
            if (row.team() == team) out.add(row);
        }
        out.sort(Comparator.comparingInt(Row::score).reversed()
                .thenComparing(Comparator.comparingInt(Row::kills).reversed())
                .thenComparing(Row::name));
        return out;
    }

    /**
     * The rows that actually fit.
     *
     * <p>Capped rather than paginated on purpose: a scoreboard you have to scroll is not readable at
     * a glance, and the overflow count tells the player the rest exist.
     */
    static List<Row> top(List<Row> rows, int limit) {
        if (limit <= 0 || rows.isEmpty()) return List.of();
        return List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
    }

    /** How many players did not fit, so the panel can say so instead of silently hiding them. */
    static int overflow(List<Row> rows, int limit) {
        return Math.max(0, rows.size() - Math.max(0, limit));
    }

    /** Colour by team, dimmed when dead. Self keeps its team colour so the marker does the work. */
    static int color(Row row) {
        if (!row.alive()) return DEAD;
        return switch (row.team()) {
            case 0 -> BLUE;
            case 1 -> RED;
            default -> NEUTRAL;
        };
    }

    /** Display name, with a marker for the viewing player. */
    static String label(Row row) {
        return row.self() ? "▶ " + row.name() : row.name();
    }

    /** Compact right-aligned statistics column: kills, deaths, score. */
    static String stats(Row row) {
        return row.kills() + "/" + row.deaths() + "  " + row.score();
    }

    /** Total personal score of one team, shown in the team header. */
    static int teamScore(List<Row> rows, int team) {
        int total = 0;
        for (Row row : rows) {
            if (row.team() == team) total += row.score();
        }
        return total;
    }

    /** How many of a team are alive, which is the number a squad actually cares about. */
    static int teamAlive(List<Row> rows, int team) {
        int alive = 0;
        for (Row row : rows) {
            if (row.team() == team && row.alive()) alive++;
        }
        return alive;
    }
}
