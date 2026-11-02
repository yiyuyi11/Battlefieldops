package cn.bfops.killicon.client.sounds;

import com.google.gson.JsonObject;
import net.minecraft.util.Mth;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.common.killtype.KillTypeDefinition;
import cn.bfops.killicon.common.killtype.KillTypeRegistry;

public class SoundTriggerManager {
   public static void tryPlaySound(String category, String name, int killType, int comboCount, boolean hasHelmet, boolean isVictimPlayer) {
      JsonObject config = ConfigManager.getElementConfig(category, name);
      if (config != null) {
         boolean visible = !config.has("visible") || config.get("visible").getAsBoolean();
         if (visible) {
            if ("kill_icon".equals(category) && killType == 1) {
               float headshotVolumeScale = isVictimPlayer ? 1.0F : 2.0F;
               ExternalSoundManager.playConfiguredSound(ConfigManager.getCurrentPresetId(), "common_headshot_hit", false, headshotVolumeScale);
            }

            if (!"kill_icon".equals(category) || !"valorant".equals(name) || killType != 4 && killType != 5) {
               if ("kill_icon".equals(category) && "card".equals(name)) {
                  if (killType == 1) {
                     if (hasHelmet) {
                        ExternalSoundManager.playConfiguredSound(ConfigManager.getCurrentPresetId(), "card_armor_headshot");
                     } else {
                        ExternalSoundManager.playConfiguredSound(ConfigManager.getCurrentPresetId(), "card_headshot");
                     }
                  } else if (killType == 2) {
                     ExternalSoundManager.playConfiguredSound(ConfigManager.getCurrentPresetId(), "card_explosion");
                  } else if (killType == 3) {
                     ExternalSoundManager.playConfiguredSound(ConfigManager.getCurrentPresetId(), "card_crit");
                  } else {
                     ExternalSoundManager.playConfiguredSound(ConfigManager.getCurrentPresetId(), "card_default");
                  }
               } else if ("kill_icon".equals(category) && ("scrolling".equals(name) || "combo".equals(name) || "valorant".equals(name))) {
                  if (!"combo".equals(name) && !"valorant".equals(name)) {
                     KillTypeDefinition def = KillTypeRegistry.get(killType);
                     if (def != null) {
                        ExternalSoundManager.playConfiguredSound(ConfigManager.getCurrentPresetId(), def.scrollingSoundSlotId());
                     }
                  } else {
                     int count = "valorant".equals(name) ? Mth.clamp(comboCount, 1, 5) : Mth.clamp(comboCount, 1, 6);

                     String slotId = switch (count) {
                        case 1 -> "valorant".equals(name) ? "valorant_1" : "combo_1";
                        case 2 -> "valorant".equals(name) ? "valorant_2" : "combo_2";
                        case 3 -> "valorant".equals(name) ? "valorant_3" : "combo_3";
                        case 4 -> "valorant".equals(name) ? "valorant_4" : "combo_4";
                        case 5 -> "valorant".equals(name) ? "valorant_5" : "combo_5";
                        default -> "combo_6";
                     };
                     if ("valorant".equals(name)) {
                        float volumeScale = 1.0F;
                        ExternalSoundManager.playConfiguredSound(ConfigManager.getCurrentPresetId(), slotId, false, volumeScale);
                     } else {
                        ExternalSoundManager.playConfiguredSound(ConfigManager.getCurrentPresetId(), slotId);
                     }
                  }
               }
            }
         }
      }
   }

   public static void playHitSound() {
      ExternalSoundManager.playConfiguredSound(ConfigManager.getCurrentPresetId(), "common_hit");
   }

   public static void playHeadshotDamageSound() {
      ExternalSoundManager.playConfiguredSound(ConfigManager.getCurrentPresetId(), "common_headshot_hit");
   }

   public static void playScoreSound() {
      ExternalSoundManager.playConfiguredSound(ConfigManager.getCurrentPresetId(), "common_score", true);
   }
}
