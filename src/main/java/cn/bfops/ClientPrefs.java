package cn.bfops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Per-player display preferences that live on the client.
 *
 * <p><b>Why these are not server settings.</b> The operator already chooses a default corner in the compat
 * settings, and that is the right place for "what this server looks like". But which corner a player finds
 * comfortable depends on their own screen, their FOV and what other HUD mods they run — it is not something
 * a server can decide for everyone. So a client value, when set, wins; when unset the server default
 * applies. That keeps one server-wide baseline without forcing every player onto it.
 *
 * <p>Stored beside the mod's other config rather than in the world: it is a client preference and must
 * survive switching servers.
 */
final class ClientPrefs {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data;

    /** Persisted shape. Missing fields keep their defaults, so an older file stays readable. */
    static final class Data {
        /**
         * Chosen corner, or an empty string for "follow the server".
         *
         * <p>Empty rather than {@code "TOP_LEFT"} so that "I never touched this" stays distinguishable from
         * "I deliberately chose top-left"; only the former should track the operator's setting.
         */
        public String minimapCorner = "";
        /** Hides this mod's HUD minimap without disabling the rest of the HUD. */
        public boolean minimapHidden = false;
        /** Show the death screen while downed instead of the crawl HUD; on by default. */
        public boolean deathOverDowned = true;
        /** File name (no extension) of a PNG in config/battlefield_ops/backgrounds, or "". */
        public String screenBackground = "";
        /** Size of the arrow that marks the player on the map, in pixels. */
        public float selfArrowSize = 9.0F;
        public boolean distantGunCull = true;
        public boolean bulletWhizz=true,suppressionEffects=true;
        public MinimapOptions minimap=new MinimapOptions();
        /** Rotates the map so the player's facing is up, instead of north-up. */
        public boolean mapRotation = true;
        /**
         * Battle sound effects: captures, shells, beacons, sensors.
         *
         * <p>Default on. This is the layer that carries information, so it starts enabled; a player who
         * dislikes it can turn it off without losing the HUD.
         */
        public boolean sfx = true;
        /**
         * Announcer and soldier callouts.
         *
         * <p>Default on, because the reference pack's callouts are part of the experience this mod is
         * reproducing. Separate from {@link #sfx} so a player who does not want English/Russian voice lines
         * over their game keeps the confirmation sounds.
         */
        public boolean voice = true;
        /**
         * Background music and loops.
         *
         * <p>Default on, matching the reference. Separate for the obvious reason: most players already run
         * their own music, and the reference itself lets its lobby and battle beds be switched off
         * independently.
         */
        public boolean music = true;
        /**
         * The chosen kill-icon preset id, empty for the built-in default.
         *
         * <p>An empty string rather than an id so "never chose one" stays distinguishable from "chose the
         * default", which is the same rule {@link #minimapCorner} follows. Ids are the pack's own file numbers
         * ({@code "00037"}), so a preset selected here can be traced to the file it came from.
         */
        public String killIconPreset = "";
        /**
         * Which {@code valorant} weapon finish to use, empty for the first.
         *
         * <p>Only consulted by that one mode, which ships cosmetic finishes rather than an icon set. Kept
         * separate from {@link #killIconPreset} so switching presets away and back remembers the finish instead
         * of resetting it.
         */
        public String valorantFinish = "";
    }

