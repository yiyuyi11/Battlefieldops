package cn.bfops;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.lang.reflect.Method;

/**
 * Hides Xaero's minimap while a match is running, and puts it back afterwards.
 *
 * <p>A match has its own minimap, and two of them in opposite corners is not a style problem: Xaero's
 * shows a cave map, waypoints and entity radar over the whole world, which is information this mod's
 * scoring matches deliberately do not hand out. So the operator's intent 鈥?"during a match, the match
 * map is the map" 鈥?is enforced client-side, where Xaero actually lives.
 *
 * <h2>Why reflection, and why it is written this defensively</h2>
 *
 * <p>Xaero is an optional dependency, so the mod compiles and loads without it. Every entry point is
 * therefore reflective and wrapped: a class that is missing, a method that moved to another type in a
 * pack update, or a signature that changed must all degrade to "Xaero keeps its own settings" rather
 * than to a crash in the render loop.
 *
 * <p>The toggle used is {@code HudModule.setActive(ClientConfigManager, boolean)}, which writes
 * {@code DISPLAY_MINIMAP} in the player's <em>current config profile</em>. That means the user's own
 * setting is being overwritten, so the original value is captured before the first write and restored
 * when the match ends, on disconnect, and on client shutdown. Restoring from the captured value 鈥?not
 * simply setting {@code true} 鈥?matters: a player who had already turned Xaero's minimap off must not
 * have it switched on for them by leaving a match.
 *
 * <p>The captured value is also written to {@code <gameDir>/battlefield_ops/xaero_restore.txt}. An
 * in-memory field cannot survive a hard crash, and a crash mid-match would otherwise leave the player's
 * Xaero profile permanently modified with no record of what it used to be. On the next launch the file
 * is read back and the original value restored, so the worst case is one session of hidden minimap
 * rather than a閰嶇疆鏂囦欢 left broken.
 */
@EventBusSubscriber(modid = "battlefield_ops", value = Dist.CLIENT)
public final class XaeroGate {
    private static final String MOD = "xaero.common.HudMod";
    private static final String MODULES = "xaero.hud.minimap.BuiltInHudModules";
    private static final String HUD_MODULE = "xaero.hud.module.HudModule";

    /** Guards against a missing or moved Xaero API: we stop trying after the first hard failure. */
    private static boolean unavailable;
    private static boolean resolved;
    private static Object module;
    private static Method isActive;
    private static Method setActive;
    /**
     * The cached lookup chain to Xaero's config manager.
     *
     * <p>Cached because {@link #apply(boolean)} runs from the render loop: a {@code Class.forName} and
     * three {@code getMethod} calls per frame, on the client thread, would be pure waste. The instance
     * itself is not cached 鈥?it is null before login and does not exist at class-init time.
     */
    private static Method getHudConfigs;
    private static Method getClientConfigManager;
    private static Class<?> hudModClass;

    /** The profile value we found before hiding, or {@code null} when we are not currently hiding. */
    private static Boolean saved;

    // ---- Entity radar gate -----------------------------------------------------------
    // The minimap's "Display Radar" toggle is a separate config option from the minimap's own
    // visibility. A match's map is the only map: enemy entities must not be handed out as radar
    // blips, so the option is turned off for the duration of a match and restored afterwards, with
    // the same capture/restore discipline as the minimap itself.
    private static boolean radarResolved;
    private static boolean radarUnavailable;
    private static Object radarOption;
    private static Method getCurrentProfile;
    private static Method configGet;
    private static Method configSet;
    private static Boolean savedRadar;
    private static boolean radarBroken;
    private static boolean radarRecoveryDone;
    private static Boolean radarLeftover;
    private static int radarRestoreFailures;

    private XaeroGate() { }

    /**
     * True when the operator asked for Xaero to be suppressed during matches.
     *
     * <p>Read from the server snapshot rather than a local config: whether Xaero is a problem is a
     * property of the pack and of the match rules, and the server already publishes its coexistence
     * decisions in {@code hud}.
     */
    private static boolean wanted() {
        if (!Client.state.has("hud")) return false;
        var hud = Client.state.getAsJsonObject("hud");
        return hud.has("hideXaero") && hud.get("hideXaero").getAsBoolean();
    }

    /** True when a match is live and this client is in it. */
    private static boolean inMatch() {
        return Client.state.has("inMatch") && Client.state.get("inMatch").getAsBoolean()
                && Client.state.has("me");
    }

