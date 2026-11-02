package cn.bfops.killicon.client.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.resources.language.I18n;

public class DefaultConfigRegistry {
   private static final Map<String, Set<String>> OFFICIAL_PRESET_STRUCTURE = new HashMap<>();
   private static final Map<String, String> OFFICIAL_PRESET_NAMES = new HashMap<>();

   public static String getOfficialPresetDisplayName(String presetId) {
      String key = "battlefield_ops.client.gui.config.preset.official." + presetId;
      return I18n.exists(key) ? I18n.get(key, new Object[0]) : OFFICIAL_PRESET_NAMES.getOrDefault(presetId, "");
   }

   private static void registerOfficialNames() {
      OFFICIAL_PRESET_NAMES.put("00001", "六五六自制预设");
      OFFICIAL_PRESET_NAMES.put("00002", "CF连杀图标模式");
      OFFICIAL_PRESET_NAMES.put("00003", "CS2卡片模式");
      OFFICIAL_PRESET_NAMES.put("00004", "Battlefield 1模式");
      OFFICIAL_PRESET_NAMES.put("00005", "Battlefield 4模式");
      OFFICIAL_PRESET_NAMES.put("00006", "PUBG淘汰字幕模式");
      OFFICIAL_PRESET_NAMES.put("00007", "Battlefield 5模式");
      OFFICIAL_PRESET_NAMES.put("00008", "三角洲行动：全面战场模式");
      OFFICIAL_PRESET_NAMES.put("00035", "Custom Preset (EN)");
      OFFICIAL_PRESET_NAMES.put("00036", "Battlefield 5 (EN)");
      OFFICIAL_PRESET_NAMES.put("00037", "Battlefield 6模式");

      for (ValorantStyleCatalog.StyleSpec definition : ValorantStyleCatalog.getDefinitions()) {
         OFFICIAL_PRESET_NAMES.put(definition.presetId(), "VALORANT " + definition.displayName());
      }
   }

   public static Set<String> getOfficialPresetElements(String presetId) {
      return OFFICIAL_PRESET_STRUCTURE.getOrDefault(presetId, Collections.emptySet());
   }

   public static boolean isOfficialPreset(String presetId) {
      return OFFICIAL_PRESET_STRUCTURE.containsKey(presetId);
   }

   public static Set<String> getOfficialPresetIds() {
      return OFFICIAL_PRESET_STRUCTURE.keySet();
   }

   private static void registerOfficialStructures() {
      Set<String> p00001 = new HashSet<>();
      p00001.add("subtitle/kill_feed");
      p00001.add("subtitle/score");
      p00001.add("subtitle/bonus_list");
      p00001.add("kill_icon/scrolling");
      OFFICIAL_PRESET_STRUCTURE.put("00001", p00001);
      Set<String> p00002 = new HashSet<>();
      p00002.add("subtitle/kill_feed");
      p00002.add("subtitle/score");
      p00002.add("subtitle/bonus_list");
      p00002.add("kill_icon/combo");
      OFFICIAL_PRESET_STRUCTURE.put("00002", p00002);
      Set<String> p00003 = new HashSet<>();
      p00003.add("kill_icon/card_bar");
      p00003.add("kill_icon/card");
      OFFICIAL_PRESET_STRUCTURE.put("00003", p00003);
      Set<String> p00004 = new HashSet<>();
      p00004.add("subtitle/score");
      p00004.add("subtitle/bonus_list");
      p00004.add("kill_icon/battlefield1");
      OFFICIAL_PRESET_STRUCTURE.put("00004", p00004);
      Set<String> p00005 = new HashSet<>();
      p00005.add("subtitle/score");
      p00005.add("subtitle/bonus_list");
      OFFICIAL_PRESET_STRUCTURE.put("00005", p00005);
      Set<String> p00006 = new HashSet<>();
      p00006.add("subtitle/combo");
      p00006.add("subtitle/kill_feed");
      OFFICIAL_PRESET_STRUCTURE.put("00006", p00006);
      Set<String> p00007 = new HashSet<>();
      p00007.add("subtitle/kill_feed");
      p00007.add("subtitle/score");
      p00007.add("subtitle/bonus_list");
      p00007.add("kill_icon/scrolling");
      OFFICIAL_PRESET_STRUCTURE.put("00007", p00007);
      Set<String> p00008 = new HashSet<>();
      p00008.add("subtitle/score");
      p00008.add("subtitle/bonus_list");
      p00008.add("kill_icon/scrolling");
      OFFICIAL_PRESET_STRUCTURE.put("00008", p00008);
      Set<String> p00035 = new HashSet<>();
      p00035.add("subtitle/kill_feed");
      p00035.add("subtitle/score");
      p00035.add("subtitle/bonus_list");
      p00035.add("kill_icon/scrolling");
      OFFICIAL_PRESET_STRUCTURE.put("00035", p00035);
      Set<String> p00036 = new HashSet<>();
      p00036.add("subtitle/kill_feed");
      p00036.add("subtitle/score");
      p00036.add("subtitle/bonus_list");
      p00036.add("kill_icon/scrolling");
      OFFICIAL_PRESET_STRUCTURE.put("00036", p00036);
      Set<String> p00037 = new HashSet<>();
      p00037.add("subtitle/kill_feed");
      p00037.add("subtitle/score");
      p00037.add("subtitle/bonus_list");
      p00037.add("kill_icon/scrolling");
      p00037.add("subtitle/hit_info");
      OFFICIAL_PRESET_STRUCTURE.put("00037", p00037);

      for (ValorantStyleCatalog.StyleSpec definition : ValorantStyleCatalog.getDefinitions()) {
         Set<String> valorantPreset = new HashSet<>();
         valorantPreset.add("kill_icon/valorant");
         OFFICIAL_PRESET_STRUCTURE.put(definition.presetId(), valorantPreset);
      }
   }

   static {
      registerOfficialStructures();
      registerOfficialNames();
   }
}
