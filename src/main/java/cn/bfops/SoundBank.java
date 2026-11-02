package cn.bfops;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gameplay event to sound-event name, as pure data.
 *
 * <p><b>Why a table and not a switch at every call site.</b> The reference pack has 120 clips. Wiring them by
 * name at the places they happen would scatter the mapping across a dozen files, and the inevitable question
 * — "which clip plays when a capture point is lost" — would have no single answer. Here it does.
 *
 * <p><b>Why categories.</b> The three groups are not taste. Music and voice are the two that players
 * legitimately want off: music competes with their own, and callouts are language-specific. Mixing all three
 * into one "sounds on/off" toggle means a player who dislikes the music loses the capture confirmation with
 * it. {@link Category#of} classifies by name prefix — the prefixes come from the reference's own
 * {@code sounds.json}, so the classification is read from the source rather than re-decided here.
 *
 * <p><b>Randomised variants on purpose.</b> The reference ships six samples for "incoming heavy shell" and ten
 * for "kill enemy". Those are not duplicates to collapse: the run-to-run variation is what stops a soundscape
 * becoming a jingle you learn to ignore. {@link #variants} returns all of them and the caller picks one.
 */
final class SoundBank {
    /** What a clip is for, so players can switch a whole class off without losing the others. */
    enum Category {
        /** Ambient music and loops. */
        MUSIC,
        /** Weapon/objective/world feedback. */
        SFX,
        /** Announcer and soldier callouts. */
        VOICE;

        /**
         * Category from the event-name prefix.
         *
         * <p>{@code music.*} and {@code loop.*} are both music: the reference's own {@code loop.deploy_screen}
         * is a bed, not an effect, and grouping it with {@code music.victory} is what lets one toggle mean
         * "no background music" rather than "no background music except four of the beds".
         */
        static Category of(String name) {
            if (name == null) return SFX;
            if (name.startsWith("music.") || name.startsWith("loop.")) return MUSIC;
            if (name.startsWith("voice.")) return VOICE;
            return SFX;
        }
    }

    /**
     * Cue to event names.
     *
     * <p>One entry per thing that can happen in a match. Where several clips mean the same thing, they are
     * listed together and the caller picks at random.
     */
    private static final Map<String, List<String>> CUES = new LinkedHashMap<>();

    static {
        // --- core match moments ---------------------------------------------------------------
        CUES.put("kill", List.of("sfx.capture_taked"));
        CUES.put("hit", List.of("sfx.marker_spot_success"));
        CUES.put("select", List.of("sfx.deploy_select_ready"));
        CUES.put("mark_success", List.of("sfx.marker_spot_success"));
        CUES.put("mark_fail", List.of("sfx.marker_spot_fail"));
        CUES.put("capture", List.of("sfx.capture_in"));
        CUES.put("heal", List.of("sfx.player_heal"));
        CUES.put("revive", List.of("sfx.revive_first_person", "sfx.revive_defibrillator_start",
                "sfx.revive_syringe_start"));
        CUES.put("beacon_deploy", List.of("sfx.beacon_deploy"));
        CUES.put("beacon_destroyed", List.of("sfx.beacon_destroyed"));
        CUES.put("beacon_loop", List.of("sfx.beacon_loop"));
        CUES.put("ammo_throw", List.of("sfx.ammo_bag_throw"));
        CUES.put("ammo_pickup", List.of("sfx.ammo_bag_pickup"));
        CUES.put("sensor_activate", List.of("sfx.ground_sensor_activate"));
        CUES.put("sensor_beep", List.of("sfx.ground_sensor_beep"));
        CUES.put("sensor_fire", List.of("sfx.ground_sensor_fire_1p"));

        // --- artillery ------------------------------------------------------------------------
        // Split by weight because that is the information: a light shell is "keep moving", a heavy one is
        // "get out of the open". One generic explosion would throw that distinction away.
        CUES.put("shell_incoming_light", List.of(
                "sfx.shelling.incoming_lightshell_1", "sfx.shelling.incoming_lightshell_2",
                "sfx.shelling.incoming_lightshell_3", "sfx.shelling.incoming_lightshell_4",
                "sfx.shelling.incoming_lightshell_5", "sfx.shelling.incoming_lightshell_6"));
        CUES.put("shell_incoming_medium", List.of(
                "sfx.shelling.incoming_mediumshell_1", "sfx.shelling.incoming_mediumshell_2",
                "sfx.shelling.incoming_mediumshell_3", "sfx.shelling.incoming_mediumshell_4",
                "sfx.shelling.incoming_mediumshell_5", "sfx.shelling.incoming_mediumshell_6"));
        CUES.put("shell_incoming_heavy", List.of(
                "sfx.shelling.incoming_heavyshell_1", "sfx.shelling.incoming_heavyshell_2",
                "sfx.shelling.incoming_heavyshell_3", "sfx.shelling.incoming_heavyshell_4",
                "sfx.shelling.incoming_heavyshell_5", "sfx.shelling.incoming_heavyshell_6"));
        CUES.put("shell_incoming_flame", List.of(
                "sfx.shelling.incoming_mediumflameshell_1", "sfx.shelling.incoming_mediumflameshell_2",
                "sfx.shelling.incoming_mediumflameshell_3", "sfx.shelling.incoming_mediumflameshell_4",
                "sfx.shelling.incoming_mediumflameshell_5", "sfx.shelling.incoming_mediumflameshell_6"));
        CUES.put("shell_incoming_missile", List.of(
                "sfx.shelling.incoming_missile_1", "sfx.shelling.incoming_missile_2",
                "sfx.shelling.incoming_missile_3", "sfx.shelling.incoming_missile_4"));
        CUES.put("shell_launch_light", List.of("sfx.shelling.launch_lightshell"));
        CUES.put("shell_launch_medium", List.of("sfx.shelling.launch_mediumshell"));
        CUES.put("shell_launch_heavy", List.of("sfx.shelling.launch_heavyshell"));
        CUES.put("shell_launch_missile", List.of(
                "sfx.shelling.launch_missile_1", "sfx.shelling.launch_missile_2",
                "sfx.shelling.launch_missile_3", "sfx.shelling.launch_missile_4",
                "sfx.shelling.launch_missile_5"));
        CUES.put("bomb_raid", List.of("sfx.shelling.bombraid_sfx"));
        // The incoming beds and the flame bed loop for the duration of the barrage rather than firing once.
        // Kept as separate cues from the one-shot variants so a caller that wants a continuous warning gets
        // one, and a caller that wants a single sting is not forced to stop a loop afterwards.
        CUES.put("shell_loop_light", List.of("sfx.shelling.loop_lightshell"));
        CUES.put("shell_loop_medium", List.of("sfx.shelling.loop_mediumshell"));
        CUES.put("shell_loop_heavy", List.of("sfx.shelling.loop_heavyshell"));
        CUES.put("shell_loop_flame", List.of("sfx.shelling.loop_mediumflameshell"));
        CUES.put("shell_loop_missile", List.of("sfx.shelling.loop_missile"));
        CUES.put("flame_burst", List.of(
                "sfx.shelling.flame_sound_1", "sfx.shelling.flame_sound_2",
                "sfx.shelling.flame_sound_3", "sfx.shelling.flame_sound_4"));

        // --- breakthrough / rush --------------------------------------------------------------
        CUES.put("charge_beep", List.of("sfx.rush.charge_beep"));
        CUES.put("charge_plant_intro", List.of("sfx.rush.charge_plant_intro_01"));
        CUES.put("charge_plant_done", List.of("sfx.rush.charge_plant_finish_01"));
        CUES.put("charge_disarm", List.of("sfx.rush.charge_disarm_leadout_01"));
        CUES.put("alarm", List.of("sfx.rush.alarm_01"));
        CUES.put("alarm_leadout", List.of("sfx.rush.alarm_leadout_01"));
        // The lead-out loop is the alarm winding down, so it belongs with the lead-out sting rather than
        // being a separate "alarm" variant: playing it as an alarm would leave a decaying tail ringing.
        CUES.put("alarm_leadout_loop", List.of("sfx.rush.alarm_leadout_loop"));
        CUES.put("brick_destroyed", List.of("sfx.rush.brick_destruction_l"));

        // --- music ----------------------------------------------------------------------------
        CUES.put("music_death", List.of("music.death"));
        CUES.put("music_first_deploy", List.of("music.first_deploy"));
        CUES.put("music_stage_begin", List.of("music.stage_begin"));
        CUES.put("music_victory", List.of("music.victory"));
        CUES.put("music_defeat", List.of("music.defeat"));
        CUES.put("loop_deploy", List.of("loop.deploy_screen"));
        CUES.put("loop_retreat", List.of("loop.retreat"));
        CUES.put("loop_attack", List.of("loop.attack"));
        CUES.put("loop_lobby", List.of("loop.lobby"));
        CUES.put("loop_queue", List.of("loop.queue"));
        CUES.put("loop_in_objective", List.of("loop.in_objective"));
        CUES.put("loop_last_objective", List.of("loop.last_objective"));
        CUES.put("loop_last_objective_victory", List.of("loop.last_objective_victory"));
        CUES.put("loop_final_victory", List.of("loop.final_segment_victory"));
        CUES.put("loop_final_defeat", List.of("loop.final_segment_defeat"));
        CUES.put("loop_desperate", List.of("loop.desperate_mode"));

        // --- voice: capture -------------------------------------------------------------------
        CUES.put("voice_attacking_hostiles", List.of(
                "voice.capture.objective_attacking_hostiles_a", "voice.capture.objective_attacking_hostiles_b",
                "voice.capture.objective_attacking_hostiles_c", "voice.capture.objective_attacking_hostiles_d",
                "voice.capture.objective_attacking_hostiles_e", "voice.capture.objective_attacking_hostiles_f"));
        CUES.put("voice_objective_lost", List.of(
                "voice.capture.objective_lost_a", "voice.capture.objective_lost_b",
                "voice.capture.objective_lost_c", "voice.capture.objective_lost_d",
                "voice.capture.objective_lost_e", "voice.capture.objective_lost_f"));
        CUES.put("voice_objective_taken", List.of(
                "voice.capture.objective_taken_a", "voice.capture.objective_taken_b",
                "voice.capture.objective_taken_c", "voice.capture.objective_taken_d",
                "voice.capture.objective_taken_e", "voice.capture.objective_taken_f"));

        // --- voice: rush / artillery / hero kits ------------------------------------------------
        CUES.put("voice_attack_defuse", List.of("voice.rush.attack_defuse_a", "voice.rush.attack_defuse_b"));
        CUES.put("voice_defend_defuse", List.of("voice.rush.defend_defuse_a", "voice.rush.defend_defuse_b"));
        CUES.put("voice_plant_set", List.of("voice.rush.plant_set_a", "voice.rush.plant_set_b"));
        CUES.put("voice_artillery_friendly", List.of(
                "voice.shelling.artillery_friendly_1", "voice.shelling.artillery_friendly_2",
                "voice.shelling.artillery_friendly_3"));
        CUES.put("voice_artillery_wave", List.of("voice.shelling.artillery_friendly_promotion_wave"));
        CUES.put("voice_herokit_at", List.of("voice.support.herokit_available_antivehicle"));
        CUES.put("voice_herokit_flame", List.of("voice.support.herokit_available_flametrooper"));
        CUES.put("voice_herokit_runner", List.of("voice.support.herokit_available_runner"));
        CUES.put("voice_herokit_sentry", List.of("voice.support.herokit_available_sentry"));
        CUES.put("voice_herokit_trench", List.of("voice.support.herokit_available_trenchraider"));

        // --- kill-icon stingers -----------------------------------------------------------------
        // The 86 sounds that ship with the kill-icon pack, grouped the way the pack's presets use them: one
        // stinger per presentation mode, per kill kind, and per consecutive-kill count. Every one of the 86 is
        // reachable from exactly one cue below, which `SoundBankSim` asserts -- an unreachable sound is a file
        // that plays silence forever without saying so.
        //
        // The four base kill sounds belong to the four "named look" families, and choosing one is what makes a
        // preset *sound* like the game it imitates.
        CUES.put("kill_bf1", List.of("kill.killsound_bf1"));
        CUES.put("kill_bf5", List.of("kill.killsound_bf5"));
        CUES.put("kill_bf6", List.of("kill.killsound_bf6"));
        CUES.put("kill_df", List.of("kill.killsound_df"));
        // Kind-specific stingers. Only some families distinguish kinds, so these lists are genuinely unequal:
        // the `df` family has one for each of default/headshot/explosion/crit, `bf6` only for a headshot.
        CUES.put("kill_default", List.of("kill.defaulticonsound_df"));
        CUES.put("kill_headshot", List.of("kill.headshotkillsound_bf1", "kill.headshotkillsound_bf5",
                "kill.headshotkillsound_bf6", "kill.headshotkillsound_df"));
        CUES.put("kill_explosion", List.of("kill.explosionkillsound_df"));
        CUES.put("kill_crit", List.of("kill.critkillsound_df"));
        CUES.put("kill_vehicle", List.of("kill.vehiclekillsound_bf5", "kill.collisionkillsound_df"));
        CUES.put("kill_assist", List.of("kill.assistsound_bf6"));
        // Hit feedback and the score tick, which the pack plays on damage rather than on a kill.
        CUES.put("hit_bf6", List.of("kill.hitsound_bf6"));
        CUES.put("hit_df", List.of("kill.hitsound_df"));
        CUES.put("add_score", List.of("kill.addscore_df"));
        // Honour badges, split by how impressive the event was.
        CUES.put("honour", List.of("kill.honorkillsound_normal"));
        CUES.put("honour_high", List.of("kill.honorkillsound_high"));
        // Consecutive-kill stingers: six variants, played by streak length.
        CUES.put("combo_1", List.of("kill.combokillsound_1_cf"));
        CUES.put("combo_2", List.of("kill.combokillsound_2_cf"));
        CUES.put("combo_3", List.of("kill.combokillsound_3_cf"));
        CUES.put("combo_4", List.of("kill.combokillsound_4_cf"));
        CUES.put("combo_5", List.of("kill.combokillsound_5_cf"));
        CUES.put("combo_6", List.of("kill.combokillsound_6_cf"));
        CUES.put("card_default", List.of("kill.cardkillsound_default_cs"));
        CUES.put("card_headshot", List.of("kill.cardkillsound_headshot_cs"));
        CUES.put("card_explosion", List.of("kill.cardkillsound_explosion_cs"));
        CUES.put("card_crit", List.of("kill.cardkillsound_crit_cs"));
        CUES.put("card_armour_headshot", List.of("kill.cardkillsound_armorheadshot_cs"));
        // The Valorant finishes: each skin has five escalating stings, chosen by streak position. Nine skins
        // ship, so this is 45 of the 86 files -- the largest single group, which is why it is worth naming
        // each one rather than collapsing them into a generic "valorant" cue.
        for (String skin : List.of("prime", "glitchpop", "singularity", "gaia", "gaiavengeance",
                "bubblegumdeathwish", "champions2021", "preludetochaos", "primordium", "radiantcrisis001",
                "rgx11zpro")) {
            CUES.put("valorant_" + skin, List.of(
                    "kill.valorant_" + skin + "kill_1", "kill.valorant_" + skin + "kill_2",
                    "kill.valorant_" + skin + "kill_3", "kill.valorant_" + skin + "kill_4",
                    "kill.valorant_" + skin + "kill_5"));
        }
        CUES.put("valorant_headshot", List.of("kill.valorant_headshot"));
    }

    private SoundBank() { }

    /**
     * Event names for a cue, in catalogue order.
     *
     * @return the names, possibly empty when the cue is unknown; never {@code null}
     */
    static List<String> variants(String cue) {
        if (cue == null) return List.of();
        List<String> names = CUES.get(cue);
        return names == null ? List.of() : names;
    }

    /**
     * One event name for a cue, chosen by {@code pick} so the caller controls the randomness.
     *
     * <p>The index is taken from the caller rather than from {@code Math.random()} so that which variant
     * plays is reproducible in a test, and so a caller that wants the same clip for several related events
     * (a shell's launch and its impact) can pass a stable seed.
     *
     * @return the name, or {@code null} when the cue has no clip
     */
    static String pick(String cue, int pick) {
        List<String> names = variants(cue);
        if (names.isEmpty()) return null;
        // Math.floorMod, not %, so a negative seed still lands inside the list instead of throwing.
        return names.get(Math.floorMod(pick, names.size()));
    }

    /** Every cue name, so the tests can assert the whole table rather than a sample of it. */
    static List<String> cues() {
        return List.copyOf(CUES.keySet());
    }

    /** Total distinct event names referenced by the table, for a coverage check against the catalogue. */
    static int referencedEvents() {
        return (int) CUES.values().stream().flatMap(List::stream).distinct().count();
    }

    /**
     * Event names the table refers to that the catalogue does not contain.
     *
     * <p>Exists because a typo in a cue name is silent: the sound simply never plays, and nothing in the game
     * reports it. Checked by {@code SoundBankSim}.
     */
    static List<String> missingFrom(List<String> catalogue) {
        return CUES.values().stream().flatMap(List::stream).distinct()
                .filter(name -> !catalogue.contains(name)).sorted().toList();
    }

    /** Catalogue events the table never plays, split out so the gap is a visible number, not a guess. */
    static List<String> unreferenced(List<String> catalogue) {
        return catalogue.stream().filter(name -> CUES.values().stream().noneMatch(list -> list.contains(name)))
                .sorted().toList();
    }
}