    private ClientPrefs() { }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("battlefield_ops-client.json");
    }

    static synchronized Data data() {
        if (data != null) return data;
        Path file = file();
        Data loaded = null;
        if (Files.isRegularFile(file)) {
            try {
                loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
            } catch (IOException | JsonSyntaxException ex) {
                // A broken preference file must never stop the mod; defaults are always safe here.
                BattlefieldOps.LOG.warn("Cannot read client preferences, using defaults", ex);
            }
        }
        data = loaded == null ? new Data() : loaded;
        if (data.minimapCorner == null) data.minimapCorner = "";
        return data;
    }

    private static synchronized void persist() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(data()), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            BattlefieldOps.LOG.warn("Cannot save client preferences", ex);
        }
    }

    /**
     * The corner to draw the minimap in: the player's own choice when they have made one, otherwise the
     * server's.
     */
    static String corner(String serverCorner) {
        String mine = data().minimapCorner;
        // Only a real corner counts as a choice. A blank value, or one left over from an older build,
        // must follow the server rather than silently pinning the minimap to the default corner — which
        // would look like the operator's setting had stopped working.
        if (Briefing.isCorner(mine)) return Briefing.normaliseCorner(mine);
        return Briefing.normaliseCorner(serverCorner);
    }

    /**
     * Advances the player's own corner and stores it.
     *
     * <p>Starts from what is currently on screen, so the first press moves the minimap to the *next* corner
     * rather than jumping to an unrelated one.
     *
     * @return the corner now in effect
     */
    static String cycleCorner(String serverCorner) {
        String next = Briefing.nextCorner(corner(serverCorner));
        data().minimapCorner = next;
        persist();
        return next;
    }

    static boolean deathOverDowned() { return data().deathOverDowned; }

    static void toggleDeathOverDowned() { data().deathOverDowned = !data().deathOverDowned; persist(); }

    static String screenBackground() {
        String value = data().screenBackground;
        return value == null ? "" : value;
    }

    static void setScreenBackground(String name) {
        data().screenBackground = name == null ? "" : name;
        persist();
    }

    static void toggleWhizz(){data().bulletWhizz=!data().bulletWhizz;persist();}
    static void toggleSuppression(){data().suppressionEffects=!data().suppressionEffects;persist();}
    static void toggleDistantGunCull(){data().distantGunCull=!data().distantGunCull;persist();}
    static MinimapOptions minimap(){if(data().minimap==null)data().minimap=new MinimapOptions();data().minimap.sanitize();return data().minimap;}
    static void saveMinimap(MinimapOptions value,boolean rotate,boolean hidden,String corner){value.sanitize();data().minimap=value.copy();data().mapRotation=rotate;data().minimapHidden=hidden;data().minimapCorner=corner;persist();}
    static int zoomMinimap(boolean in){if(NativeXaero.zoom(in))return minimap().span;minimap().zoom(in);persist();return minimap().span;}

    /** @return true when the minimap is now hidden */
    static boolean toggleMinimap() {
        data().minimapHidden = !data().minimapHidden;
        persist();
        return data().minimapHidden;
    }

    /** Arrow size on the map, clamped so a bad config value cannot make it unusable. */
    static float selfArrowSize() {
        float size = data().selfArrowSize;
        if (!Float.isFinite(size) || size <= 0.0F) return 9.0F;
        return Math.max(4.0F, Math.min(20.0F, size));
    }

    static void setSelfArrowSize(float size) {
        data().selfArrowSize = Math.max(4.0F, Math.min(20.0F, size));
        persist();
    }

    static boolean minimapHidden() {
        return data().minimapHidden;
    }

    static boolean followHeading() {
        return data().mapRotation;
    }

    /** @return true when the map now rotates with the player's facing */
    static boolean toggleMapRotation() {
        data().mapRotation = !data().mapRotation;
        persist();
        return data().mapRotation;
    }

    /** Returns the corner to the server's choice, e.g. for a "reset" action. */
    static void resetCorner() {
        data().minimapCorner = "";
        persist();
    }

    // --- audio switches --------------------------------------------------------------------

    static boolean sfxEnabled() {
        return data().sfx;
    }

    static boolean voiceEnabled() {
        return data().voice;
    }

    static boolean musicEnabled() {
        return data().music;
    }

    /** @return true when SFX are now enabled */
    static boolean toggleSfx() {
        data().sfx = !data().sfx;
        persist();
        return data().sfx;
    }

    /** @return true when voice is now enabled */
    static boolean toggleVoice() {
        data().voice = !data().voice;
        persist();
        return data().voice;
    }

    /** @return true when music is now enabled */
    static boolean toggleMusic() {
        data().music = !data().music;
        persist();
        return data().music;
    }

    /**
     * The audio state as a pure value, so the cycle can be checked without writing a config file.
     *
     * <p>The three switches are independent on disk; this groups them into the named states the single key
     * steps through. Keeping the transition table here rather than inline in {@link #cycleAudio} means the
     * reachability of every state — in particular that silence is always reachable and always escapable — can be
     * asserted headless. A cycle that can strand a player in silence is worse than no key at all.
     */
    static final class AudioState {
        /** Everything on. */
        static final String ALL = "SFX_VOICE_MUSIC";
        /** Effects only, the first press. */
        static final String SFX_ONLY = "SFX_ONLY";
        /** Nothing at all. */
        static final String SILENT = "SILENT";
        /** Effects off but callouts and music on: reachable only by editing the file, but must not be a trap. */
        static final String VOICE_MUSIC = "VOICE_MUSIC";

        private AudioState() { }

        /** The named state for a set of switches. */
        static String of(boolean sfx, boolean voice, boolean music) {
            if (sfx && voice && music) return ALL;
            if (sfx && !voice && !music) return SFX_ONLY;
            if (!sfx && !voice && !music) return SILENT;
            if (!sfx && (voice || music)) return VOICE_MUSIC;
            // Everything else -- effects plus one of the other two -- steps down to effects only on the next
            // press, so it is labelled as that state rather than given a name nobody will ever see.
            return SFX_ONLY;
        }

        /**
         * The state one press moves to.
         *
         * <p>The order is the order people silence things: music first, then callouts, then effects. A key that
         * jumped straight to silence would be useless for the common case, which is wanting the music gone while
         * the match is still audible.
         */
        static String next(String state) {
            if (ALL.equals(state)) return SFX_ONLY;
            if (SFX_ONLY.equals(state)) return SILENT;
            if (SILENT.equals(state)) return ALL;
            if (VOICE_MUSIC.equals(state)) return SILENT;
            // An unknown state is a corrupt or future file; returning to fully on is the only recovery that
            // cannot leave the player wondering why there is no sound.
            return ALL;
        }

        /** What to say on the action bar for a state. */
        static String describe(String state) {
            if (ALL.equals(state)) return "音效全开（音效 / 语音 / 音乐）";
            if (SFX_ONLY.equals(state)) return "仅音效（语音与音乐已关）";
            if (SILENT.equals(state)) return "全部静音";
            if (VOICE_MUSIC.equals(state)) return "语音与音乐开、音效关（配置文件状态）";
            return "音频状态未知，已恢复全开";
        }
    }

    /**
     * Cycles through the three audio states: all on, effects only, all off.
     *
     * <p>The transition table lives in {@link AudioState} so it can be tested; this method only applies it and
     * reports the result.
     *
     * @return a short description of the new state, for the action bar
     */
    static String cycleAudio() {
        Data d = data();
        String next = AudioState.next(AudioState.of(d.sfx, d.voice, d.music));
        // The switches are written from one explicit table rather than from string parsing: a state name is a
        // label for the player, and deriving three booleans from one with `contains` is how "SILENT" ends up
        // leaving `sfx` true. The table is exhaustive over the four named states, and `next` only ever returns
        // one of them.
        if (AudioState.ALL.equals(next)) {
            d.sfx = true;
            d.voice = true;
            d.music = true;
        } else if (AudioState.SFX_ONLY.equals(next)) {
            d.sfx = true;
            d.voice = false;
            d.music = false;
        } else if (AudioState.SILENT.equals(next)) {
            d.sfx = false;
            d.voice = false;
            d.music = false;
        } else {
            d.sfx = false;
            d.voice = true;
            d.music = true;
        }
        persist();
        return AudioState.describe(AudioState.of(d.sfx, d.voice, d.music));
    }

    /** Human-readable description of the current audio switches. */
    static String audioStateName() {
        Data d = data();
        return AudioState.describe(AudioState.of(d.sfx, d.voice, d.music));
    }

    // --- kill-icon style -------------------------------------------------------------------

    /**
     * The kill-icon style in effect.
     *
     * <p>Falls back to the catalogue's own fallback when the stored value is blank or names a preset this build
     * does not ship — a preference file written by a newer build must not leave the feed with no icon at all.
     */
    static String killIconPreset() {
        String mine = data().killIconPreset;
        if (mine != null && !mine.isBlank()) return mine;
        return DEFAULT_KILL_ICON_PRESET;
    }

    /** Stores the chosen preset. Unknown ids are rejected so a bad caller cannot poison the file. */
    static void setKillIconPreset(String presetId) {
        if (presetId == null || presetId.isBlank()) return;
        data().killIconPreset = presetId;
        persist();
    }

    /**
     * Advances to the next kill-icon preset and stores it.
     *
     * @param available the preset ids this build ships, in display order
     * @return the preset now in effect
     */
    static String cycleKillIconPreset(java.util.List<String> available) {
        if (available == null || available.isEmpty()) return killIconPreset();
        int index = available.indexOf(killIconPreset());
        String next = available.get((index + 1) % available.size());
        data().killIconPreset = next;
        persist();
        return next;
    }

    /** The {@code valorant} finish in effect, falling back to the first when unset or unknown. */
    static String valorantFinish() {
        return KillIconFinishes.orDefault(data().valorantFinish);
    }

    /** Advances to the next {@code valorant} finish. @return the finish now in effect */
    static String cycleValorantFinish() {
        String next = KillIconFinishes.next(valorantFinish());
        data().valorantFinish = next;
        persist();
        return next;
    }

    /** The preset id used when the player has never chosen one: the pack's Battlefield 6 look. */
    static final String DEFAULT_KILL_ICON_PRESET = "00037";
}
