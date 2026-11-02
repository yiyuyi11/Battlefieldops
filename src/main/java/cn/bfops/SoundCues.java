package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Plays a cue from {@link SoundBank}, honouring the player's category switches.
 *
 * <p><b>Why the switches are per category.</b> Music and voice are the two a player may legitimately want
 * gone — music competes with their own soundtrack, and callouts are in one language. A single "sounds
 * off" would take the capture confirmation with them. {@link ClientPrefs} stores the three flags.
 *
 * <p><b>Why the variant index is a round robin and not a random draw.</b> A cue with six samples should not
 * play the same one every time, but an independent draw repeats far more often than people expect: with six
 * options, three repeats in a row is common enough to be noticed. Advancing through the list guarantees
 * every sample is heard before any repeats.
 *
 * <p><b>Threading.</b> Client only, called from HUD and tick code, so it reads {@code Minecraft.getInstance()}
 * and returns quietly when there is no player — the state during the loading screen this class is first
 * reached from.
 */
final class SoundCues {
    /** Last variant index played per cue. */
    private static final java.util.Map<String, Integer> LAST = new java.util.HashMap<>();

    private SoundCues() { }

    /** Plays a cue at default pitch and volume. */
    static void play(String cue) {
        play(cue, 1.0f, 1.0f);
    }
    static void playAt(String cue,double x,double y,double z){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        String name=SoundBank.pick(cue,nextPick(cue));if(name==null)return;
        SoundBank.Category category=SoundBank.Category.of(name);if(!enabled(category))return;
        SoundEvent sound=Sounds.get(name);if(sound==null)return;
        mc.level.playLocalSound(x,y,z,sound,SoundSource.PLAYERS,categoryVolume(category),1f,false);
    }

    /**
     * Plays a cue with an explicit volume and pitch.
     *
     * <p>The category trim and the player's switch are applied here rather than at the call site, so a cue
     * added later is automatically governed by the player's preference instead of silently ignoring it.
     */
    static void play(String cue, float volume, float pitch) {
        String name = SoundBank.pick(cue, nextPick(cue));
        if (name != null) playEvent(name, volume, pitch);
    }

    /**
     * Plays a specific event by name.
     *
     * <p>The only path that actually makes sound, so the category check and the volume trim cannot be
     * forgotten by a caller: the cue-table route and the direct route both end up here.
     */
    static void playEvent(String name, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        if (mc.options == null) return;
        SoundEvent sound = Sounds.get(name);
        if (sound == null) return;
        SoundBank.Category category = SoundBank.Category.of(name);
        if (!enabled(category)) return;
        if(category==SoundBank.Category.MUSIC){MusicDirector.oneShot(name,volume*.85f,pitch);return;}
        // MUSIC must go on the MUSIC source: that is what the options screen's music slider controls, and
        // routing a streamed track through MASTER would ignore a player's own music setting.
        SoundSource source = switch (category) {
            case MUSIC -> SoundSource.MUSIC;
            case VOICE -> SoundSource.PLAYERS;
            case SFX -> SoundSource.PLAYERS;
        };
        float trimmed = volume * categoryVolume(category);
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), sound, source,
                Math.max(0f, Math.min(4f, trimmed)), pitch, false);
    }

    /** Whether a category is currently switched on. */
    static boolean enabled(SoundBank.Category category) {
        return switch (category) {
            case MUSIC -> ClientPrefs.musicEnabled();
            case VOICE -> ClientPrefs.voiceEnabled();
            case SFX -> ClientPrefs.sfxEnabled();
        };
    }

    /**
     * Per-category volume trim.
     *
     * <p>The reference's per-clip volumes (0.375 for the beacon loop, 1.2 for the ammo bag) are already baked
     * into {@code sounds.json}. This is the coarse layer on top: voice sits a little below SFX so a callout
     * does not talk over the shot that earned it.
     */
    private static float categoryVolume(SoundBank.Category category) {
        return switch (category) {
            case MUSIC -> 0.85f;
            case VOICE -> 0.75f;
            case SFX -> 1.0f;
        };
    }

    /**
     * Next variant index for a cue.
     *
     * <p>Deterministic so a test can assert the order, and so that a cue never immediately repeats when the
     * cue has more than one sample.
     */
    static int nextPick(String cue) {
        int index = LAST.getOrDefault(cue, -1) + 1;
        int size = SoundBank.variants(cue).size();
        if (size > 1) index = index % size;
        LAST.put(cue, index);
        return index;
    }

    /** Forgets the round-robin state, e.g. on joining a new match. */
    static void reset() {
        LAST.clear();
    }

    /**
     * Plays the stinger for a kill-icon award.
     *
     * <p>The cue is chosen from two things: which presentation mode the player's preset selects, and what
     * happened. That pairing is the reason the pack ships four base kill sounds and eleven Valorant finish sets
     * — a preset that looks like Battlefield 1 should sound like Battlefield 1, and one that looks like a
     * Valorant skin should use that skin's stinger. Falls back to the plain family sound for presets whose mode
     * has no audio of its own, and does nothing when the preset is unknown.
     *
     * @param presetId the preset in effect, used to pick the family
     * @param award    this mod's award name, mapped to a pack kind by {@link KillIcons}
     */
    static void killFeedback(String presetId, String award) {
        String cue = killCue(presetId, award);
        if (cue != null) play(cue);
    }

    /**
     * The cue for a preset and award, or {@code null} when nothing should play.
     *
     * <p>Pure and package-visible so the mapping is testable without a sound engine: a cue that names a
     * non-existent event fails silently in game, which is exactly the class of bug a headless check catches.
     */
    static String killCue(String presetId, String award) {
        String preset = presetId == null ? "" : presetId;
        KillType kind = KillIcons.kindFor(award);

        // A Valorant preset is defined by its skin, so the skin's own stinger is the only correct answer --
        // ahead of the kind sound, because a player who picked "Valorant Prime" wants Prime's audio.
        String skin = ValorantPresets.skinFor(preset);
        if (skin != null) return "valorant_" + skin;

        // The named-look presets are as much about the sound as the art, so their family sound wins over
        // the kind sound: Battlefield 1's kill stinger is the point of the Battlefield 1 preset.
        String familyCue = switch (preset) {
            case "00001", "00035" -> "kill_df";        // this mod's own custom preset, in the `df` idiom
            case "00004" -> "kill_bf1";
            case "00007", "00036" -> "kill_bf5";       // 00036 is the English-labelled Battlefield 5 preset
            case "00037" -> "kill_bf6";
            case "00003" -> "card_default";            // CS2 card mode has its own card-slap audio
            default -> null;                           // 00002/00005/00006/00008: no family audio of their own
        };
        boolean namedLook = familyCue != null && switch (preset) {
            case "00001", "00004", "00007", "00037", "00035", "00036" -> true;
            default -> false;
        };
        if (namedLook) return familyCue;

        // Everything else: the kind sound carries the most information, so prefer it.
        String kindCue = switch (kind.id()) {
            case "HEADSHOT" -> "kill_headshot";
            case "EXPLOSION" -> "kill_explosion";
            case "CRIT" -> "kill_crit";
            case "DESTROY_VEHICLE" -> "kill_vehicle";
            case "ASSIST", "MEDIC", "RESCUE", "VEHICLE_DESTROY_ASSIST" -> "kill_assist";
            default -> null;
        };
        if (kindCue != null) return kindCue;
        // Falling back to the pack's own default kill sound rather than to silence: a preset with no audio
        // mapping should still confirm the kill.
        return familyCue != null ? familyCue : "kill_bf6";
    }
}
