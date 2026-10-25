package cn.bfops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Coexistence policy with the proxy-side TAB plugin and with other HUD mods (Xaero).
 *
 * <p>Two surfaces on this server are not ours to own unconditionally:
 *
 * <ul>
 *   <li><b>TAB</b> runs on the Velocity proxy and drives both {@code DisplaySlot.SIDEBAR} and player
 *       team membership. A player can only belong to <i>one</i> scoreboard team, so whoever assigns
 *       last wins and the other side's settings (nametag visibility, friendly fire) silently stop
 *       applying. Two writers to one sidebar likewise produce flicker or a layout that snaps back
 *       every second.</li>
 *   <li><b>Xaero's minimap</b> already draws a minimap in a screen corner, so drawing a second one
 *       is redundant.</li>
 * </ul>
 *
 * <p>This class is only the adapter: it reads live scoreboard state and hands it to
 * {@link CompatPolicy}, which owns the decisions and is testable without a running server. The
 * split exists because these rules fail silently rather than loudly.
 *
 * <p>Nothing here guesses whether TAB is installed. A Velocity plugin leaves no marker on the
 * backend, so the mod list cannot answer that question; observing who actually owns the surface can.
 */
final class Compat {
    /** Persisted settings; stored as {@code world/battlefield_ops/compat.json}. */
    static final class Settings {
        public CompatPolicy.Mode sidebar = CompatPolicy.Mode.AUTO;
        public CompatPolicy.Mode teams = CompatPolicy.Mode.AUTO;
        public CompatPolicy.Mode minimap = CompatPolicy.Mode.AUTO;
        /**
         * Whether clients of this server are expected to run Xaero's minimap.
         *
         * <p>Xaero is client-side, so the server cannot probe for it — this is a declaration by the
         * operator, not a detection. It defaults to true because both known packs ship Xaero, and
         * assuming it present only costs a corner of the screen while assuming it absent causes two
         * minimaps to be drawn on top of each other.
         */
        public boolean xaeroMinimap = true;
        /**
         * Whether Xaero's minimap is hidden while a match is running.
         *
         * <p>Separate from {@link #xaeroMinimap}: that one means "assume Xaero is installed", this one
         * means "suppress it during matches". A pack can hold both — the mod yields the corner to Xaero
         * outside matches, and hides it during them so the match map is the only map.</p>
         *
         * <p>Hiding shows a full-world map with waypoints and entity radar, which is exactly the
         * information a scored match does not hand out, so this defaults on.</p>
         */
        public boolean hideXaeroInMatch = true;
        /** Minimap edge length in GUI pixels. */
        public int minimapSize = 120;
        /**
         * Which screen corner the HUD minimap sits in.
         *
         * <p>{@code TOP_LEFT} by default: the top-centre band is reserved for the ticket bar and the
         * objective cards, and the bottom of the screen carries the capture-progress bar and the
         * objective hint — the two corners that stay legible are the left ones. Anything unrecognised
         * falls back to this, so a hand-edited config file cannot leave the minimap undrawable.
         */
        public String minimapCorner = "TOP_LEFT";
        /** Negative means "choose a corner". */
        public int minimapX = -1;
        public int minimapY = -1;
        public int hudX = 8;
        public int hudY = 24;
        /** One log line per yielded surface, so the reason is discoverable. */
        public boolean logDecisions = true;
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private final CompatPolicy policy = new CompatPolicy();
    private final Settings settings;
    private final Path file;
    private final List<String> notes = new ArrayList<>();

    Compat(Settings settings, Path file) {
        this.settings = settings == null ? new Settings() : settings;
        this.file = file;
    }

    static Compat load(Path folder, org.slf4j.Logger log) {
        Path target = folder.resolve("compat.json");
        Settings loaded = null;
        if (Files.isRegularFile(target)) {
            try {
                loaded = GSON.fromJson(Files.readString(target, StandardCharsets.UTF_8), Settings.class);
            } catch (Exception ex) {
                // A corrupt compat file must never stop a match; the defaults are the safe choice.
                log.warn("无法读取兼容配置，改用默认值：{}", target, ex);
            }
        }
        Compat compat = new Compat(loaded, target);
        if (!Files.isRegularFile(target)) compat.save(log);
        return compat;
    }

    void save(org.slf4j.Logger log) {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(settings), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("无法写入兼容配置：{}", file, ex);
        }
    }

    Settings settings() {
        return settings;
    }

    CompatPolicy policy() {
        return policy;
    }

    List<String> notes() {
        return List.copyOf(notes);
    }

    void reset() {
        policy.reset();
        notes.clear();
    }

    private void note(String line, org.slf4j.Logger log) {
        if (notes.contains(line)) return;
        notes.add(line);
        if (settings.logDecisions) log.info("[battlefield_ops] 兼容：{}", line);
    }

    /** Reads the live sidebar slot so the pure core can decide. */
    boolean useSidebar(Scoreboard scoreboard, String ourObjectiveName, org.slf4j.Logger log) {
        Objective shown = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        String shownName = shown == null ? null : shown.getName();
        boolean before = policy.foreignSidebar();
        boolean allowed = policy.mayUseSidebar(settings.sidebar, shownName, ourObjectiveName);
        if (!before && policy.foreignSidebar()) {
            note("侧边栏由其它插件占用（" + shownName + "），改用 HUD 显示比分", log);
        }
        return allowed;
    }

    /** Needs the player's current team, which only the scoreboard knows. */
    boolean useTeams(PlayerTeam current, org.slf4j.Logger log) {
        String name = current == null ? null : current.getName();
        boolean before = policy.foreignTeams();
        boolean allowed = policy.mayUseTeams(settings.teams, name, "bfops_");
        if (!before && policy.foreignTeams()) {
            note("队伍由其它插件管理（" + name + "），已停止接管队伍与名牌", log);
        }
        return allowed;
    }

    /** Xaero is client-side, so its presence is a pack fact rather than something we probe. */
    boolean useMinimap(org.slf4j.Logger log) {
        boolean allowed = policy.mayUseMinimap(settings.minimap, settings.xaeroMinimap);
        if (!allowed) note("已让出小地图，避免与 Xaero 小地图重叠（规则页可强制开启）", log);
        return allowed;
    }

    /** Full state for the GUI, including the plain-language list of what was yielded. */
    JsonObject report(boolean tacz, boolean superbwarfare, String serverModName) {
        JsonObject out = policy.report(settings.sidebar, settings.teams, settings.minimap);
        out.addProperty("xaeroAssumed", settings.xaeroMinimap);
        // Where the HUD minimap is drawn. Reported so the settings page can show the current corner and
        // cycle from it, rather than always offering the same "next" without saying "now".
        out.addProperty("minimapCorner", Briefing.normaliseCorner(settings.minimapCorner));
        out.addProperty("tacz", tacz);
        out.addProperty("superbwarfare", superbwarfare);
        out.addProperty("serverModName", serverModName);
        out.addProperty("file", file == null ? "" : file.toString());
        JsonArray list = new JsonArray();
        for (String note : notes) list.add(note);
        out.add("notes", list);
        return out;
    }

    String toJson() {
        return GSON.toJson(settings);
    }
}
