package cn.bfops;

import java.util.Locale;

/**
 * Detects a third-party ragdoll mod.
 *
 * <p>There is no need to guess: Sable's physics pipeline ships with this instance and "Sable: Ragdolls"
 * (plus its "Ragdoll Corpse" companion) are NeoForge 1.21.1 mods that already produce real Havok-style
 * ragdolls on death. When one of them is installed, the hand-posed body in {@link CorpseRagdoll} is a
 * second corpse with worse physics, so it stands down and leaves the bodies to the mod that has the
 * solver.
 *
 * <p>Matching is by mod id or display name containing "ragdoll" rather than by a hard-coded id list: the
 * two Sable addons were renamed during their alpha ("Player Ragdoll" → "Ragdolls"), and a user may also
 * run "Saro's Player Ragdoll" or an older fork.
 */
final class RagdollCompat {
    private static Boolean installed;

    private RagdollCompat() {}

    static boolean installed() {
        if (installed == null) {
            boolean found = false;
            try {
                for (var mod : net.neoforged.fml.ModList.get().getMods()) {
                    String id = mod.getModId() == null ? "" : mod.getModId().toLowerCase(Locale.ROOT);
                    String name = mod.getDisplayName() == null ? "" : mod.getDisplayName().toLowerCase(Locale.ROOT);
                    if (id.contains("ragdoll") || name.contains("ragdoll")) {
                        found = true;
                        BattlefieldOps.LOG.info("[bfops] ragdoll mod detected ({}); hand-posed death bodies disabled",
                                mod.getModId());
                        break;
                    }
                }
            } catch (RuntimeException | LinkageError ex) {
                BattlefieldOps.LOG.debug("Ragdoll detection unavailable", ex);
            }
            installed = found;
        }
        return installed;
    }
}
