package cn.bfops.killicon.client.gui.screens;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import cn.bfops.killicon.client.config.ElementConfigManager;
import cn.bfops.killicon.client.config.ElementTextureDefinition;
import cn.bfops.killicon.client.gui.elements.PromptDialog;
import cn.bfops.killicon.client.gui.elements.entries.BooleanConfigEntry;
import cn.bfops.killicon.client.gui.elements.entries.FixedChoiceConfigEntry;
import cn.bfops.killicon.client.gui.elements.entries.FloatConfigEntry;
import cn.bfops.killicon.client.gui.elements.entries.HexColorConfigEntry;
import cn.bfops.killicon.client.gui.elements.entries.IntegerConfigEntry;
import cn.bfops.killicon.client.gui.elements.entries.StringConfigEntry;
import cn.bfops.killicon.client.gui.tabs.ConfigTabContent;
import cn.bfops.killicon.client.gui.tabs.ElementConfigContent;
import cn.bfops.killicon.client.render.ScreenAnchor;
import cn.bfops.killicon.client.textures.ExternalTextureManager;
import cn.bfops.killicon.client.util.I18nCompat;
import cn.bfops.killicon.common.bonus.BonusRegistry;
import cn.bfops.killicon.common.config.ConfigType;
import cn.bfops.killicon.common.config.ElementConfigRegistry;
import cn.bfops.killicon.common.honor.HonorRegistry;
import cn.bfops.killicon.common.killtype.KillTypeRegistry;

public class ElementConfigBuilderRegistry {
   private static final Map<String, ElementConfigBuilder> builders = new HashMap<>();
   private static final ElementConfigBuilder DEFAULT_BUILDER = new ElementConfigBuilderRegistry.DefaultElementConfigBuilder();
   private static final Pattern HEX_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
   private static final Set<String> TEAM_ELEMENT_IDS = Set.of("kill_icon/card", "kill_icon/card_bar");
   private static List<FixedChoiceConfigEntry.Choice> cachedVanillaItemChoices = null;
   private static String cachedVanillaItemLanguage = null;

   public static void register(String elementId, ElementConfigBuilder builder) {
      builders.put(elementId, builder);
   }

   public static ElementConfigBuilder getBuilder(String elementId) {
      return builders.getOrDefault(elementId, DEFAULT_BUILDER);
   }

   private static List<FixedChoiceConfigEntry.Choice> getCachedVanillaItemChoices() {
      String languageCode = Minecraft.getInstance().options.languageCode;
      if (cachedVanillaItemChoices != null && languageCode.equals(cachedVanillaItemLanguage)) {
         return cachedVanillaItemChoices;
      } else {
         List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
         List<ResourceLocation> itemIds = new ArrayList<>();

         for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
               ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
               if (id != null && "minecraft".equals(id.getNamespace())) {
                  itemIds.add(id);
               }
            }
         }

         itemIds.sort((a, b) -> a.getPath().compareToIgnoreCase(b.getPath()));

         for (ResourceLocation id : itemIds) {
            Item itemx = (Item)BuiltInRegistries.ITEM.get(id);
            String value = "minecraft:item/" + id.getPath();
            if (ExternalTextureManager.isVanillaTextureAvailable(value)) {
               String label = I18n.get(itemx.getDescriptionId(), new Object[0]);
               choices.add(new FixedChoiceConfigEntry.Choice(value, label));
            }
         }

