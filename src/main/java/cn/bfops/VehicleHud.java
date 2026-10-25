package cn.bfops;

import java.util.ArrayList;
import java.util.List;

/**
 * Rules for the vehicle HUD, deliberately free of Minecraft types so they can be tested headlessly.
 *
 * <p>Scope is deliberately narrow, per the requirement: show <b>friend or foe</b> and <b>whether it
 * can be deployed</b>. Health bars, low-health warnings and driver names were considered and left
 * out — a vehicle HUD that reports more than the player can act on is just clutter over the
 * crosshair.
 *
 * <p>Two decisions worth spelling out:
 *
 * <ul>
 *   <li><b>Unclaimed still has an owner.</b> A vehicle spawned by the blue team but not yet entered
 *       belongs to blue. Marking it neutral would invite teammates to shoot their own team's armour,
 *       so ownership is recorded at spawn and never derived from who is currently sitting in it.</li>
 *   <li><b>"Can deploy" is a timing question, not a budget one.</b> Vehicles cost a cooldown and a
 *       slot in the per-match cap, never personal score, so the HUD reports whether the cooldown has
 *       elapsed rather than whether the player can afford it.</li>
 * </ul>
 */
final class VehicleHud {
    /** Why a vehicle is or is not available, in the order the player will hit the blockers. */
    enum State { READY, COOLDOWN, MATCH_LIMIT, NOT_ALLOWED, WRONG_TEAM, NO_SITE }

    /**
     * One vehicle on the map.
     *
     * @param ownerTeam team that spawned it, {@code -1} if unknown
     * @param occupants 0 when parked
     */
    record Blip(int ownerTeam, double x, double z, int occupants, boolean destroyed) {
    }

    /**
     * Relation of a vehicle to the viewing player, as the HUD should colour it.
     *
     * <p>Spectators (viewer team {@code -1}) see everything as neutral: they have no side, and
     * colouring by someone else's team would be a lie.
     */
    static boolean friendly(Blip blip, int viewerTeam) {
        if (viewerTeam < 0 || blip.ownerTeam() < 0) return false;
        return blip.ownerTeam() == viewerTeam;
    }

    /** Whether the HUD should even draw this blip. Destroyed hulls are dropped, not drawn greyed. */
    static boolean visible(Blip blip) {
        return !blip.destroyed();
    }

    /**
     * Blips the viewer should see.
     *
     * <p>Enemy vehicles are always shown. Unlike enemy <i>players</i>, a vehicle is a large loud
     * object that the player can already see and hear; hiding it from the HUD while it is in plain
     * sight would be inconsistent rather than stealthy. This also means the HUD needs no line-of-sight
     * check, which keeps it honest on a server where the client cannot be trusted with positions.
     */
    static List<Blip> visibleFor(List<Blip> all, int viewerTeam) {
        List<Blip> out = new ArrayList<>();
        for (Blip blip : all) {
            if (visible(blip)) out.add(blip);
        }
        return out;
    }

    /**
     * Occupancy label: an empty vehicle is a different tactical object from an occupied one, so this
     * is worth the two characters.
     */
    static String occupancy(Blip blip) {
        return blip.occupants() > 0 ? "占用" : "空车";
    }

    /**
     * Whether the player may take a vehicle right now, and if not, why.
     *
     * <p>Order is the order the player would hit the blockers: whether the map allows the hull at all,
     * whether it is theirs, whether a spawn can launch it, then the cooldown they must wait out, and
     * last the match-wide cap they cannot fix alone.
     *
     * @param allowed       whether this map whitelists the vehicle at all
     * @param team          whether the vehicle belongs to the requesting player's team
     * @param siteAvailable whether the team holds a spawn that can launch this class of vehicle
     * @param cooldownTicks falling edge of the player's vehicle cooldown
     */
    static State availability(boolean allowed, boolean team, boolean siteAvailable,
                              int cooldownTicks, int matchCount, int matchLimit) {
        if (!allowed) return State.NOT_ALLOWED;
        if (!team) return State.WRONG_TEAM;
        if (!siteAvailable) return State.NO_SITE;
        if (cooldownTicks > 0) return State.COOLDOWN;
        if (matchCount >= matchLimit) return State.MATCH_LIMIT;
        return State.READY;
    }

    static boolean deployable(State state) {
        return state == State.READY;
    }

    /** Short HUD text. Numbers are included where the player could act on them. */
    static String describe(State state, int cooldownSeconds, int used, int limit) {
        return switch (state) {
            case READY -> "可部署 · 本方已用 " + used + "/" + limit;
            case COOLDOWN -> "冷却 " + cooldownSeconds + "s";
            case MATCH_LIMIT -> limit==0?"本方载具已禁用":"本方名额已满 " + limit;
            case NOT_ALLOWED -> "本图未授权";
            case WRONG_TEAM -> "非本方";
            case NO_SITE -> "无可用刷新点";
        };
    }

    /**
     * Whether a deployed vehicle would spawn a spare seat for a teammate.
     *
     * <p>Used only to decide whether the HUD hints at the option; the actual seat logic stays in
     * Superb Warfare.
     */
    static boolean shareable(Blip blip, int viewerTeam) {
        return friendly(blip, viewerTeam) && blip.occupants() == 0;
    }
}

