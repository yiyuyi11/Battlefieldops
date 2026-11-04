package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Suppresses third-party client aids for the duration of a match.
 *
 * <h2>Why a second gate rather than more code in {@link XaeroGate}</h2>
 *
 * <p>{@code XaeroGate} is written around one mod with a known, stable reflective surface:
 * {@code HudMod.INSTANCE}, one module handle, one config toggle. It also carries a fair amount of
 * crash-recovery machinery — a persisted restore record, retry accounting — which exists because it
 * overwrites the player's own setting and must put it back even if the process dies mid-match.
 *
 * <p>Everything else that needs suppressing is a different shape: a list of optional mods, each with its
 * own idea of how to be turned off, some with no API at all. Folding that list into the Xaero gate would
 * make the one piece of code that already works harder to reason about, and one misbehaving optional mod
 * would take the minimap gate down with it.
 *
 * <p>So the two gates share a trigger and nothing else. Both are driven from the same predicate — a match
 * is live and this client is in it — which is what makes "everything turns off together" true without the
 * two implementations being coupled.
 *
 * <h2>What is suppressed, and why each one counts as an aid</h2>
 *
 * <p>The test applied here is not "is this mod popular" but a single question: <b>does it tell a player
 * something the match deliberately does not hand out?</b>
 *
 * <table>
 *   <caption>Suppression targets</caption>
 *   <tr><th>Mod</th><th>Why it is an aid during a match</th></tr>
 *   <tr><td><b>Xaero's Minimap</b></td><td>Cave map, waypoints and entity radar over the whole world.
 *       The match has its own map with a spotting rule; the radar bypasses it.</td></tr>
 *   <tr><td><b>YSM / Yes Steve Model</b> and other player-model mods</td><td>Not a radar, but a
 *       full-body model visible through terrain-adjacent cases changes how easily a stationary enemy is
 *       spotted, and — more importantly for fairness — model mods commonly add a name/health layer that
 *       the match's own overhead bars are calibrated against. Treated as cosmetic-but-revealing and
 *       disabled, matching the reference pack, which ships no player-model mod at all.</td></tr>
 *   <tr><td><b>Xaero's World Map</b></td><td>The same world data as the minimap, at a larger scale.</td></tr>
 *   <tr><td><b>JourneyMap / VoxelMap / MiniMap-class mods</b></td><td>Same class of information as
 *       Xaero; a pack that swapped one for another would otherwise silently reopen the hole.</td></tr>
 *   <tr><td><b>Entity/cave radar mods</b></td><td>Directly reveal enemy positions.</td></tr>
 * </table>
 *
 * <h2>Why reflection, and why nothing here throws</h2>
 *
 * <p>Every target is optional. A missing class, a moved method or an incompatible version must degrade to
 * "that mod keeps its own settings", never to an exception in the client tick. Each target is therefore an
 * independent, individually guarded attempt, and a failure only retires that one target.
 */
@EventBusSubscriber(modid = "battlefield_ops", value = Dist.CLIENT)
public final class AidGate {
    private static final List<Target> TARGETS = new ArrayList<>();

    private static boolean initialised;

    private AidGate() { }

    /** One suppressible mod: how to find it, how to read its state, how to write it. */
    private interface Target {
        /** Short name for logging. */
        String name();

        /** True when the mod is present and we have a working handle on it. */
        boolean available();

        /** Current enabled state, or {@code null} if it cannot be read. */
        Boolean read();

        /** Applies the state. Throws on failure, which retires this target. */
        void write(boolean enabled) throws Exception;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        boolean suppress = inMatch() && wanted();
        for (Target target : targets()) {
            try {
                if (!target.available()) continue;
                Boolean current = target.read();
                // Null means unreadable. Hiding and then being unable to restore is the one outcome worth
                // avoiding, so an unreadable target is left alone rather than guessed at.
                if (current == null) continue;
                if (current == !suppress) continue;
                target.write(!suppress);
                BattlefieldOps.LOG.debug("{} {}", target.name(), suppress ? "suppressed for match" : "restored");
            } catch (Throwable ex) {
                // Retiring the target rather than retrying: a reflective write that fails once will fail
                // every tick, which would flood the log at 20 lines a second for the whole match.
                retire(target, ex);
            }
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        // A disconnect ends the match, so everything suppressed must come back. Without this a player who
        // leaves mid-match keeps a disabled minimap in every other world until they restart.
        for (Target target : targets()) {
            try {
                if (target.available() && Boolean.FALSE.equals(target.read())) target.write(true);
            } catch (Throwable ex) {
                retire(target, ex);
            }
        }
    }

    /** True when a match is running and this client is participating in it. */
    private static boolean inMatch() {
        var state = Client.state;
        return state != null
                && state.has("inMatch") && state.get("inMatch").getAsBoolean()
                && state.has("me");
    }

    /**
     * True when the operator asked for every aid to be suppressed, rather than just the minimap.
     *
     * <p>Read from the snapshot, not from a local config. The rule belongs to the match: a client that
     * decided this for itself would be offering an opt-in, and an opt-in is not a rule. Absent field means
     * off, so a server built before this setting existed does not suddenly start disabling players' mods.
     */
    private static boolean wanted() {
        var state = Client.state;
        if (state == null || !state.has("hud") || !state.get("hud").isJsonObject()) return false;
        var hud = state.getAsJsonObject("hud");
        return hud.has("strictAids") && hud.get("strictAids").getAsBoolean();
    }

