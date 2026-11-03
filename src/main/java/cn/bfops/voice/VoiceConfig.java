package cn.bfops.voice;

/**
 * Front-voice runtime configuration.
 *
 * <p>The reference ships a {@code ForgeConfigSpec} with two dozen toggles. This port folds the settings into
 * fixed sensible defaults rather than exposing them through the parent mod's config UI, because the callout
 * subsystem is meant to be heard, not tuned. Every value here mirrors the reference's default.
 */
public final class VoiceConfig {
    private VoiceConfig() {
    }

    /** Master server switch for broadcasting audio cues. */
    public static boolean isServerAudioEnabled() {
        return cn.bfops.BattleServerMode.running();
    }

    /** Master gain multiplier applied to all server broadcasts. */
    public static float getServerMasterVolume() {
        return 1.0F;
    }

    /** Master client switch for audio playback. */
    public static boolean isClientAudioEnabled() {
        return true;
    }

    /** Additional gain for local first-person sounds. */
    public static float getClientFirstPersonVolume() {
        return 1.0F;
    }

    /** Additional gain for positional third-person sounds. */
    public static float getClientThirdPersonVolume() {
        return 1.0F;
    }

    /** Master gain applied to all playback requests. */
    public static float getClientMasterVolume() {
        return 1.0F;
    }

    /** Maximum third-person attenuation distance used by the base player. */
    public static float getClientMaxThirdPersonDistance() {
        return 48.0F;
    }

    /** Play third-person sound for the local player when first-person is missing. */
    public static boolean isClientFallbackThirdPersonForLocal() {
        return true;
    }

    /** Detect enemy players via vanilla team rather than the conquest camp. */
    public static boolean isClientDetectEnemyByVanillaTeam() {
        return false;
    }

    /** Use the friendly/enemy voice pack configs instead of the conquest camp. */
    public static boolean isClientIgnoreConquestCamp() {
        return false;
    }

    /** Friendly voice pack when conquest is not installed. */
    public static cn.bfops.voice.common.audio.CampType getClientFriendlyVoicePack() {
        return cn.bfops.voice.common.audio.CampType.CN;
    }

    /** Enemy voice pack when conquest is not installed. */
    public static cn.bfops.voice.common.audio.CampType getClientEnemyVoicePack() {
        return cn.bfops.voice.common.audio.CampType.US;
    }

    /** Max simultaneous instances of a single third-person sound type. */
    public static int getClientMaxSimultaneousSingleTpSound() {
        return 2;
    }

    /** Max simultaneous friendly players emitting third-person sounds. */
    public static int getClientMaxSimultaneousTpPlayersFriendly() {
        return 3;
    }

    /** Max simultaneous enemy players emitting third-person sounds. */
    public static int getClientMaxSimultaneousTpPlayersEnemy() {
        return 3;
    }

    /** Whether client debug logs should be printed to the logger. */
    public static boolean isClientDebugLoggingEnabled() {
        return true;
    }

    /** Whether client debug output should also be mirrored to in-game chat. */
    public static boolean isClientDebugChatEnabled() {
        return false;
    }

    /** Whether common-side debug logs should be printed. */
    public static boolean isCommonDebugLoggingEnabled() {
        return true;
    }

    /** Whether to log the imported audio manifest summary during startup. */
    public static boolean shouldLogManifestSummary() {
        return true;
    }

    /** Cooldown in seconds between quick messages sent via the radial HUD. */
    public static double getChatCooldownSeconds() {
        return 3.0D;
    }

    /** Whether players may use custom quick-wheel messages. */
    public static boolean isHudAllowCustomMessages() {
        return true;
    }

    /** Master switch for the quick-chat radial HUD. */
    public static boolean isHudEnableQuickChat() {
        return true;
    }

    /** Number of custom extension slots for the quick-message wheel. */
    public static int getQuickWheelExtensionCount() {
        return 0;
    }

    /** Custom text for a quick-wheel extension slot (always empty in this port). */
    public static String getQuickWheelExtensionSlot(int index) {
        return "";
    }
}