         cachedVanillaItemChoices = choices;
         cachedVanillaItemLanguage = languageCode;
         return cachedVanillaItemChoices;
      }
   }

   private static boolean isValorantMathParticleKey(String key) {
      return "math_particle_density".equals(key) || "math_particle_spread".equals(key) || "math_particle_size".equals(key);
   }

   private static boolean isValorantStandardParticleKey(String key) {
      if (key == null) {
         return false;
      } else if (key.startsWith("base_particle_")
         || key.startsWith("hero_flame_")
         || key.startsWith("large_sparks_")
         || key.startsWith("x_sparks_")
         || key.startsWith("color_base_particle")
         || key.startsWith("color_hero_flame")
         || key.startsWith("color_large_sparks")
         || key.startsWith("color_x_sparks")
         || key.startsWith("enable_custom_color_base_particle")
         || key.startsWith("enable_custom_color_hero_flame")
         || key.startsWith("enable_custom_color_large_sparks")
         || key.startsWith("enable_custom_color_x_sparks")
         || key.startsWith("enable_base_particle")
         || key.startsWith("enable_hero_flame")
         || key.startsWith("enable_large_sparks")
         || key.startsWith("enable_x_sparks")) {
         return true;
      } else {
         return !key.startsWith("anim_base_particle_")
               && !key.startsWith("anim_hero_flame_")
               && !key.startsWith("anim_large_sparks_")
               && !key.startsWith("anim_x_sparks_")
            ? key.equals("texture_mode_base_particle")
               || key.equals("texture_style_base_particle")
               || key.equals("custom_texture_base_particle")
               || key.equals("vanilla_texture_base_particle")
               || key.equals("texture_mode_hero_flame")
               || key.equals("texture_style_hero_flame")
               || key.equals("custom_texture_hero_flame")
               || key.equals("vanilla_texture_hero_flame")
               || key.equals("texture_mode_large_sparks")
               || key.equals("texture_style_large_sparks")
               || key.equals("custom_texture_large_sparks")
               || key.equals("vanilla_texture_large_sparks")
               || key.equals("texture_mode_x_sparks")
               || key.equals("texture_style_x_sparks")
               || key.equals("custom_texture_x_sparks")
               || key.equals("vanilla_texture_x_sparks")
            : true;
      }
   }

   private static class DefaultElementConfigBuilder implements ElementConfigBuilder {
      @Override
      public void build(ConfigTabContent content) {
         if (content instanceof ElementConfigContent elementContent) {
            String presetId = elementContent.getPresetId();
            String elementId = elementContent.getElementId();
            Set<String> configKeys = ElementConfigManager.getConfigKeys(presetId, elementId);
            JsonObject currentConfig = ElementConfigManager.getElementConfig(presetId, elementId);
            JsonObject defaultConfig = ElementConfigManager.getResetDefaultConfig(presetId, elementId);
            if (currentConfig == null) {
               currentConfig = new JsonObject();
            }

            Function<String, Boolean> getConfigBoolean = k -> {
               JsonObject liveConfig = ElementConfigManager.getElementConfig(presetId, elementId);
               if (liveConfig != null && liveConfig.has(k)) {
                  return liveConfig.get(k).getAsBoolean();
               } else {
                  JsonObject liveDefault = ElementConfigManager.getResetDefaultConfig(presetId, elementId);
                  return liveDefault != null && liveDefault.has(k) && liveDefault.get(k).getAsBoolean();
               }
            };
            Function<String, Supplier<Boolean>> getDependency = k -> !k.equals("visible") && configKeys.contains("visible")
                  ? () -> {
                     if (!getConfigBoolean.apply("visible")) {
                        return false;
                     } else if (k.equals("combo_reset_timeout")) {
                        JsonObject liveConfig = ElementConfigManager.getElementConfig(presetId, elementId);
                        JsonObject liveDefault = ElementConfigManager.getResetDefaultConfig(presetId, elementId);
                        String killReset = liveConfig != null && liveConfig.has("reset_kill_combo")
                           ? liveConfig.get("reset_kill_combo").getAsString()
                           : (liveDefault != null && liveDefault.has("reset_kill_combo") ? liveDefault.get("reset_kill_combo").getAsString() : "death");
                        String assistReset = liveConfig != null && liveConfig.has("reset_assist_combo")
                           ? liveConfig.get("reset_assist_combo").getAsString()
                           : (liveDefault != null && liveDefault.has("reset_assist_combo") ? liveDefault.get("reset_assist_combo").getAsString() : "death");
                        return "time".equals(killReset) || "time".equals(assistReset);
                     } else {
                        if ("kill_icon/valorant".equals(elementId) && !k.equals("enable_math_particle_effect")) {
                           boolean mathEnabled = configKeys.contains("enable_math_particle_effect") && getConfigBoolean.apply("enable_math_particle_effect");
                           if ("color_base_particle".equals(k) || "enable_custom_color_base_particle".equals(k)) {
                              return true;
                           }

                           if (ElementConfigBuilderRegistry.isValorantStandardParticleKey(k)) {
                              return !mathEnabled;
                           }

                           if (ElementConfigBuilderRegistry.isValorantMathParticleKey(k)) {
                              return mathEnabled;
                           }
                        }

                        if (k.startsWith("anim_")) {
                           String matchingTexture = null;

                           for (String texture : ElementTextureDefinition.getTextures(elementId)) {
                              String prefix = "anim_" + texture + "_";
                              if (k.startsWith(prefix)) {
                                 matchingTexture = texture;
                                 break;
                              }
                           }

                           if (matchingTexture != null) {
                              String prefix = "anim_" + matchingTexture + "_";
                              String property = k.substring(prefix.length());
                              if (property.equals("enable_texture_animation")) {
                                 return true;
                              }

                              if (property.equals("texture_scale")
                                 || property.equals("texture_final_opacity")
                                 || property.equals("texture_x_offset")
                                 || property.equals("texture_y_offset")) {
                                 return true;
                              }

                              if (property.equals("texture_frame_width_ratio") || property.equals("texture_frame_height_ratio")) {
                                 String enableKey = prefix + "enable_texture_animation";
                                 if (configKeys.contains(enableKey)) {
                                    return !getConfigBoolean.apply(enableKey);
                                 }

                                 return true;
                              }

                              String enableKey = prefix + "enable_texture_animation";
                              if (configKeys.contains(enableKey)) {
                                 return getConfigBoolean.apply(enableKey);
                              }
                           }
                        }

                        return true;
                     }
                  }
                  : () -> {
                     if (k.equals("combo_reset_timeout")) {
                        JsonObject liveConfig = ElementConfigManager.getElementConfig(presetId, elementId);
                        JsonObject liveDefault = ElementConfigManager.getResetDefaultConfig(presetId, elementId);
                        String killReset = liveConfig != null && liveConfig.has("reset_kill_combo")
                           ? liveConfig.get("reset_kill_combo").getAsString()
                           : (liveDefault != null && liveDefault.has("reset_kill_combo") ? liveDefault.get("reset_kill_combo").getAsString() : "death");
                        String assistReset = liveConfig != null && liveConfig.has("reset_assist_combo")
                           ? liveConfig.get("reset_assist_combo").getAsString()
                           : (liveDefault != null && liveDefault.has("reset_assist_combo") ? liveDefault.get("reset_assist_combo").getAsString() : "death");
                        return "time".equals(killReset) || "time".equals(assistReset);
                     } else {
                        if ("kill_icon/valorant".equals(elementId) && !k.equals("enable_math_particle_effect")) {
                           boolean mathEnabled = configKeys.contains("enable_math_particle_effect") && getConfigBoolean.apply("enable_math_particle_effect");
                           if ("color_base_particle".equals(k) || "enable_custom_color_base_particle".equals(k)) {
                              return true;
                           }

                           if (ElementConfigBuilderRegistry.isValorantStandardParticleKey(k)) {
                              return !mathEnabled;
                           }

                           if (ElementConfigBuilderRegistry.isValorantMathParticleKey(k)) {
                              return mathEnabled;
                           }
                        }

                        if (k.startsWith("anim_")) {
                           String matchingTexture = null;

                           for (String texture : ElementTextureDefinition.getTextures(elementId)) {
                              String prefix = "anim_" + texture + "_";
                              if (k.startsWith(prefix)) {
                                 matchingTexture = texture;
                                 break;
                              }
                           }

                           if (matchingTexture != null) {
                              String prefix = "anim_" + matchingTexture + "_";
                              String property = k.substring(prefix.length());
                              if (property.equals("enable_texture_animation")) {
                                 return true;
                              }

                              if (property.equals("texture_scale")
                                 || property.equals("texture_final_opacity")
                                 || property.equals("texture_x_offset")
                                 || property.equals("texture_y_offset")) {
                                 return true;
                              }

                              if (property.equals("texture_frame_width_ratio") || property.equals("texture_frame_height_ratio")) {
                                 String enableKey = prefix + "enable_texture_animation";
                                 if (configKeys.contains(enableKey)) {
                                    return !getConfigBoolean.apply(enableKey);
                                 }

                                 return true;
                              }

                              String enableKey = prefix + "enable_texture_animation";
                              if (configKeys.contains(enableKey)) {
                                 return getConfigBoolean.apply(enableKey);
                              }
                           }
                        }

                        return true;
                     }
                  };
            List<String> sortedKeys = new ArrayList<>(configKeys);
            Collections.sort(sortedKeys);

            for (String key : sortedKeys) {
               if ((!"kill_icon/valorant".equals(elementId) || !key.equals("display_duration"))
                  && (!key.startsWith("ring_effect_normal_") || !configKeys.contains("ring_effect_crit_color"))) {
                  JsonElement defaultElement = defaultConfig.get(key);
                  if (defaultElement != null && defaultElement.isJsonPrimitive()) {
                     JsonPrimitive primitive = defaultElement.getAsJsonPrimitive();
                     ConfigType registryType = ElementConfigRegistry.getType(elementId, key);
                     if (registryType != null) {
                        boolean isFormatConfigKey = BonusRegistry.isFormatKey(key);
                        int formatBonusType = isFormatConfigKey ? BonusRegistry.getTypeByFormatKey(key) : -1;
                        int killFeedType = "subtitle/kill_feed".equals(elementId) ? KillTypeRegistry.getKillTypeByFormatKey(key) : -1;
                        boolean isHonorFormatKey = "kill_icon/honor".equals(elementId) && HonorRegistry.isFormatKey(key);
                        String honorFormatId = isHonorFormatKey ? HonorRegistry.getHonorIdByFormatKey(key) : null;
                        String displayName;
                        if (isFormatConfigKey) {
                           displayName = I18n.get(BonusRegistry.nameKey(formatBonusType), new Object[0]);
                        } else if (killFeedType != -1) {
                           displayName = I18n.get(KillTypeRegistry.get(killFeedType).displayName(), new Object[0]);
                        } else if (isHonorFormatKey && honorFormatId != null) {
                           displayName = I18n.get(HonorRegistry.get(honorFormatId).displayNameKey(), new Object[0]);
                        } else {
                           String nameKey = "battlefield_ops.client.gui.config.element." + elementId.replace("/", ".") + "." + key;
                           if (I18nCompat.exists(nameKey)) {
                              displayName = I18n.get(nameKey, new Object[0]);
                           } else {
                              String genericKey = "battlefield_ops.client.gui.config.generic." + key;
                              if (I18nCompat.exists(genericKey)) {
                                 displayName = I18n.get(genericKey, new Object[0]);
                              } else if (!key.startsWith("anim_")) {
                                 displayName = key;
                              } else {
                                 String matchingTexture = null;

                                 for (String texture : ElementTextureDefinition.getTextures(elementId)) {
                                    if (key.startsWith("anim_" + texture + "_")) {
                                       matchingTexture = texture;
                                       break;
                                    }
                                 }

                                 if (matchingTexture != null) {
                                    String prefix = "anim_" + matchingTexture + "_";
                                    String actualProperty = key.substring(prefix.length());
                                    String animGenericKey = "battlefield_ops.client.gui.config.generic." + actualProperty;
                                    if (I18nCompat.exists(animGenericKey)) {
                                       displayName = I18n.get(animGenericKey, new Object[0]);
                                    } else {
                                       displayName = key;
                                    }
                                 } else {
                                    displayName = key;
                                 }
                              }
                           }
                        }

                        if (key.startsWith("texture_style_")) {
                           String styleKey = "battlefield_ops.client.gui.config.generic.official_texture_select";
                           if (I18nCompat.exists(styleKey)) {
                              displayName = I18n.get(styleKey, new Object[0]);
                           }
                        }

                        if (key.startsWith("custom_texture_")) {
                           String customKey = "battlefield_ops.client.gui.config.generic.custom_texture_select";
                           if (I18nCompat.exists(customKey)) {
                              displayName = I18n.get(customKey, new Object[0]);
                           }
                        }

                        if (key.startsWith("texture_mode_")) {
                           String modeKey = "battlefield_ops.client.gui.config.generic.texture_select_mode";
                           if (I18nCompat.exists(modeKey)) {
                              displayName = I18n.get(modeKey, new Object[0]);
                           }
                        }

                        if (key.startsWith("vanilla_texture_")) {
                           String vanillaKey = "battlefield_ops.client.gui.config.generic.vanilla_texture_select";
                           if (I18nCompat.exists(vanillaKey)) {
                              displayName = I18n.get(vanillaKey, new Object[0]);
                           }
                        }

                        String registryDep = ElementConfigRegistry.getDependsOn(elementId, key);
                        Supplier<Boolean> activeCondition;
                        if (registryDep != null) {
                           activeCondition = () -> !configKeys.contains(registryDep) || getConfigBoolean.apply(registryDep);
                        } else {
                           activeCondition = getDependency.apply(key);
                        }

                        if (registryType == ConfigType.BOOLEAN) {
                           boolean defaultValue = primitive.getAsBoolean();
                           boolean currentValue = currentConfig.has(key) ? currentConfig.get(key).getAsBoolean() : defaultValue;
                           BooleanConfigEntry entry = new BooleanConfigEntry(
                              0,
                              0,
                              0,
                              0,
                              1296319556,
                              0.3F,
                              displayName,
                              key,
                              "battlefield_ops.config.desc." + key,
                              currentValue,
                              defaultValue,
                              newValue -> ElementConfigManager.updateConfigValue(presetId, elementId, key, String.valueOf(newValue)),
                              activeCondition
                           );
                           content.getConfigRows().add(entry);
                        } else if (registryType == ConfigType.INT) {
                           int defaultValue = primitive.getAsInt();
                           int currentValue = currentConfig.has(key) ? currentConfig.get(key).getAsInt() : defaultValue;
                           IntegerConfigEntry entry = new IntegerConfigEntry(
                              0,
                              0,
                              0,
                              0,
                              1296319556,
                              0.3F,
                              displayName,
                              key,
                              "battlefield_ops.config.desc." + key,
                              currentValue,
                              defaultValue,
                              newValue -> ElementConfigManager.updateConfigValue(presetId, elementId, key, String.valueOf(newValue)),
                              content.getTextInputDialog(),
                              activeCondition
                           );
                           content.getConfigRows().add(entry);
                        } else if (registryType == ConfigType.FLOAT) {
                           float defaultValue = primitive.getAsFloat();
                           float currentValue = currentConfig.has(key) ? currentConfig.get(key).getAsFloat() : defaultValue;
                           FloatConfigEntry entry = new FloatConfigEntry(
                              0,
                              0,
                              0,
                              0,
                              1296319556,
                              0.3F,
                              displayName,
                              key,
                              "battlefield_ops.config.desc." + key,
                              currentValue,
                              defaultValue,
                              newValue -> ElementConfigManager.updateConfigValue(presetId, elementId, key, String.valueOf(newValue)),
                              content.getTextInputDialog(),
                              activeCondition
                           );
                           content.getConfigRows().add(entry);
                        } else {
                           String defaultValue = primitive.getAsString();
                           String currentValue = currentConfig.has(key) ? currentConfig.get(key).getAsString() : defaultValue;
                           String resolvedCurrentValue = currentValue;
                           String resolvedDescription = isFormatConfigKey
                              ? I18n.get(BonusRegistry.descKey(formatBonusType), new Object[0])
                              : (
                                 isHonorFormatKey && honorFormatId != null
                                    ? I18n.get(HonorRegistry.get(honorFormatId).descriptionKey(), new Object[0])
                                    : "battlefield_ops.config.desc." + key
                              );
                           boolean isColorConfig = registryType == ConfigType.COLOR;
                           if ("screen_anchor".equals(key)) {
                              List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();

                              for (String anchorId : ScreenAnchor.getAnchorIds()) {
                                 choices.add(
                                    new FixedChoiceConfigEntry.Choice(
                                       anchorId, I18n.get("battlefield_ops.config.choice.screen_anchor." + anchorId, new Object[0])
                                    )
                                 );
                              }

                              FixedChoiceConfigEntry entry = new FixedChoiceConfigEntry(
                                 0,
                                 0,
                                 0,
                                 0,
                                 1296319556,
                                 0.3F,
                                 displayName,
                                 key,
                                 "battlefield_ops.config.desc." + key,
                                 resolvedCurrentValue,
                                 defaultValue,
                                 choices,
                                 newValue -> {
                                    if (newValue != null && !newValue.equals(resolvedCurrentValue)) {
                                       JsonObject live = ElementConfigManager.getElementConfig(presetId, elementId);
                                       String oldAnchor = live != null && live.has("screen_anchor") ? live.get("screen_anchor").getAsString() : "bottom_center";
                                       int oldX = live != null && live.has("x_offset") ? live.get("x_offset").getAsInt() : 0;
                                       int oldY = live != null && live.has("y_offset") ? live.get("y_offset").getAsInt() : 0;
                                       int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                                       int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
                                       int newX = ScreenAnchor.translateXOffset(oldAnchor, newValue, oldX, screenW);
                                       int newY = ScreenAnchor.translateYOffset(oldAnchor, newValue, oldY, screenH);
                                       ElementConfigManager.updateConfigValue(presetId, elementId, "screen_anchor", newValue);
                                       ElementConfigManager.updateConfigValue(presetId, elementId, "x_offset", String.valueOf(newX));
                                       ElementConfigManager.updateConfigValue(presetId, elementId, "y_offset", String.valueOf(newY));
                                    }
                                 },
                                 content.getChoiceListDialog(),
                                 activeCondition
                              );
                              content.getConfigRows().add(entry);
                           } else if (!"scroll_direction".equals(key) && !"direction".equals(key)) {
                              if (key.startsWith("texture_mode_")) {
                                 List<FixedChoiceConfigEntry.Choice> choices = List.of(
                                    new FixedChoiceConfigEntry.Choice(
                                       "custom", I18n.get("battlefield_ops.config.choice.texture_mode.custom", new Object[0])
                                    ),
                                    new FixedChoiceConfigEntry.Choice(
                                       "official", I18n.get("battlefield_ops.config.choice.texture_mode.official", new Object[0])
                                    ),
                                    new FixedChoiceConfigEntry.Choice(
                                       "vanilla", I18n.get("battlefield_ops.config.choice.texture_mode.vanilla", new Object[0])
                                    )
                                 );
                                 String textureKey = key.substring("texture_mode_".length());
                                 FixedChoiceConfigEntry entry = new FixedChoiceConfigEntry(
                                    0,
                                    0,
                                    0,
                                    0,
                                    1296319556,
                                    0.3F,
                                    displayName,
                                    key,
                                    "battlefield_ops.config.desc.texture_select_mode",
                                    resolvedCurrentValue,
                                    defaultValue,
                                    choices,
                                    newValue -> {
                                       if (newValue == null || !newValue.equals(resolvedCurrentValue)) {
                                          ElementConfigManager.updateConfigValue(presetId, elementId, key, newValue);
                                          if ("vanilla".equalsIgnoreCase(newValue)) {
                                             JsonObject updated = ElementConfigManager.getElementConfig(presetId, elementId);
                                             String vanillaKey = ElementTextureDefinition.getVanillaTextureKey(textureKey);
                                             String vanillaValue = updated != null && updated.has(vanillaKey) ? updated.get(vanillaKey).getAsString() : null;
                                             if (!ExternalTextureManager.isVanillaTextureAvailable(vanillaValue)) {
                                                elementContent.getChoiceListDialog().hide();
                                                elementContent.getPromptDialog()
                                                   .show(
                                                      I18n.get("battlefield_ops.client.gui.prompt.texture_unavailable", new Object[0]),
                                                      PromptDialog.PromptType.ERROR,
                                                      null
                                                   );
                                                ElementConfigManager.updateConfigValue(presetId, elementId, key, "official");
                                                elementContent.rebuildUIFromConfig();
                                                return;
                                             }
                                          }

                                          String fileName = ElementTextureDefinition.getSelectedTextureFileName(presetId, elementId, textureKey);
                                          boolean gifDerived = "custom".equalsIgnoreCase(newValue)
                                             && ExternalTextureManager.isGifDerivedCustomTexture(presetId, fileName);
                                          elementContent.handleTextureBindingChanged(textureKey, fileName, gifDerived);
                                       }
                                    },
                                    content.getChoiceListDialog(),
                                    activeCondition
                                 );
                                 content.getConfigRows().add(entry);
                              } else if (key.startsWith("vanilla_texture_")) {
                                 List<FixedChoiceConfigEntry.Choice> choices = ElementConfigBuilderRegistry.getCachedVanillaItemChoices();
                                 String textureKey = key.substring("vanilla_texture_".length());
                                 FixedChoiceConfigEntry entry = new FixedChoiceConfigEntry(
                                    0,
                                    0,
                                    0,
                                    0,
                                    1296319556,
                                    0.3F,
                                    displayName,
                                    key,
                                    "battlefield_ops.config.desc.vanilla_texture_select",
                                    resolvedCurrentValue,
                                    defaultValue,
                                    choices,
                                    newValue -> {
                                       if (newValue == null || !newValue.equals(resolvedCurrentValue)) {
                                          if (!ExternalTextureManager.isVanillaTextureAvailable(newValue)) {
                                             elementContent.getChoiceListDialog().hide();
                                             elementContent.getPromptDialog()
                                                .show(
                                                   I18n.get("battlefield_ops.client.gui.prompt.texture_unavailable", new Object[0]),
                                                   PromptDialog.PromptType.ERROR,
                                                   null
                                                );
                                             ElementConfigManager.updateConfigValue(presetId, elementId, key, resolvedCurrentValue);
                                             elementContent.rebuildUIFromConfig();
                                          } else {
                                             ElementConfigManager.updateConfigValue(presetId, elementId, key, newValue);
                                             elementContent.handleTextureBindingChanged(textureKey, newValue, false);
                                          }
                                       }
                                    },
                                    content.getChoiceListDialog(),
                                    activeCondition
                                 );
                                 content.getConfigRows().add(entry);
                              } else if (key.startsWith("texture_style_")) {
                                 List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
                                 String textureKey = key.substring("texture_style_".length());

                                 for (String fileName : ExternalTextureManager.getAllTextureFileNames()) {
                                    String baseName = fileName.endsWith(".png") ? fileName.substring(0, fileName.length() - 4) : fileName;
                                    String labelKey = "battlefield_ops.client.gui.texture.file." + baseName;
                                    String label = I18nCompat.exists(labelKey) ? I18n.get(labelKey, new Object[0]) : baseName;
                                    choices.add(new FixedChoiceConfigEntry.Choice(fileName, label));
                                 }

                                 FixedChoiceConfigEntry entry = new FixedChoiceConfigEntry(
                                    0,
                                    0,
                                    0,
                                    0,
                                    1296319556,
                                    0.3F,
                                    displayName,
                                    key,
                                    "battlefield_ops.config.desc.official_texture_select",
                                    resolvedCurrentValue,
                                    defaultValue,
                                    choices,
                                    newValue -> {
                                       if (newValue == null || !newValue.equals(resolvedCurrentValue)) {
                                          ElementConfigManager.updateConfigValue(presetId, elementId, key, newValue);
                                          elementContent.handleTextureBindingChanged(textureKey, newValue, false);
                                       }
                                    },
                                    content.getChoiceListDialog(),
                                    activeCondition
                                 );
                                 content.getConfigRows().add(entry);
                              } else if (!key.startsWith("custom_texture_")) {
                                 if ("team".equals(key) && ElementConfigBuilderRegistry.TEAM_ELEMENT_IDS.contains(elementId)) {
                                    List<FixedChoiceConfigEntry.Choice> choices = List.of(
                                       new FixedChoiceConfigEntry.Choice("ct", "CT"), new FixedChoiceConfigEntry.Choice("t", "T")
                                    );
                                    FixedChoiceConfigEntry entry = new FixedChoiceConfigEntry(
                                       0,
                                       0,
                                       0,
                                       0,
                                       1296319556,
                                       0.3F,
                                       displayName,
                                       key,
                                       resolvedDescription,
                                       resolvedCurrentValue,
                                       defaultValue,
                                       choices,
                                       newValue -> ElementConfigManager.updateConfigValue(presetId, elementId, key, newValue),
                                       content.getChoiceListDialog(),
                                       activeCondition
                                    );
                                    content.getConfigRows().add(entry);
                                 } else if (key.endsWith("texture_animation_orientation")) {
                                    List<FixedChoiceConfigEntry.Choice> choices = List.of(
                                       new FixedChoiceConfigEntry.Choice("horizontal", I18n.get("battlefield_ops.config.choice.horizontal", new Object[0])),
                                       new FixedChoiceConfigEntry.Choice("vertical", I18n.get("battlefield_ops.config.choice.vertical", new Object[0]))
                                    );
                                    FixedChoiceConfigEntry entry = new FixedChoiceConfigEntry(
                                       0,
                                       0,
                                       0,
                                       0,
                                       1296319556,
                                       0.3F,
                                       displayName,
                                       key,
                                       resolvedDescription,
                                       resolvedCurrentValue,
                                       defaultValue,
                                       choices,
                                       newValue -> ElementConfigManager.updateConfigValue(presetId, elementId, key, newValue),
                                       content.getChoiceListDialog(),
                                       activeCondition
                                    );
                                    content.getConfigRows().add(entry);
                                 } else if (key.endsWith("texture_animation_play_style")) {
                                    List<FixedChoiceConfigEntry.Choice> choices = List.of(
                                       new FixedChoiceConfigEntry.Choice("sequential", I18n.get("battlefield_ops.config.choice.sequential", new Object[0])),
                                       new FixedChoiceConfigEntry.Choice("reverse", I18n.get("battlefield_ops.config.choice.reverse", new Object[0])),
                                       new FixedChoiceConfigEntry.Choice("pingpong", I18n.get("battlefield_ops.config.choice.pingpong", new Object[0])),
                                       new FixedChoiceConfigEntry.Choice("random", I18n.get("battlefield_ops.config.choice.random", new Object[0]))
                                    );
                                    FixedChoiceConfigEntry entry = new FixedChoiceConfigEntry(
                                       0,
                                       0,
                                       0,
                                       0,
                                       1296319556,
                                       0.3F,
                                       displayName,
                                       key,
                                       resolvedDescription,
                                       resolvedCurrentValue,
                                       defaultValue,
                                       choices,
                                       newValue -> ElementConfigManager.updateConfigValue(presetId, elementId, key, newValue),
                                       content.getChoiceListDialog(),
                                       activeCondition
                                    );
                                    content.getConfigRows().add(entry);
                                 } else if (!key.equals("reset_kill_combo") && !key.equals("reset_assist_combo")) {
                                    if (isColorConfig) {
                                       HexColorConfigEntry entry = new HexColorConfigEntry(
                                          0,
                                          0,
                                          0,
                                          0,
                                          1296319556,
                                          0.3F,
                                          displayName,
                                          key,
                                          resolvedDescription,
                                          resolvedCurrentValue,
                                          defaultValue,
                                          newValue -> ElementConfigManager.updateConfigValue(presetId, elementId, key, newValue),
                                          content.getTextInputDialog(),
                                          content.getColorPickerDialog(),
                                          activeCondition
                                       );
                                       content.getConfigRows().add(entry);
                                    } else {
                                       StringConfigEntry entry = new StringConfigEntry(
                                          0,
                                          0,
                                          0,
                                          0,
                                          1296319556,
                                          0.3F,
                                          displayName,
                                          key,
                                          resolvedDescription,
                                          resolvedCurrentValue,
                                          defaultValue,
                                          newValue -> ElementConfigManager.updateConfigValue(presetId, elementId, key, newValue),
                                          content.getTextInputDialog(),
                                          activeCondition
                                       );
                                       content.getConfigRows().add(entry);
                                    }
                                 } else {
                                    List<FixedChoiceConfigEntry.Choice> choices = key.equals("reset_assist_combo")
                                       ? List.of(
                                          new FixedChoiceConfigEntry.Choice("death", I18n.get("battlefield_ops.config.choice.reset_death", new Object[0])),
                                          new FixedChoiceConfigEntry.Choice("time", I18n.get("battlefield_ops.config.choice.reset_time", new Object[0])),
                                          new FixedChoiceConfigEntry.Choice("logout", I18n.get("battlefield_ops.config.choice.reset_logout", new Object[0])),
                                          new FixedChoiceConfigEntry.Choice("never", I18n.get("battlefield_ops.config.choice.reset_never", new Object[0]))
                                       )
                                       : List.of(
                                          new FixedChoiceConfigEntry.Choice("server", I18n.get("battlefield_ops.config.choice.reset_server", new Object[0])),
                                          new FixedChoiceConfigEntry.Choice("death", I18n.get("battlefield_ops.config.choice.reset_death", new Object[0])),
                                          new FixedChoiceConfigEntry.Choice("time", I18n.get("battlefield_ops.config.choice.reset_time", new Object[0])),
                                          new FixedChoiceConfigEntry.Choice("logout", I18n.get("battlefield_ops.config.choice.reset_logout", new Object[0])),
                                          new FixedChoiceConfigEntry.Choice("never", I18n.get("battlefield_ops.config.choice.reset_never", new Object[0]))
                                       );
                                    FixedChoiceConfigEntry entry = new FixedChoiceConfigEntry(
                                       0,
                                       0,
                                       0,
                                       0,
                                       1296319556,
                                       0.3F,
                                       displayName,
                                       key,
                                       resolvedDescription,
                                       resolvedCurrentValue,
                                       defaultValue,
                                       choices,
                                       newValue -> ElementConfigManager.updateConfigValue(presetId, elementId, key, newValue),
                                       content.getChoiceListDialog(),
                                       activeCondition
                                    );
                                    content.getConfigRows().add(entry);
                                 }
                              } else {
                                 List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
                                 Map<String, String> customLabels = ExternalTextureManager.getCustomTextureLabels(presetId);

                                 for (String fileName : ExternalTextureManager.getCustomTextureFileNames(presetId)) {
                                    String label = customLabels.getOrDefault(fileName, fileName);
                                    choices.add(new FixedChoiceConfigEntry.Choice(fileName, label));
                                 }

                                 if (choices.isEmpty()) {
                                    choices.add(
                                       new FixedChoiceConfigEntry.Choice(
                                          "", I18n.get("battlefield_ops.client.gui.config.choice.custom_texture_none", new Object[0])
                                       )
                                    );
                                 }

                                 String textureKey = key.substring("custom_texture_".length());
                                 FixedChoiceConfigEntry entry = new FixedChoiceConfigEntry(
                                    0,
                                    0,
                                    0,
                                    0,
                                    1296319556,
                                    0.3F,
                                    displayName,
                                    key,
                                    "battlefield_ops.config.desc.custom_texture_select",
                                    resolvedCurrentValue,
                                    defaultValue,
                                    choices,
                                    newValue -> {
                                       if (newValue == null || !newValue.equals(resolvedCurrentValue)) {
                                          ElementConfigManager.updateConfigValue(presetId, elementId, key, newValue);
                                          if (newValue != null && !newValue.isEmpty()) {
                                             boolean gifDerived = ExternalTextureManager.isGifDerivedCustomTexture(presetId, newValue);
                                             elementContent.handleTextureBindingChanged(textureKey, newValue, gifDerived);
                                          }
                                       }
                                    },
                                    content.getChoiceListDialog(),
                                    activeCondition
                                 );
                                 content.getConfigRows().add(entry);
                              }
                           } else {
                              List<FixedChoiceConfigEntry.Choice> choices = List.of(
                                 new FixedChoiceConfigEntry.Choice("left", I18n.get("battlefield_ops.config.choice.scroll_direction.left", new Object[0])),
                                 new FixedChoiceConfigEntry.Choice("right", I18n.get("battlefield_ops.config.choice.scroll_direction.right", new Object[0]))
                              );
                              FixedChoiceConfigEntry entry = new FixedChoiceConfigEntry(
                                 0,
                                 0,
                                 0,
                                 0,
                                 1296319556,
                                 0.3F,
                                 displayName,
                                 key,
                                 "battlefield_ops.config.desc." + key,
                                 resolvedCurrentValue,
                                 defaultValue,
                                 choices,
                                 newValue -> {
                                    if (newValue == null || !newValue.equals(resolvedCurrentValue)) {
                                       ElementConfigManager.updateConfigValue(presetId, elementId, key, newValue);
                                    }
                                 },
                                 content.getChoiceListDialog(),
                                 activeCondition
                              );
                              content.getConfigRows().add(entry);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
