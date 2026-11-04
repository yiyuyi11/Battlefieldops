package cn.bfops;

/**
 * Hides Jade's overlay while a match is running.
 *
 * <p>Jade has no single "off" switch in its API that is safe to flip, so the four display booleans are used and the
 * player's own values are captured first: leaving the match or turning the switch off restores them exactly.</p>
 */
final class JadeGate {
    private static boolean hidden;
    private static boolean[] saved;
    private static boolean unavailable;
    private static boolean logged;

    private JadeGate() {}

    /** Called from the client tick with the current in-match flag. */
    static void tick(boolean inMatch) {
        boolean shouldHide = inMatch && ClientPrefs.hideJadeInMatch();
        if (shouldHide == hidden) return;
        try {
            apply(shouldHide);
            hidden = shouldHide;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError notAvailable) {
            unavailable = true;
            if (!logged) {
                logged = true;
                BattlefieldOps.LOG.debug("[bfops] Jade overlay not controllable", notAvailable);
            }
        }
    }

    private static void apply(boolean hide) throws ReflectiveOperationException {
        Class<?> generalType = Class.forName("snownee.jade.api.config.IWailaConfig$IConfigGeneral");
        Object internals = Class.forName("snownee.jade.JadeInternals").getField("INSTANCE").get(null);
        Object waila = internals.getClass().getMethod("getWailaConfig").invoke(internals);
        Object general = waila.getClass().getMethod("getGeneral").invoke(waila);
        if (hide) {
            saved = new boolean[]{
                    (boolean) generalType.getMethod("getDisplayBlocks").invoke(general),
                    (boolean) generalType.getMethod("getDisplayEntities").invoke(general),
                    (boolean) generalType.getMethod("getDisplayFluids").invoke(general),
                    (boolean) generalType.getMethod("getDisplayBosses").invoke(general)};
            set(generalType, general, false, false, false, false);
        } else {
            boolean[] restore = saved == null ? new boolean[]{true, true, true, true} : saved;
            set(generalType, general, restore[0], restore[1], restore[2], restore[3]);
        }
    }

    private static void set(Class<?> type, Object general, boolean blocks, boolean entities, boolean fluids, boolean bosses)
            throws ReflectiveOperationException {
        type.getMethod("setDisplayBlocks", boolean.class).invoke(general, blocks);
        type.getMethod("setDisplayEntities", boolean.class).invoke(general, entities);
        type.getMethod("setDisplayFluids", boolean.class).invoke(general, fluids);
        type.getMethod("setDisplayBosses", boolean.class).invoke(general, bosses);
    }
}
