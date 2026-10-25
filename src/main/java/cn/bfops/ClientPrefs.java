package cn.bfops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.neoforged.fml.loading.FMLPaths;

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

    /** @return true when the minimap is now hidden */
    static boolean toggleMinimap() {
        data().minimapHidden = !data().minimapHidden;
        persist();
        return data().minimapHidden;
    }

    static boolean minimapHidden() {
        return data().minimapHidden;
    }

    /** Returns the corner to the server's choice, e.g. for a "reset" action. */
    static void resetCorner() {
        data().minimapCorner = "";
        persist();
    }
}
