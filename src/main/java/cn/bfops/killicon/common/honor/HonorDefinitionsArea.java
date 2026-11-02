package cn.bfops.killicon.common.honor;

public final class HonorDefinitionsArea {
   private HonorDefinitionsArea() {
   }

   public static void register() {
      HonorRegistry.register(new HonorDefinition("headhunter", ConditionType.KILL_STREAK, "headshot:3", 30));
      HonorRegistry.register(new HonorDefinition("warrior", ConditionType.KILL_STREAK, "moving:3", 50));
      HonorRegistry.register(new HonorDefinition("avenge", ConditionType.CONDITIONAL, "avenge", 30));
      HonorRegistry.register(new HonorDefinition("bomb_technician", ConditionType.CONDITIONAL, "rush_bomb_defused", 50));
      HonorRegistry.register(new HonorDefinition("combat_medic", ConditionType.CONDITIONAL, "round_revive_20", 8));
      HonorRegistry.register(new HonorDefinition("detonator", ConditionType.CONDITIONAL, "rush_objective_destroyed", 35));
      HonorRegistry.register(new HonorDefinition("raider", ConditionType.CONDITIONAL, "kill_within_8s_after_combo4", 150));
      HonorRegistry.register(new HonorDefinition("scout", ConditionType.CONDITIONAL, "spot_20", 30));
      HonorRegistry.register(new HonorDefinition("arsenal", ConditionType.CONDITIONAL, "arsenal_in_30s_3_items", 5));
      HonorRegistry.register(new HonorDefinition("artillery", ConditionType.CONDITIONAL, "vehicle_damage_800", 8));
      HonorRegistry.register(new HonorDefinition("avenger", ConditionType.CONDITIONAL, "leave_it_to_me", 65));
      HonorRegistry.register(new HonorDefinition("ballistics", ConditionType.CONDITIONAL, "destroy_vehicle_outside", 20));
      HonorRegistry.register(new HonorDefinition("ace_pilot", ConditionType.CONDITIONAL, "destroy_air_vehicle_in_air", 30));
      HonorRegistry.register(new HonorDefinition("bail_rocket", ConditionType.CONDITIONAL, "bail_out_destroy_air_return", 1));
      HonorRegistry.register(new HonorDefinition("breaker", ConditionType.CONDITIONAL, "conquest_stage_breaker", 60));
      HonorRegistry.register(new HonorDefinition("top", ConditionType.CONDITIONAL, "victim_top_scorer", 30));
      HonorRegistry.register(new HonorDefinition("medic", ConditionType.CONDITIONAL, "revive_3_consecutive", 60));
      HonorRegistry.register(new HonorDefinition("mechanic", ConditionType.CONDITIONAL, "repair_vehicle_25", 20));
      HonorRegistry.register(new HonorDefinition("minelayer", ConditionType.CONDITIONAL, "mine_destroy_vehicle", 8));
      HonorRegistry.register(new HonorDefinition("fire_support", ConditionType.CONDITIONAL, "fire_suppression_5", 45));
      HonorRegistry.register(new HonorDefinition("field_medic", ConditionType.CONDITIONAL, "revive_5_in_life", 40));
      HonorRegistry.register(new HonorDefinition("flight_dispatcher", ConditionType.CONDITIONAL, "victim_riding_air", 25));
      HonorRegistry.register(new HonorDefinition("pathfinder", ConditionType.CONDITIONAL, "squad_deploy_3", 35));
      HonorRegistry.register(new HonorDefinition("demolition", ConditionType.CONDITIONAL, "destroy_7_vehicles", 5));
      HonorRegistry.register(new HonorDefinition("destroyer", ConditionType.CONDITIONAL, "tactical_gadget_3", 25));
      HonorRegistry.register(new HonorDefinition("executioner", ConditionType.CONDITIONAL, "backstab", 30));
      HonorRegistry.register(new HonorDefinition("flank", ConditionType.CONDITIONAL, "kill_blinded", 30));
      HonorRegistry.register(new HonorDefinition("frenzy", ConditionType.CONDITIONAL, "buff_kill", 50));
      HonorRegistry.register(new HonorDefinition("garrison", ConditionType.CONDITIONAL, "garrison_kill_3", 50));
      HonorRegistry.register(new HonorDefinition("ground_control", ConditionType.CONDITIONAL, "conquest_stage_ground_control", 25));
      HonorRegistry.register(new HonorDefinition("guardian", ConditionType.CONDITIONAL, "leave_it_to_me", 50));
      HonorRegistry.register(new HonorDefinition("gunner", ConditionType.CONDITIONAL, "gunner_kill_5", 35));
      HonorRegistry.register(new HonorDefinition("quickdraw", ConditionType.CONDITIONAL, "quickdraw", 8));
      HonorRegistry.register(new HonorDefinition("roadhog", ConditionType.CONDITIONAL, "roadkill", 50));
      HonorRegistry.register(new HonorDefinition("quartermaster", ConditionType.CONDITIONAL, "supply_revive_25", 35));
      HonorRegistry.register(new HonorDefinition("support", ConditionType.CONDITIONAL, "support_1000_score", 30));
      HonorRegistry.register(new HonorDefinition("tank_destroyer", ConditionType.CONDITIONAL, "destroy_tank", 10));
      HonorRegistry.register(new HonorDefinition("recon", ConditionType.CONDITIONAL, "recon_1000_score", 30));
      HonorRegistry.register(new HonorDefinition("assault", ConditionType.CONDITIONAL, "assault_1000_score", 30));
      HonorRegistry.register(new HonorDefinition("engineer", ConditionType.CONDITIONAL, "engineer_1000_score", 30));
      HonorRegistry.register(new HonorDefinition("sharp_shooter", ConditionType.CONDITIONAL, "long_range_headshot", 30));
      HonorRegistry.register(new HonorDefinition("long_shot", ConditionType.CONDITIONAL, "long_shot_range", 60));
      HonorRegistry.register(new HonorDefinition("marksman", ConditionType.CONDITIONAL, "sniper_kill_10_15", 8));
      HonorRegistry.register(new HonorDefinition("rifleman", ConditionType.CONDITIONAL, "rifle_kill_15_20", 8));
      HonorRegistry.register(new HonorDefinition("sniper_duel", ConditionType.CONDITIONAL, "sniper_duel", 5));
      HonorRegistry.register(new HonorDefinition("smoke_screen", ConditionType.CONDITIONAL, "smoke_revive_5", 5));
      HonorRegistry.register(new HonorDefinition("unstoppable", ConditionType.CONDITIONAL, "mg_kill_5_no_reload", 10));
      HonorRegistry.register(new HonorDefinition("vanguard", ConditionType.CONDITIONAL, "first_kill_in_team", 3));
      HonorRegistry.register(new HonorDefinition("warhead", ConditionType.CONDITIONAL, "heavy_headshot", 8));
   }
}
