package cn.bfops.killicon.common.honor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import cn.bfops.killicon.common.config.ElementConfigRegistry;

public final class HonorRegistry {
   private static final Map<String, HonorDefinition> DEFINITIONS = new LinkedHashMap<>();
   private static final Map<String, String> FORMAT_KEY_TO_ID = new HashMap<>();
   private static final Set<String> FORMAT_KEYS = new HashSet<>();

   private HonorRegistry() {
   }

   public static void register(HonorDefinition def) {
      if (def != null && !DEFINITIONS.containsKey(def.id())) {
         DEFINITIONS.put(def.id(), def);
         String formatKey = "format_" + def.id();
         ElementConfigRegistry.registerFormatKey("kill_icon/honor", formatKey, "");
         FORMAT_KEY_TO_ID.put(formatKey, def.id());
         FORMAT_KEYS.add(formatKey);
      }
   }

   public static HonorDefinition get(String id) {
      return DEFINITIONS.get(id);
   }

   public static boolean isRegistered(String id) {
      return DEFINITIONS.containsKey(id);
   }

   public static Set<String> getIds() {
      return Collections.unmodifiableSet(DEFINITIONS.keySet());
   }

   public static Collection<HonorDefinition> getAll() {
      return Collections.unmodifiableCollection(DEFINITIONS.values());
   }

   public static int size() {
      return DEFINITIONS.size();
   }

   public static boolean isFormatKey(String key) {
      return FORMAT_KEYS.contains(key);
   }

   public static String getHonorIdByFormatKey(String formatKey) {
      return FORMAT_KEY_TO_ID.get(formatKey);
   }

   public static String displayNameKey(String honorId) {
      HonorDefinition def = get(honorId);
      return def != null ? def.displayNameKey() : "";
   }

   static {
      HonorDefinitionsArea.register();
   }
}