    private static final List<Target> retired = new ArrayList<>();

    private static synchronized void retire(Target target, Throwable cause) {
        if (retired.contains(target)) return;
        retired.add(target);
        BattlefieldOps.LOG.warn("Cannot toggle {} for match mode; leaving it alone", target.name(), cause);
    }

    private static synchronized List<Target> targets() {
        if (!initialised) {
            initialised = true;
            // Xaero is handled by its own gate, which has the crash-safe restore record. It is deliberately
            // not in this list: two gates writing the same option would fight over it every tick.
            TARGETS.add(new XaeroWorldMapTarget());
            TARGETS.add(new ReflectionBoolTarget(
                    "Xaero's World Map",
                    new String[] { "xaero.map.WorldMap", "xaero.map.MapProcessor" },
                    "xaero.map.WorldMap.setEnabled",
                    "xaero.map.WorldMap.isEnabled"));
            TARGETS.add(new ReflectionBoolTarget(
                    "JourneyMap",
                    new String[] { "journeymap.client.JourneymapClient" },
                    "journeymap.client.JourneymapClient.setMiniMapEnabled",
                    "journeymap.client.JourneymapClient.isMiniMapEnabled"));
        }
        TARGETS.removeAll(retired);
        return TARGETS;
    }

    // ---------------------------------------------------------------- targets

    /**
     * Xaero's World Map.
     *
     * <p>Kept separate from the generic reflective target because Xaero's own toggle lives on a module
     * handle, the same shape {@link XaeroGate} already resolves — reusing that knowledge here is cheaper
     * and more robust than guessing a second time.
     */
    private static final class XaeroWorldMapTarget implements Target {
        private static final String MODULES = "xaero.hud.minimap.BuiltInHudModules";
        private static final String HUD_MODULE = "xaero.hud.module.HudModule";
        private static final String MOD = "xaero.common.HudMod";

        private boolean resolved;
        private boolean usable;
        private Object module;
        private Method isActive;
        private Method setActive;
        private Method manager;

        @Override
        public String name() {
            return "Xaero's World Map";
        }

        @Override
        public boolean available() {
            if (resolved) return usable;
            resolved = true;
            try {
                Object found = Class.forName(MODULES).getField("WORLD_MAP").get(null);
                if (found == null) return false;
                Class<?> managerClass = Class.forName("xaero.lib.client.config.ClientConfigManager");
                Class<?> hudModule = Class.forName(HUD_MODULE);
                module = found;
                isActive = hudModule.getMethod("isActive", managerClass);
                setActive = hudModule.getMethod("setActive", managerClass, boolean.class);
                Method getConfigs = Class.forName(MOD).getMethod("getHudConfigs");
                manager = getConfigs.getReturnType().getMethod("getClientConfigManager");
                usable = true;
            } catch (Throwable ex) {
                usable = false;
            }
            return usable;
        }

        private Object handle() throws Exception {
            var instance = Class.forName(MOD).getField("INSTANCE").get(null);
            if (instance == null) return null;
            Object configs = Class.forName(MOD).getMethod("getHudConfigs").invoke(instance);
            return configs == null ? null : manager.invoke(configs);
        }

        @Override
        public Boolean read() {
            try {
                Object handle = handle();
                return handle == null ? null : (Boolean) isActive.invoke(module, handle);
            } catch (Exception ex) {
                return null;
            }
        }

        @Override
        public void write(boolean enabled) throws Exception {
            Object handle = handle();
            if (handle != null) setActive.invoke(module, handle, enabled);
        }
    }

    /**
     * A mod whose on/off state is a plain boolean behind a static getter/setter pair.
     *
     * <p>Covers the mods that expose no config API worth speaking of but do expose a toggle — which is the
     * common shape for minimap mods. All names are looked up together so a version bump that renames one
     * method retires this target cleanly instead of half-working.
     */
    private static final class ReflectionBoolTarget implements Target {
        private final String label;
        private final String[] classCandidates;
        private final String setter;
        private final String getter;

        private boolean resolved;
        private boolean usable;
        private Method get;
        private Method set;

        ReflectionBoolTarget(String label, String[] classCandidates, String setter, String getter) {
            this.label = label;
            this.classCandidates = classCandidates;
            this.setter = setter;
            this.getter = getter;
        }

        @Override
        public String name() {
            return label;
        }

        @Override
        public boolean available() {
            if (resolved) return usable;
            resolved = true;
            for (String candidate : classCandidates) {
                try {
                    Class<?> type = Class.forName(candidate);
                    get = type.getMethod(getter);
                    set = type.getMethod(setter, boolean.class);
                    usable = true;
                    return true;
                } catch (Throwable ignored) {
                    // Try the next candidate name: a fork or a version bump often only differs here.
                }
            }
            usable = false;
            return false;
        }

        @Override
        public Boolean read() {
            try {
                Object value = get.invoke(null);
                return value instanceof Boolean flag ? flag : null;
            } catch (Exception ex) {
                return null;
            }
        }

        @Override
        public void write(boolean enabled) throws Exception {
            set.invoke(null, enabled);
        }
    }
}
