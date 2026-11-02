package cn.bfops.killicon.common.config;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import cn.bfops.killicon.client.config.ElementTextureDefinition;

public final class ElementConfigRegistry {
   private static final Map<String, Map<String, ConfigKeyDefinition>> DEFINITIONS = new LinkedHashMap<>();

   private ElementConfigRegistry() {
   }

   public static void register(ConfigKeyDefinition... defs) {
      for (ConfigKeyDefinition def : defs) {
         DEFINITIONS.computeIfAbsent(def.elementId(), k -> new LinkedHashMap<>()).put(def.key(), def);
      }
   }

   public static void register(List<ConfigKeyDefinition> defs) {
      for (ConfigKeyDefinition def : defs) {
         register(def);
      }
   }

   public static boolean hasElement(String elementId) {
      return DEFINITIONS.containsKey(elementId);
   }

   public static Set<String> getElements() {
      return DEFINITIONS.keySet();
   }

   public static Set<String> getKeys(String elementId) {
      Map<String, ConfigKeyDefinition> defs = DEFINITIONS.get(elementId);
      return defs == null ? Collections.emptySet() : defs.keySet();
   }

   public static ConfigKeyDefinition getDefinition(String elementId, String key) {
      Map<String, ConfigKeyDefinition> defs = DEFINITIONS.get(elementId);
      return defs == null ? null : defs.get(key);
   }

   public static boolean isRegistered(String elementId, String key) {
      return getDefinition(elementId, key) != null;
   }

   public static ConfigType getType(String elementId, String key) {
      ConfigKeyDefinition def = getDefinition(elementId, key);
      return def == null ? null : def.type();
   }

   public static Category getCategory(String elementId, String key) {
      ConfigKeyDefinition def = getDefinition(elementId, key);
      return def == null ? null : def.category();
   }

   public static String getDependsOn(String elementId, String key) {
      ConfigKeyDefinition def = getDefinition(elementId, key);
      return def == null ? null : def.dependsOn();
   }

   public static String getTextureTab(String elementId, String key) {
      ConfigKeyDefinition def = getDefinition(elementId, key);
      return def == null ? null : def.textureTab();
   }

   public static JsonObject buildElementDefaults(String elementId) {
      JsonObject config = new JsonObject();
      Map<String, ConfigKeyDefinition> defs = DEFINITIONS.get(elementId);
      if (defs != null) {
         for (ConfigKeyDefinition def : defs.values()) {
            config.add(def.key(), def.defaultValue());
         }
      }

      return config;
   }

   public static void registerTextureKeys(String elementId) {
      if (ElementTextureDefinition.hasTextures(elementId)) {
         for (String texture : ElementTextureDefinition.getTextures(elementId)) {
            String prefix = "anim_" + texture + "_";
            register(
               builder(prefix + "enable_texture_animation", elementId).type(ConfigType.BOOLEAN).textureTab(texture).defaultValue(false).build(),
               builder(prefix + "texture_animation_total_frames", elementId).type(ConfigType.INT).textureTab(texture).defaultValue(1).build(),
               builder(prefix + "texture_animation_interval_ms", elementId).type(ConfigType.INT).textureTab(texture).defaultValue(100).build(),
               builder(prefix + "texture_animation_orientation", elementId)
                  .type(ConfigType.CHOICE)
                  .textureTab(texture)
                  .defaultValue("vertical")
                  .choices("vertical", "horizontal")
                  .build(),
               builder(prefix + "texture_animation_loop", elementId).type(ConfigType.BOOLEAN).textureTab(texture).defaultValue(false).build(),
               builder(prefix + "texture_animation_play_style", elementId)
                  .type(ConfigType.CHOICE)
                  .textureTab(texture)
                  .defaultValue("sequential")
                  .choices("sequential", "random")
                  .build(),
               builder(prefix + "texture_frame_width_ratio", elementId).type(ConfigType.INT).textureTab(texture).defaultValue(1).build(),
               builder(prefix + "texture_frame_height_ratio", elementId).type(ConfigType.INT).textureTab(texture).defaultValue(1).build(),
               builder(prefix + "texture_scale", elementId).type(ConfigType.FLOAT).textureTab(texture).defaultValue(1.0F).build(),
               builder(prefix + "texture_final_opacity", elementId).type(ConfigType.FLOAT).textureTab(texture).defaultValue(1.0F).build(),
               builder(prefix + "texture_x_offset", elementId).type(ConfigType.INT).textureTab(texture).defaultValue(0).build(),
               builder(prefix + "texture_y_offset", elementId).type(ConfigType.INT).textureTab(texture).defaultValue(0).build()
            );
            String officialKey = ElementTextureDefinition.getOfficialTextureKey(texture);
            String customKey = ElementTextureDefinition.getCustomTextureKey(texture);
            String modeKey = ElementTextureDefinition.getTextureModeKey(texture);
            String vanillaKey = ElementTextureDefinition.getVanillaTextureKey(texture);
            String fileName = ElementTextureDefinition.getTextureFileName("00001", elementId, texture);
            String vanillaDefault = resolveVanillaDefaultTexture(elementId, texture);
            List<ConfigKeyDefinition> textureDefs = new ArrayList<>();
            if (fileName != null) {
               textureDefs.add(
                  builder(officialKey, elementId)
                     .type(ConfigType.CHOICE)
                     .textureTab(texture)
                     .defaultValue(fileName)
                     .choices(ElementTextureDefinition.getTextureFileName("00001", elementId, texture))
                     .build()
               );
            }

            textureDefs.add(builder(customKey, elementId).type(ConfigType.STRING).textureTab(texture).defaultValue("").build());
            textureDefs.add(
               builder(modeKey, elementId)
                  .type(ConfigType.CHOICE)
                  .textureTab(texture)
                  .defaultValue("official")
                  .choices("official", "custom", "vanilla")
                  .build()
            );
            if (vanillaDefault != null) {
               textureDefs.add(builder(vanillaKey, elementId).type(ConfigType.CHOICE).textureTab(texture).defaultValue(vanillaDefault).build());
            }

            register(textureDefs);
         }
      }
   }

