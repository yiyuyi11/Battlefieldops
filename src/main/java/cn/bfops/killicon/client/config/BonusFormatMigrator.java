package cn.bfops.killicon.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public final class BonusFormatMigrator {
   public static final int TARGET_VERSION = 3;
   private static final Map<String, String> LEGACY_KEY_MOVES = Map.ofEntries(
      Map.entry("format_backstab", "format_backstab_kill"),
      Map.entry("format_backstab_melee", "format_backstab_melee_kill"),
      Map.entry("format_crit_damage", "format_crit"),
      Map.entry("format_explosion_damage", "format_explosion"),
      Map.entry("format_headshot_damage", "format_headshot"),
      Map.entry("format_combo", "format_kill_combo")
   );
   private static final Set<String> LEGACY_LANG_KEYS = Set.of(
      "battlefield_ops.client.format.bonus_absolute_air_control",
      "battlefield_ops.client.format.bonus_ammo_supply",
      "battlefield_ops.client.format.bonus_assist",
      "battlefield_ops.client.format.bonus_avenge",
      "battlefield_ops.client.format.bonus_backstab",
      "battlefield_ops.client.format.bonus_backstab_melee",
      "battlefield_ops.client.format.bonus_berserker",
      "battlefield_ops.client.format.bonus_blind_kill",
      "battlefield_ops.client.format.bonus_bloodthirsty",
      "battlefield_ops.client.format.bonus_both_buff_debuff_kill",
      "battlefield_ops.client.format.bonus_brave_return",
      "battlefield_ops.client.format.bonus_buff_kill",
      "battlefield_ops.client.format.bonus_charge_assault",
      "battlefield_ops.client.format.bonus_combo",
      "battlefield_ops.client.format.bonus_conquest_capture_control",
      "battlefield_ops.client.format.bonus_conquest_capture_neutralize",
      "battlefield_ops.client.format.bonus_conquest_capture_progress",
      "battlefield_ops.client.format.bonus_crit",
      "battlefield_ops.client.format.bonus_damage",
      "battlefield_ops.client.format.bonus_debuff_kill",
      "battlefield_ops.client.format.bonus_desperate_counterattack",
      "battlefield_ops.client.format.bonus_destroy_block",
      "battlefield_ops.client.format.bonus_destroy_vehicle",
      "battlefield_ops.client.format.bonus_effortless_kill",
      "battlefield_ops.client.format.bonus_emergency_reinforcement",
      "battlefield_ops.client.format.bonus_explosion",
      "battlefield_ops.client.format.bonus_fierce",
      "battlefield_ops.client.format.bonus_fire_suppression",
      "battlefield_ops.client.format.bonus_friendly_deploy_on_your_vehicle",
      "battlefield_ops.client.format.bonus_ground_sensor_scan",
      "battlefield_ops.client.format.bonus_headshot",
      "battlefield_ops.client.format.bonus_healing",
      "battlefield_ops.client.format.bonus_hit_vehicle_armor",
      "battlefield_ops.client.format.bonus_hold_position",
      "battlefield_ops.client.format.bonus_interrupted_streak",
      "battlefield_ops.client.format.bonus_justice_from_above",
      "battlefield_ops.client.format.bonus_kill",
      "battlefield_ops.client.format.bonus_kill_crit",
      "battlefield_ops.client.format.bonus_kill_explosion",
      "battlefield_ops.client.format.bonus_kill_headshot",
      "battlefield_ops.client.format.bonus_kill_invisible",
      "battlefield_ops.client.format.bonus_kill_long_distance",
      "battlefield_ops.client.format.bonus_last_bullet_kill",
      "battlefield_ops.client.format.bonus_leave_it_to_me",
      "battlefield_ops.client.format.bonus_locked_target",
      "battlefield_ops.client.format.bonus_merciless",
      "battlefield_ops.client.format.bonus_one_bullet_multi_kill",
      "battlefield_ops.client.format.bonus_potato_aim",
      "battlefield_ops.client.format.bonus_purge",
      "battlefield_ops.client.format.bonus_quick_switch",
      "battlefield_ops.client.format.bonus_revive",
      "battlefield_ops.client.format.bonus_rush_bomb_defused",
      "battlefield_ops.client.format.bonus_rush_bomb_planted",
      "battlefield_ops.client.format.bonus_rush_objective_destroyed",
      "battlefield_ops.client.format.bonus_savage",
      "battlefield_ops.client.format.bonus_savior",
      "battlefield_ops.client.format.bonus_seize_opportunity",
      "battlefield_ops.client.format.bonus_shockwave",
      "battlefield_ops.client.format.bonus_slay_the_leader",
      "battlefield_ops.client.format.bonus_spotting",
      "battlefield_ops.client.format.bonus_spotting_kill",
      "battlefield_ops.client.format.bonus_spotting_team_assist",
      "battlefield_ops.client.format.bonus_squad_beacon_deploy",
      "battlefield_ops.client.format.bonus_squad_deploy_on_you",
      "battlefield_ops.client.format.bonus_squad_last_member_kill",
      "battlefield_ops.client.format.bonus_squad_wipe_completion",
      "battlefield_ops.client.format.bonus_tactical_gadget_destroyed",
      "battlefield_ops.client.format.bonus_valiant",
      "battlefield_ops.client.format.bonus_value_objective_support_beacon_deploy",
      "battlefield_ops.client.format.bonus_value_target_destroyed",
      "battlefield_ops.client.format.bonus_vehicle_destroy_assist",
      "battlefield_ops.client.format.bonus_vehicle_repair"
   );

   private BonusFormatMigrator() {
   }

   public static boolean migrate() {
      boolean changed = false;

      for (ElementConfigManager.ElementPreset preset : ElementConfigManager.getActivePresets().values()) {
         JsonObject bonusList = preset.getConfig("subtitle/bonus_list");
         if (bonusList != null) {
            changed |= migrateBonusList(bonusList);
         }
      }

      if (changed) {
         ElementConfigManager.saveConfig();
      }

      return changed;
   }

   private static boolean migrateBonusList(JsonObject config) {
      boolean changed = false;

      for (Entry<String, String> move : LEGACY_KEY_MOVES.entrySet()) {
         if (config.has(move.getKey())) {
            JsonElement value = config.remove(move.getKey());
            if (!config.has(move.getValue())) {
               config.add(move.getValue(), value);
            }

            changed = true;
         }
      }

      for (String key : new ArrayList<>(config.keySet())) {
         JsonElement element = config.get(key);
         if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            if (LEGACY_LANG_KEYS.contains(value) || value.startsWith("battlefield_ops.bonus_format.")) {
               config.remove(key);
               changed = true;
            }
         }
      }

      return changed;
   }
}
