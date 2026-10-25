package cn.bfops;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vanilla scoreboard plumbing for team identity, name colours and the side bar.
 *
 * <p>Everything here is standard server scoreboard state, so the client needs no mod-side code to
 * see team colours, the coloured player list or the side bar. That is why this approach was chosen
 * over a custom HUD: it survives a client without the mod, and it matches how the reference mod
 * presented teams.
 *
 * <p>Two invariants the caller relies on:
 * <ul>
 *   <li>Team names are namespaced ({@code bfops_<map>_blue}), so leaving a match never strips a
 *       server's own teams.</li>
 *   <li>Every player this class touched is recorded in {@link #joined}, so {@link #clearPlayer} can
 *       undo it exactly. Nothing is guessed from the current scoreboard contents.</li>
 * </ul>
 */
final class Board {
    /** Blue is team 0, red is team 1; anything else is a spectator with no team. */
    static final ChatFormatting[] COLORS = {ChatFormatting.BLUE, ChatFormatting.RED};
    static final String[] LABELS = {"蓝方", "红方"};

    private static final String OBJECTIVE = "bfops";

    private final MinecraftServer server;
    /**
     * Compatibility policy, or {@code null} before the engine finishes wiring (the board is created
     * early). Null means "no policy known yet", which is treated as permissive so nothing silently
     * stops working during construction.
     */
    private Compat compat;
    private Objective objective;
    /** Player name -> team name currently applied by this class. */
    private final Map<String, String> joined = new HashMap<>();
    /** Player name -> score currently written, so unchanged rows are not rewritten every second. */
    private final Map<String, String> rendered = new HashMap<>();

    Board(MinecraftServer server) {
        this.server = server;
    }

    void compat(Compat compat) {
        this.compat = compat;
    }

    static String teamName(String map, int team) {
        return "bfops_" + map.toLowerCase(Locale.ROOT) + (team == 0 ? "_blue" : "_red");
    }

    static ChatFormatting color(int team) {
        return team == 0 || team == 1 ? COLORS[team] : ChatFormatting.GRAY;
    }

    static String label(int team) {
        return team == 0 || team == 1 ? LABELS[team] : "未选队";
    }

    private Scoreboard board() {
        return server.getScoreboard();
    }

    /**
     * Puts a player on their team and gives them the team colour, the coloured prefix and the
     * configured nametag visibility. Friendly fire on the scoreboard is disabled independently of
     * the hurt hook so vanilla mechanics agree with {@link Engine#blockDamage}.
     *
     * <p>{@code HIDE_FOR_OTHER_TEAMS} is exactly the "enemies cannot read my nametag, teammates
     * can" rule, and vanilla already implements it per viewer 鈥?unlike glowing, which is global.
     */
    void apply(ServerPlayer player, String map, int team, boolean friendlyFire, boolean hideEnemyNametags) {
        Scoreboard scoreboard = board();
        String name = player.getScoreboardName();
        String wanted = teamName(map, team);

        // TAB on the proxy also assigns teams, and vanilla allows one team per player. If TAB owns
        // this player, taking them over would break TAB's nametag and tab-list formatting and TAB
        // would simply put them back 鈥?a fight that shows up as a flickering name, not as an error.
        // In that case we keep our own bookkeeping but leave the scoreboard alone.
        if (compat != null && !compat.useTeams(scoreboard.getPlayerTeam(name), BattlefieldOps.LOG)) {
            joined.put(name, wanted);
            return;
        }

        PlayerTeam existing = scoreboard.getPlayerTeam(name);
        if (existing != null && !existing.getName().equals(wanted)) {
            scoreboard.removePlayerFromTeam(name);
        }
        PlayerTeam target = scoreboard.getPlayerTeam(wanted);
        if (target == null) target = scoreboard.addPlayerTeam(wanted);
        target.setColor(color(team));
        target.setDisplayName(Component.literal(label(team) + " 路 " + map));
        target.setPlayerPrefix(Component.literal("[" + label(team) + "] ").withStyle(color(team)));
        target.setAllowFriendlyFire(friendlyFire);
        target.setNameTagVisibility(hideEnemyNametags
                ? Team.Visibility.HIDE_FOR_OTHER_TEAMS : Team.Visibility.ALWAYS);
        target.setCollisionRule(Team.CollisionRule.ALWAYS);
        if (!target.getPlayers().contains(name)) scoreboard.addPlayerToTeam(name, target);
        joined.put(name, wanted);
    }

    /**
     * Whether team identity should be shown by this mod at all. When false the caller still tracks
     * teams for gameplay but must not rely on scoreboard colours or nametag visibility, because the
     * proxy plugin is presenting them instead.
     */
    boolean teamsEnabled() {
        return compat == null || !compat.policy().foreignTeams()
                && compat.settings().teams != CompatPolicy.Mode.NEVER;
    }

    /** Removes one player from whatever this class put them in, leaving foreign teams alone. */
    void clearPlayer(ServerPlayer player) {
        String name = player.getScoreboardName();
        String owned = joined.remove(name);
        if (owned == null) return;
        PlayerTeam current = board().getPlayerTeam(name);
        if (current != null && current.getName().equals(owned)) board().removePlayerFromTeam(name);
        // Side bar rows use synthetic holder names, never the player name, so there is no score to
        // clear here. Calling resetAllPlayerScores would wipe this player's scores in *other*
        // plugins' objectives, which is not ours to touch.
        if (current != null && current.getPlayers().isEmpty()) board().removePlayerTeam(current);
    }

    /** Removes every team this map created, whether or not the players are still online. */
    void clearMap(String map) {
        for (int team = 0; team < 2; team++) {
            PlayerTeam existing = board().getPlayerTeam(teamName(map, team));
            if (existing == null) continue;
            for (String name : new ArrayList<>(existing.getPlayers())) {
                board().removePlayerFromTeam(name);
                joined.remove(name);
                rendered.remove(name);
            }
            board().removePlayerTeam(existing);
        }
    }

    void clearAll() {
        for (String name : new ArrayList<>(joined.keySet())) {
            PlayerTeam current = board().getPlayerTeam(name);
            if (current != null) board().removePlayerFromTeam(name);
        }
        for (String team : generatedTeams()) {
            PlayerTeam existing = board().getPlayerTeam(team);
            if (existing != null) board().removePlayerTeam(existing);
        }
        joined.clear();
        rendered.clear();
        if (objective != null) {
            // Same rule as hideSidebar: never clear a slot someone else is displaying.
            Objective shown = board().getDisplayObjective(DisplaySlot.SIDEBAR);
            if (shown == objective) board().setDisplayObjective(DisplaySlot.SIDEBAR, null);
            board().removeObjective(objective);
            objective = null;
        }
    }

    /**
     * Rewrites the side bar. Rows are keyed by a stable synthetic holder name and the display text
     * carries the actual numbers, which is the only way to show composite lines on a vanilla side
     * bar.
     *
     * <p><b>One side bar exists per server, not per player.</b> Two matches running at once cannot
     * both own {@link DisplaySlot#SIDEBAR}, so the caller must only call this when a single match is
     * live; otherwise the engine falls back to the per-player HUD, which has no such limitation.
     */
    void sidebar(List<Map.Entry<String, String>> rows, String title) {
        Scoreboard scoreboard = board();
        // Yielding to TAB is silent if we just skip the write, so the caller also needs to know
        // whether the sidebar is actually being shown; it falls back to the HUD either way.
        if (compat != null && !compat.useSidebar(scoreboard, OBJECTIVE, BattlefieldOps.LOG)) {
            hideSidebar();
            return;
        }
        if (objective == null) {
            objective = scoreboard.getObjective(OBJECTIVE);
            if (objective == null) {
                objective = scoreboard.addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY,
                        Component.literal(title), ObjectiveCriteria.RenderType.INTEGER, false, null);
            }
        }
        objective.setDisplayName(Component.literal(title));
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            String key = rows.get(i).getKey();
            String text = rows.get(i).getValue();
            if (!seen.add(key)) continue;
            ScoreHolder holder = ScoreHolder.forNameOnly(key);
            var score = scoreboard.getOrCreatePlayerScore(holder, objective);
            // Descending scores keep the list in the caller's order without re-sorting on the client.
            score.set(1000 - i);
            if (!text.equals(rendered.get(key))) {
                score.display(Component.literal(text));
                rendered.put(key, text);
            }
        }
        for (String key : new ArrayList<>(rendered.keySet())) {
            if (seen.contains(key)) continue;
            scoreboard.resetAllPlayerScores(ScoreHolder.forNameOnly(key));
            rendered.remove(key);
        }
    }

    /** Whether this mod's objective is the one currently on the sidebar slot. */
    boolean sidebarVisible() {
        if (objective == null) return false;
        Objective shown = board().getDisplayObjective(DisplaySlot.SIDEBAR);
        return shown != null && shown.getName().equals(OBJECTIVE);
    }

    /** Hides and forgets the side bar, used when a second match starts or the last one ends. */
    void hideSidebar() {
        // Only clear the slot if we are the ones displaying it. Wiping a slot TAB owns would blank
        // the proxy's sidebar, which is exactly the cross-plugin damage this class exists to avoid.
        if (objective != null) {
            Objective shown = board().getDisplayObjective(DisplaySlot.SIDEBAR);
            if (shown == objective) board().setDisplayObjective(DisplaySlot.SIDEBAR, null);
        }
        for (String key : new ArrayList<>(rendered.keySet())) {
            board().resetAllPlayerScores(ScoreHolder.forNameOnly(key));
        }
        rendered.clear();
    }

    /** Teams this class created, for the shutdown sweep. */
    List<String> generatedTeams() {
        return board().getPlayerTeams().stream()
                .map(PlayerTeam::getName)
                .filter(name -> name.startsWith("bfops_"))
                .toList();
    }
}