   private static ConfigKeyDefinition.Builder builder(String key, String elementId) {
      return ConfigKeyDefinition.builder().key(key).element(elementId).category(Category.TEXTURE);
   }

   private static String resolveVanillaDefaultTexture(String elementId, String textureKey) {
      if ("kill_icon/scrolling".equals(elementId)) {
         return switch (textureKey) {
            case "default" -> "minecraft:item/ender_pearl";
            case "headshot" -> "minecraft:item/ender_eye";
            case "explosion" -> "minecraft:item/fire_charge";
            case "crit" -> "minecraft:item/magma_cream";
            case "destroy_vehicle" -> "minecraft:item/blaze_powder";
            case "assist" -> "minecraft:item/slime_ball";
            default -> "minecraft:item/ender_pearl";
         };
      } else if ("kill_icon/battlefield1".equals(elementId)) {
         return switch (textureKey) {
            case "default" -> "minecraft:item/ender_pearl";
            case "headshot" -> "minecraft:item/ender_eye";
            case "explosion" -> "minecraft:item/fire_charge";
            case "crit" -> "minecraft:item/magma_cream";
            case "destroy_vehicle" -> "minecraft:item/blaze_powder";
            default -> "minecraft:item/ender_pearl";
         };
      } else if ("kill_icon/combo".equals(elementId)) {
         return switch (textureKey) {
            case "combo_1" -> "minecraft:item/coal";
            case "combo_2" -> "minecraft:item/copper_ingot";
            case "combo_3" -> "minecraft:item/iron_ingot";
            case "combo_4" -> "minecraft:item/gold_ingot";
            case "combo_5" -> "minecraft:item/diamond";
            case "combo_6" -> "minecraft:item/netherite_ingot";
            default -> "minecraft:item/coal";
         };
      } else if ("kill_icon/valorant".equals(elementId)) {
         return switch (textureKey) {
            case "emblem" -> "minecraft:item/nether_star";
            case "frame" -> "minecraft:item/echo_shard";
            case "bar" -> "minecraft:item/amethyst_shard";
            case "headshot" -> "minecraft:item/firework_star";
            default -> "minecraft:item/nether_star";
         };
      } else if ("kill_icon/card".equals(elementId)) {
         return textureKey != null && textureKey.contains("assist") ? "minecraft:item/slime_ball" : "minecraft:item/netherite_ingot";
      } else if ("kill_icon/card_bar".equals(elementId)) {
         return textureKey != null && textureKey.contains("assist") ? "minecraft:item/slime_ball" : "minecraft:item/netherite_ingot";
      } else {
         return null;
      }
   }

   public static void registerFormatKey(String elementId, String key, String defaultValue) {
      register(ConfigKeyDefinition.builder().key(key).element(elementId).type(ConfigType.STRING).category(Category.CONTENT).defaultValue(defaultValue).build());
   }

   static {
      ElementConfigDefinitionsArea.register();
   }
}
