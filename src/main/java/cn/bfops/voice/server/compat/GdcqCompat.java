package cn.bfops.voice.server.compat;

import net.minecraft.server.level.ServerPlayer;
import cn.bfops.voice.common.audio.CampType;

/**
 * Conquest interop shim. The reference reflects into {@code gd656conquest} to resolve a player's voice pack,
 * compare camps, defer death cues, and pulse the quick-voice marker. Conquest is merged into the parent mod's
 * {@code cn.bfops} engine here, so this port degrades to the same fallbacks the reference already uses when the
 * conquest mod is absent: the CN pack for the source camp, vanilla-team comparison for camp checks, no death-cue
 * deferral, and no marker pulse.
 */
public final class GdcqCompat {
    private GdcqCompat() {
    }

    public static boolean isLoaded() {
        return false;
    }

    /** The reference pulses a teammate marker through conquest; the parent mod has no such marker, so this is a no-op. */
    public static void triggerQuickVoicePulse(ServerPlayer player) {
    }

    public static CampType resolveCamp(ServerPlayer player) {
        return CampType.CN;
    }

    public static boolean isSameCamp(ServerPlayer left, ServerPlayer right) {
        if (left == null || right == null) {
            return false;
        }
        return left.isAlliedTo(right);
    }

    public static boolean shouldDeferDeathCue(ServerPlayer player) {
        return false;
    }
}