    @SubscribeEvent
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        // Driven from the client tick, not from a GUI render event: the gate must also hold while a
        // menu is open, and {@code RenderGuiEvent} stops firing then — a player sitting in the
        // deployment screen would get Xaero's map back. Tick is also the safer place to write a config
        // value than the middle of a frame, since Xaero reads that config while it renders.
        apply(inMatch() && wanted());
        applyRadar(inMatch());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        // A disconnect ends the match by definition; leaving Xaero hidden would strand the player with
        // no minimap in every other world until they restarted the game.
        apply(false);
        applyRadar(false);
    }

    /**
     * Repairs a profile left modified by a previous session that died mid-match.
     *
     * <p>Runs on login rather than at startup because Xaero's config manager does not exist until a
     * world is joined. At this point {@code saved} is null 鈥?if it were not, this session would be
     * hiding Xaero itself and there would be nothing to repair.
     */
    @SubscribeEvent
    public static void loggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (saved == null) apply(false);
        if (savedRadar == null) applyRadar(false);
    }

    /** Restores on shutdown so an exit does not leave the profile modified on disk. */
    @SubscribeEvent
    public static void shuttingDown(net.neoforged.neoforge.event.GameShuttingDownEvent event) {
        apply(false);
        applyRadar(false);
    }

    /**
     * Pushes the desired visibility to Xaero, once per change.
     *
     * <p>Idempotent by construction: {@code saved} records whether we are currently hiding, so the
     * reflective call happens on the transition and not on every frame.
     *
     * <p>Note the ordering: Xaero is not touched at all until there is an actual write to perform. This
     * matters because resolving the API instantiates Xaero's module registry, and the common case 鈥?     * player in a world, no match running, gate doing nothing 鈥?must not pay for that.
     */
    static void apply(boolean hide) {
        if (hide) {
            if (saved != null || hideBroken) return;
            if (!resolve()) return;
            try {
                var manager = manager();
                if (manager == null) return;
                // Capture before the first write. Reading through getEffective would return the
                // server-synced value; the profile's raw value is what the player chose.
                Object current = isActive.invoke(module, manager);
                if (!(current instanceof Boolean captured)) {
                    // Nothing to restore from, so nothing may be changed. Hiding and then being
                    // unable to put it back is the one outcome that must never happen.
                    BattlefieldOps.LOG.warn("Xaero minimap state unreadable; leaving it alone");
                    hideBroken = true;
                    return;
                }
                saved = captured;
                persist(saved);
                setActive.invoke(module, manager, false);
            } catch (Throwable ex) {
                // The capture is kept on purpose: the write may have partially landed, and the restore
                // path below still knows what to put back. Only further hiding is abandoned.
                hideBroken = true;
                BattlefieldOps.LOG.warn("Could not hide the Xaero minimap; giving up on this session", ex);
            }
            return;
        }

        // Not hiding. Two things can need repair here: a capture this session took, and a record left
        // behind by a session that died mid-match 鈥?without the second, that player's minimap would
        // stay off until they found the setting in Xaero's own menu.
        Boolean pending = recoverOnce();
        if (saved == null && pending == null) return;
        if (!resolve()) return;
        try {
            var manager = manager();
            if (manager == null) return;
            Boolean restore = saved != null ? saved : pending;
            if (restore != null) setActive.invoke(module, manager, restore);
            // Only once the write has actually landed: clear the in-memory capture and drop the record.
            // Clearing first would mean a throwing write leaves the minimap hidden with no record of
            // what it should be restored to.
            saved = null;
            leftover = null;
            recoveryDone = true;
            clearPersisted();
            restoreFailures = 0;
        } catch (Throwable ex) {
            // Deliberately not marking the gate unavailable: doing so would abandon the restore and
            // leave the minimap hidden for the rest of the session, which is worse than the failure. The
            // capture and the on-disk record are both still intact, so the next attempt can succeed; if
            // this client cannot restore at all, the record survives for the next launch to honour.
            if (restoreFailures++ == 0) {
                BattlefieldOps.LOG.warn("Could not restore the Xaero minimap setting; will retry", ex);
            }
        }
    }

    /**
     * Pushes the radar's desired state to Xaero, once per change.
     *
     * <p>The radar is disabled for the whole duration of a match — not just when the operator hides the
     * minimap — because enemy entities on the radar are information a match must not hand out. It shares
     * the minimap gate's capture-then-restore discipline, so a player who had already turned the radar
     * off is not switched back on by leaving a match.
     */
    static void applyRadar(boolean disable) {
        if (disable) {
            if (savedRadar != null || radarBroken) return;
            if (!resolve() || !resolveRadar()) return;
            try {
                var manager = manager();
                if (manager == null) return;
                Object profile = getCurrentProfile.invoke(manager);
                if (profile == null) return;
                Object current = configGet.invoke(profile, radarOption);
                if (!(current instanceof Boolean captured)) {
                    radarBroken = true;
                    BattlefieldOps.LOG.warn("Xaero radar state unreadable; leaving it alone");
                    return;
                }
                savedRadar = captured;
                persistRadar(savedRadar);
                configSet.invoke(profile, radarOption, false);
            } catch (Throwable ex) {
                radarBroken = true;
                BattlefieldOps.LOG.warn("Could not disable the Xaero radar; giving up on this session", ex);
            }
            return;
        }

        Boolean pending = recoverRadarOnce();
        if (savedRadar == null && pending == null) return;
        if (!resolve() || !resolveRadar()) return;
        try {
            var manager = manager();
            if (manager == null) return;
            Object profile = getCurrentProfile.invoke(manager);
            if (profile == null) return;
            Boolean restore = savedRadar != null ? savedRadar : pending;
            if (restore != null) configSet.invoke(profile, radarOption, restore);
            savedRadar = null;
            radarLeftover = null;
            radarRecoveryDone = true;
            clearPersistedRadar();
            radarRestoreFailures = 0;
        } catch (Throwable ex) {
            if (radarRestoreFailures++ == 0) {
                BattlefieldOps.LOG.warn("Could not restore the Xaero radar setting; will retry", ex);
            }
        }
    }

    /**
     * Resolves the radar option and its read/write methods once; false when Xaero is absent or the
     * option moved. Kept separate from {@link #resolve()} so a radar API change cannot take the minimap
     * gate down with it.
     */
    private static boolean resolveRadar() {
        if (radarResolved) return !radarUnavailable;
        radarResolved = true;
        try {
            Class<?> options = Class.forName("xaero.hud.minimap.common.config.option.MinimapProfiledConfigOptions");
            Object option = options.getField("DISPLAY_RADAR").get(null);
            if (option == null) {
                radarUnavailable = true;
                return false;
            }
            Class<?> managerClass = Class.forName("xaero.lib.client.config.ClientConfigManager");
            Class<?> optionClass = Class.forName("xaero.lib.common.config.option.ConfigOption");
            Class<?> configClass = Class.forName("xaero.lib.common.config.Config");
            radarOption = option;
            getCurrentProfile = managerClass.getMethod("getCurrentProfile");
            configGet = configClass.getMethod("get", optionClass);
            configSet = configClass.getMethod("set", optionClass, Object.class);
            return true;
        } catch (Throwable ex) {
            radarUnavailable = true;
            return false;
        }
    }

    // ---------------------------------------------------------------- crash-safe restore record

    private static java.nio.file.Path record() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) return null;
        return mc.gameDirectory.toPath().resolve("battlefield_ops").resolve("xaero_restore.txt");
    }

    private static void persist(Boolean value) {
        if (value == null) return;
        try {
            java.nio.file.Path file = record();
            if (file == null) return;
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, value ? "true" : "false");
        } catch (Exception ex) {
            // Not fatal: the in-memory copy still restores on a clean exit.
            BattlefieldOps.LOG.debug("Could not write the Xaero restore record", ex);
        }
    }

    private static void clearPersisted() {
        try {
            java.nio.file.Path file = record();
            if (file != null) java.nio.file.Files.deleteIfExists(file);
        } catch (Exception ex) {
            BattlefieldOps.LOG.debug("Could not clear the Xaero restore record", ex);
        }
    }

    /**
     * Reads a record left by a previous session that ended mid-match, without consuming it.
     *
     * <p>The file is left in place until the restore write has actually succeeded (see
     * {@link #clearPersisted()}). Deleting on read would mean a failure between the read and the write
     * loses the only record of the player's original setting 鈥?the exact situation the record exists to
     * prevent.
     */
    private static Boolean readRecord() {
        try {
            java.nio.file.Path file = record();
            if (file == null || !java.nio.file.Files.exists(file)) return null;
            String text = java.nio.file.Files.readString(file).trim();
            if (text.equalsIgnoreCase("true")) return Boolean.TRUE;
            if (text.equalsIgnoreCase("false")) return Boolean.FALSE;
            return null;
        } catch (Exception ex) {
            BattlefieldOps.LOG.debug("Could not read the Xaero restore record", ex);
            return null;
        }
    }

    /**
     * The leftover record, read at most once per session.
     *
     * <p>{@link #apply(boolean)} is called from the render loop, so an unconditional file stat here
     * would mean a disk access every frame 鈥?on the client thread, in the middle of rendering. The
     * answer cannot change within a session either.
     */
    private static Boolean recoverOnce() {
        if (!recoveryDone) {
            recoveryDone = true;
            leftover = readRecord();
        }
        return leftover;
    }

    /** The radar's restore record, separate from the minimap's so the two can be repaired independently. */
    private static java.nio.file.Path radarRecord() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) return null;
        return mc.gameDirectory.toPath().resolve("battlefield_ops").resolve("xaero_radar_restore.txt");
    }

    private static void persistRadar(Boolean value) {
        if (value == null) return;
        try {
            java.nio.file.Path file = radarRecord();
            if (file == null) return;
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, value ? "true" : "false");
        } catch (Exception ex) {
            BattlefieldOps.LOG.debug("Could not write the Xaero radar restore record", ex);
        }
    }

    private static void clearPersistedRadar() {
        try {
            java.nio.file.Path file = radarRecord();
            if (file != null) java.nio.file.Files.deleteIfExists(file);
        } catch (Exception ex) {
            BattlefieldOps.LOG.debug("Could not clear the Xaero radar restore record", ex);
        }
    }

    private static Boolean readRadarRecord() {
        try {
            java.nio.file.Path file = radarRecord();
            if (file == null || !java.nio.file.Files.exists(file)) return null;
            String text = java.nio.file.Files.readString(file).trim();
            if (text.equalsIgnoreCase("true")) return Boolean.TRUE;
            if (text.equalsIgnoreCase("false")) return Boolean.FALSE;
            return null;
        } catch (Exception ex) {
            BattlefieldOps.LOG.debug("Could not read the Xaero radar restore record", ex);
            return null;
        }
    }

    private static Boolean recoverRadarOnce() {
        if (!radarRecoveryDone) {
            radarRecoveryDone = true;
            radarLeftover = readRadarRecord();
        }
        return radarLeftover;
    }

    private static boolean recoveryDone;
    private static Boolean leftover;
    /** Only used to log the first restore failure; retries stay silent so the log cannot be flooded. */
    private static int restoreFailures;
    /**
     * Set when hiding Xaero failed this session, so the attempt is not repeated every frame.
     *
     * <p>Separate from {@link #unavailable}, which means the API itself could not be resolved. A failed
     * write is different: the capture may still be valid and must still be restored, so this flag stops
     * the hiding half while leaving the restoring half alive.
     */
    private static boolean hideBroken;

    /** {@code HudMod.INSTANCE.getHudConfigs().getClientConfigManager()}, or null before login. */
    private static Object manager() throws ReflectiveOperationException {
        Object instance = hudModClass.getField("INSTANCE").get(null);
        if (instance == null) return null;
        Object configs = getHudConfigs.invoke(instance);
        if (configs == null) return null;
        return getClientConfigManager.invoke(configs);
    }

    /** Resolves the module and its methods once; false when Xaero is absent or incompatible. */
    private static boolean resolve() {
        if (resolved) return !unavailable;
        resolved = true;
        try {
            Class<?> modules = Class.forName(MODULES);
            Object found = modules.getField("MINIMAP").get(null);
            if (found == null) {
                unavailable = true;
                return false;
            }
            Class<?> hudModule = Class.forName(HUD_MODULE);
            Class<?> managerClass = Class.forName("xaero.lib.client.config.ClientConfigManager");
            module = found;
            isActive = hudModule.getMethod("isActive", managerClass);
            setActive = hudModule.getMethod("setActive", managerClass, boolean.class);

            hudModClass = Class.forName(MOD);
            getHudConfigs = hudModClass.getMethod("getHudConfigs");
            // The manager accessor is declared on ConfigChannel; resolved from the declared return type
            // rather than the runtime class, which may be a subclass with a different lookup order.
            getClientConfigManager = getHudConfigs.getReturnType().getMethod("getClientConfigManager");
            if (!managerClass.isAssignableFrom(getClientConfigManager.getReturnType())) {
                BattlefieldOps.LOG.warn("Xaero config manager type changed; minimap gate disabled");
                unavailable = true;
                return false;
            }
            return true;
        } catch (Throwable ex) {
            // Xaero not installed, or a version whose API differs: honour the player's own settings.
            unavailable = true;
            return false;
        }
    }

}
