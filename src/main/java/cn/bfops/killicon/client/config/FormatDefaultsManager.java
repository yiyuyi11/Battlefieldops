package cn.bfops.killicon.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

public final class FormatDefaultsManager {
   private static final String DEFAULT_LANGUAGE = "zh_cn";
   private static final String FORMATS_PATH_PREFIX = "formats/";
   private static Map<String, Map<String, String>> cache = Map.of();
   private static String cachedLanguage = null;

   private FormatDefaultsManager() {
   }

   public static String getDefault(String elementId, String key) {
      if (elementId != null && key != null && !key.isEmpty()) {
         Map<String, Map<String, String>> current = currentLanguageDefaults();
         Map<String, String> element = current.get(elementId);
         if (element != null) {
            String value = element.get(key);
            if (value != null) {
               return value;
            }
         }

         if (!"zh_cn".equals(cachedLanguage)) {
            Map<String, String> zhElement = load("zh_cn").get(elementId);
            if (zhElement != null) {
               String value = zhElement.get(key);
               if (value != null) {
                  return value;
               }
            }
         }

         return "";
      } else {
         return "";
      }
   }

   private static String currentLanguage() {
      try {
         Minecraft minecraft = Minecraft.getInstance();
         return minecraft != null && minecraft.options != null ? minecraft.options.languageCode : null;
      } catch (Throwable var1) {
         return null;
      }
   }

   private static Map<String, Map<String, String>> currentLanguageDefaults() {
      String language = currentLanguage();
      if (language == null) {
         return load("zh_cn");
      } else {
         if (!language.equals(cachedLanguage)) {
            cache = load(language);
            cachedLanguage = language;
         }

         return cache;
      }
   }

   private static Map<String, Map<String, String>> load(String language) {
      try {
         ResourceLocation location = ResourceLocation.fromNamespaceAndPath("battlefield_ops", "formats/" + language + ".json");
         Optional<Resource> resourceOptional = Minecraft.getInstance().getResourceManager().getResource(location);
         if (resourceOptional.isEmpty()) {
            return Map.of();
         } else {
            Object var15;
            try (InputStreamReader reader = new InputStreamReader(resourceOptional.get().open(), StandardCharsets.UTF_8)) {
               JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
               Map<String, Map<String, String>> result = new HashMap<>(root.size());

               for (String elementId : root.keySet()) {
                  JsonObject elementObject = root.getAsJsonObject(elementId);
                  Map<String, String> element = new HashMap<>(elementObject.size());

                  for (String key : elementObject.keySet()) {
                     element.put(key, elementObject.get(key).getAsString());
                  }

                  result.put(elementId, element);
               }

               var15 = result;
            }

            return (Map<String, Map<String, String>>)var15;
         }
      } catch (Exception var14) {
         return Map.of();
      }
   }
}
