package cn.bfops.killicon.client.render.impl;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.config.ElementTextureDefinition;
import cn.bfops.killicon.client.gui.tabs.PreviewTextureFocusContext;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.render.PreviewRenderTimeContext;
import cn.bfops.killicon.client.render.ScreenAnchor;
import cn.bfops.killicon.client.render.effect.IconGlowRenderEffect;
import cn.bfops.killicon.client.sounds.ExternalSoundManager;
import cn.bfops.killicon.client.textures.ExternalTextureManager;
import cn.bfops.killicon.client.util.ClientMessageLogger;
import cn.bfops.killicon.common.killtype.KillTypeRegistry;

public class Battlefield1Renderer implements IHudRenderer {
   @Override public void resetPreview(){displayQueue.clear();isVisible=false;startTime=-1;}

   private static final String SOUND_NAME = "killsound_bf1";
   private static final String HEADSHOT_SOUND_NAME = "headshotkillsound_bf1";
   private boolean visible = true;
   private int iconSize = 40;
   private int borderSize = 3;
   private int xOffset = 0;
   private int yOffset = 0;
   private String screenAnchor = "bottom_center";
   private int backgroundColor = 0;
   private float iconBoxAlpha = 0.2F;
   private float textBoxAlpha = 0.1F;
   private float scaleWeapon = 1.0F;
   private float scaleVictim = 1.2F;
   private float scaleHealth = 1.5F;
   private int colorVictim = 16711680;
   private long animationDuration = 200L;
   private long displayDuration = 300L;
   private boolean enableIconGlow = false;
   private int iconGlowColor = 16777215;
   private float iconGlowIntensity = 0.45F;
   private float iconGlowSize = 4.0F;
   private JsonObject currentConfig;
   private long startTime = -1L;
   private boolean isVisible = false;
   private static final int MAX_QUEUE_SIZE = 5;
   private final Queue<IHudRenderer.TriggerContext> displayQueue = new ArrayDeque<>();
   private long lastSwitchTime = 0L;
   private String weaponName = "";
   private String victimName = "";
   private String healthText = "";
   private int killType = 0;
   private String currentIconPath = "killicon_battlefield1_default.png";

   @Override
   public void trigger(IHudRenderer.TriggerContext context) {
      if (context.type() != 4) {
         JsonObject config = ConfigManager.getElementConfig("kill_icon", "battlefield1");
         if (config != null) {
            if (this.displayQueue.size() < 5) {
               this.displayQueue.offer(context);
            }
         }
      }
   }

   private void processContext(IHudRenderer.TriggerContext context) {
      JsonObject config = ConfigManager.getElementConfig("kill_icon", "battlefield1");
      if (config != null) {
         this.loadConfig(config);
         if (!this.visible) {
            this.isVisible = false;
         } else {
            this.killType = context.type();
            Minecraft mc = Minecraft.getInstance();
            String soundSlot = "bf1_default";
            if (this.killType == 1) {
               soundSlot = "bf1_headshot";
            }

            String textureKey = this.getTextureKey(this.killType);
            this.currentIconPath = ElementTextureDefinition.getSelectedTextureFileName(
               ConfigManager.getCurrentPresetId(), "kill_icon/battlefield1", textureKey, this.currentConfig
            );
            ExternalSoundManager.playConfiguredSound(ConfigManager.getCurrentPresetId(), soundSlot);
            String extraData = context.extraData() == null ? "" : context.extraData();
            if (this.killType == 5) {
               if (!extraData.isEmpty() && extraData.contains("|")) {
                  String[] parts = extraData.split("\\|", 2);
                  if (parts.length > 0) {
                     this.victimName = I18n.get(parts[0], new Object[0]);
                  } else {
                     this.victimName = I18n.get("battlefield_ops.client.text.vehicle", new Object[0]);
                  }

                  if (parts.length > 1) {
                     this.healthText = parts[1];
                  } else {
                     this.healthText = "?";
                  }
               } else {
                  this.victimName = I18n.get("battlefield_ops.client.text.vehicle", new Object[0]);
                  this.healthText = "?";
               }

               if (mc.player != null) {
                  if (mc.player.getVehicle() != null) {
                     this.weaponName = mc.player.getVehicle().getDisplayName().getString();
                  } else {
                     ItemStack held = mc.player.getMainHandItem();
                     this.weaponName = held.isEmpty() ? I18n.get("battlefield_ops.client.text.bare_hand", new Object[0]) : held.getHoverName().getString();
                  }
               } else {
                  this.weaponName = "Unknown";
               }
            } else {
               if (mc.player != null) {
                  if (mc.player.getVehicle() != null) {
                     this.weaponName = mc.player.getVehicle().getDisplayName().getString();
                  } else {
                     ItemStack held = mc.player.getMainHandItem();
                     this.weaponName = held.isEmpty() ? I18n.get("battlefield_ops.client.text.bare_hand", new Object[0]) : held.getHoverName().getString();
                  }
               } else {
                  this.weaponName = "Unknown";
               }

               String nameOverride = null;
               if (!extraData.isEmpty()) {
                  if (extraData.contains("|")) {
                     nameOverride = extraData.substring(0, extraData.indexOf("|"));
                  } else {
                     nameOverride = extraData;
                  }
               }

               if (nameOverride != null) {
                  this.victimName = I18n.get(nameOverride, new Object[0]);
                  if (mc.level != null && context.entityId() != -1) {
                     if (mc.level.getEntity(context.entityId()) instanceof LivingEntity living) {
                        this.healthText = String.valueOf((int)living.getMaxHealth());
                     } else {
                        this.healthText = "20";
                     }
                  } else {
                     this.healthText = "?";
                  }
               } else if (mc.level != null && context.entityId() != -1) {
                  Entity entity = mc.level.getEntity(context.entityId());
                  if (entity != null) {
                     this.victimName = entity.getDisplayName().getString();
                     if (entity instanceof LivingEntity living) {
                        float maxHealth = living.getMaxHealth();
                        this.healthText = String.valueOf((int)maxHealth);
                     } else {
                        this.healthText = "20";
                     }
                  } else {
                     this.victimName = "Unknown";
                     this.healthText = "?";
                  }
               } else {
                  this.victimName = "Unknown";
                  this.healthText = "?";
               }
            }

            this.startTime = PreviewRenderTimeContext.currentTimeMillis();
            this.lastSwitchTime = this.startTime;
            this.isVisible = true;
         }
      }
   }

   private String getTextureKey(int killType) {
      return KillTypeRegistry.get(killType).bf1TextureKey();
   }

   private long resolveQueueSwitchDelay(int nextKillType) {
      long baseDelay = this.animationDuration;
      if (this.killType == 5 || nextKillType == 5) {
         baseDelay = Math.max(baseDelay, this.displayDuration);
      }

      return baseDelay;
   }

   public void triggerPreview(int killType, String weaponName, String victimName, String healthText) {
      JsonObject config = ConfigManager.getElementConfig("kill_icon", "battlefield1");
      if (config != null) {
         this.loadConfig(config);
         if (!this.visible) {
            this.isVisible = false;
         } else {
            this.killType = killType;
            this.weaponName = weaponName;
            this.victimName = victimName;
            this.healthText = healthText;
            String textureKey = this.getTextureKey(this.killType);
            this.currentIconPath = ElementTextureDefinition.getSelectedTextureFileName(
               ConfigManager.getCurrentPresetId(), "kill_icon/battlefield1", textureKey, this.currentConfig
            );
            this.startTime = PreviewRenderTimeContext.currentTimeMillis();
            this.lastSwitchTime = this.startTime;
            this.isVisible = true;
            this.displayQueue.clear();
         }
      }
   }

   @Override
   public void render(GuiGraphics guiGraphics, float partialTick) {
      long currentTime = PreviewRenderTimeContext.currentTimeMillis();
      if (!this.displayQueue.isEmpty()) {
         boolean shouldSwitch = !this.isVisible;
         if (this.isVisible) {
            long elapsedSinceSwitch = currentTime - this.lastSwitchTime;
            IHudRenderer.TriggerContext nextContext = this.displayQueue.peek();
            int nextKillType = nextContext != null ? nextContext.type() : 0;
            long switchDelay = this.resolveQueueSwitchDelay(nextKillType);
            if (elapsedSinceSwitch >= switchDelay) {
               shouldSwitch = true;
            }
         }

         if (shouldSwitch) {
            this.processContext(this.displayQueue.poll());
         }
      }

      if (this.isVisible && this.startTime != -1L) {
         Minecraft mc = Minecraft.getInstance();
         int screenWidth = mc.getWindow().getGuiScaledWidth();
         int screenHeight = mc.getWindow().getGuiScaledHeight();
         float centerX = (float)ScreenAnchor.resolveCenterX(this.screenAnchor, this.xOffset, screenWidth);
         float centerY = (float)ScreenAnchor.resolveCenterY(this.screenAnchor, this.yOffset, screenHeight);
         this.renderInternal(guiGraphics, partialTick, currentTime, centerX, centerY);
      }
   }

   public void renderAt(GuiGraphics guiGraphics, float partialTick, float centerX, float centerY) {
      if (this.isVisible && this.startTime != -1L) {
         long currentTime = PreviewRenderTimeContext.currentTimeMillis();
         this.renderInternal(guiGraphics, partialTick, currentTime, centerX, centerY);
      }
   }

   private void renderInternal(GuiGraphics guiGraphics, float partialTick, long currentTime, float centerX, float centerY) {
      long elapsed = currentTime - this.startTime;
      float alpha = this.getAlpha(elapsed);
      float globalScale = this.getGlobalScale(elapsed);
      if (this.displayQueue.isEmpty() && alpha <= 0.001F && elapsed > this.displayDuration) {
         this.isVisible = false;
      } else {
         Minecraft mc = Minecraft.getInstance();
         Font font = mc.font;
         int weaponW = font.width(this.weaponName);
         int victimW = font.width(this.victimName);
         int healthW = font.width(this.healthText);
         int fontH = 9;
         float effWeaponW = (float)weaponW * this.scaleWeapon;
         float effWeaponH = (float)fontH * this.scaleWeapon;
         float effVictimW = (float)victimW * this.scaleVictim;
         float effVictimH = (float)fontH * this.scaleVictim;
         float effHealthW = (float)healthW * this.scaleHealth;
         float effHealthH = (float)fontH * this.scaleHealth;
         float weaponX = -effWeaponW / 2.0F;
         float weaponY = -effWeaponH / 2.0F;
         float weaponRight = weaponX + effWeaponW;
         float weaponBottom = weaponY + effWeaponH;
         float victimX = weaponRight - effVictimW;
         float victimBottom = weaponY - (float)this.borderSize;
         float victimY = victimBottom - effVictimH;
         float healthX = weaponRight + (float)this.borderSize;
         float midY = (victimY + weaponBottom) / 2.0F;
         float healthY = midY - effHealthH / 2.0F;
         float healthRight = healthX + effHealthW;
         float healthBottom = healthY + effHealthH;
         float destroyW = 0.0F;
         float destroyH = 0.0F;
         float destroyX = 0.0F;
         float destroyY = 0.0F;
         if (this.killType == 5) {
            String destroyText = I18n.get("battlefield_ops.client.text.vehicle_destroyed", new Object[0]);
            destroyW = (float)font.width(destroyText) * this.scaleVictim;
            destroyH = 9.0F * this.scaleVictim;
            destroyY = midY - destroyH / 2.0F;
            destroyX = healthX - (float)this.borderSize - destroyW;
         }

         float subBoxTop = Math.min(weaponY, Math.min(healthY, victimY)) - (float)this.borderSize;
         float subBoxRight;
         if (this.killType == 5) {
            subBoxRight = Math.max(weaponRight, Math.max(healthRight, weaponRight)) + (float)this.borderSize;
         } else {
            float distY = healthY - subBoxTop;
            distY = Math.max(distY, (float)this.borderSize);
            subBoxRight = healthRight + distY;
         }

         float subBoxLeft = Math.min(weaponX, Math.min(healthX, victimX)) - (float)this.borderSize;
         float subBoxBottom = Math.max(weaponBottom, Math.max(healthBottom, victimBottom)) + (float)this.borderSize;
         if (this.killType == 5) {
            subBoxLeft = Math.min(subBoxLeft, destroyX - (float)this.borderSize);
            subBoxTop = Math.min(subBoxTop, destroyY - (float)this.borderSize);
            subBoxBottom = Math.max(subBoxBottom, destroyY + destroyH + (float)this.borderSize);
         }

         float subBoxW = subBoxRight - subBoxLeft;
         float subBoxH = subBoxBottom - subBoxTop;
         float iconBoxLeft = subBoxLeft - subBoxH;
         float iconBoxBottom = subBoxTop + subBoxH;
         PoseStack pose = guiGraphics.pose();
         pose.pushPose();
         pose.translate(centerX, centerY, 0.0F);
         pose.scale(globalScale, globalScale, 1.0F);
         float subBoxFinalAlpha = alpha * this.textBoxAlpha;
         int subBoxColor = (int)(subBoxFinalAlpha * 255.0F) << 24 | this.backgroundColor & 16777215;
         guiGraphics.fill((int)subBoxLeft, (int)subBoxTop, (int)subBoxRight, (int)subBoxBottom, subBoxColor);
         float iconBoxFinalAlpha = alpha * this.iconBoxAlpha;
         int iconBoxColor = (int)(iconBoxFinalAlpha * 255.0F) << 24 | this.backgroundColor & 16777215;
         guiGraphics.fill((int)iconBoxLeft, (int)subBoxTop, (int)subBoxLeft, (int)iconBoxBottom, iconBoxColor);
         float iconDrawSize = (float)this.iconSize;
         float iconCenterX = iconBoxLeft + subBoxH / 2.0F;
         float iconCenterY = subBoxTop + subBoxH / 2.0F;
         float iconDrawX = iconCenterX - iconDrawSize / 2.0F;
         float iconDrawY = iconCenterY - iconDrawSize / 2.0F;
         this.renderIcon(guiGraphics, iconDrawX, iconDrawY, iconDrawSize, alpha);
         int textAlpha = (int)(alpha * 255.0F);
         if (textAlpha < 5) {
            textAlpha = 5;
         }

         if (this.killType == 5) {
            String destroyText = I18n.get("battlefield_ops.client.text.vehicle_destroyed", new Object[0]);
            this.drawScaledText(guiGraphics, font, destroyText, destroyX, destroyY, this.scaleVictim, 16777215, textAlpha);
         } else {
            this.drawScaledText(guiGraphics, font, this.weaponName, weaponX, weaponY, this.scaleWeapon, 16777215, textAlpha);
            this.drawScaledText(guiGraphics, font, this.victimName, victimX, victimY, this.scaleVictim, this.colorVictim, textAlpha);
         }

         this.drawScaledText(guiGraphics, font, this.healthText, healthX, healthY, this.scaleHealth, 16777215, textAlpha);
         pose.popPose();
      }
   }

   private void renderIcon(GuiGraphics guiGraphics, float x, float y, float size, float alpha) {
      ResourceLocation texture = ExternalTextureManager.getTexture(this.currentIconPath);
      String textureKey = this.getBattlefieldTextureKey();
      float frameWidthRatio = this.resolveFrameRatio(textureKey, "texture_frame_width_ratio");
      float frameHeightRatio = this.resolveFrameRatio(textureKey, "texture_frame_height_ratio");
      float drawWidth = size * frameWidthRatio;
      float drawHeight = size * frameHeightRatio;
      float drawX = x + (size - drawWidth) / 2.0F;
      float drawY = y + (size - drawHeight) / 2.0F;
      float focusedAlpha = alpha * PreviewTextureFocusContext.alphaMultiplier("kill_icon/battlefield1", textureKey);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, focusedAlpha);
      RenderSystem.enableBlend();
      if (this.enableIconGlow) {
         IconGlowRenderEffect.drawGlowFrame(
            guiGraphics,
            texture,
            (int)drawX,
            (int)drawY,
            (int)drawWidth,
            (int)drawHeight,
            0,
            0,
            (int)drawWidth,
            (int)drawHeight,
            (int)drawWidth,
            (int)drawHeight,
            focusedAlpha,
            this.iconGlowColor,
            this.iconGlowIntensity,
            this.iconGlowSize
         );
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, focusedAlpha);
      }

      guiGraphics.blit(texture, (int)drawX, (int)drawY, 0.0F, 0.0F, (int)drawWidth, (int)drawHeight, (int)drawWidth, (int)drawHeight);
      RenderSystem.disableBlend();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private void drawScaledText(GuiGraphics guiGraphics, Font font, String text, float x, float y, float scale, int colorRGB, int alpha) {
      PoseStack pose = guiGraphics.pose();
      pose.pushPose();
      pose.translate(x, y, 0.0F);
      pose.scale(scale, scale, 1.0F);
      int color = alpha << 24 | colorRGB & 16777215;
      guiGraphics.drawString(font, text, 0, 0, color, true);
      pose.popPose();
   }

   private void loadConfig(JsonObject config) {
      try {
         this.currentConfig = config;
         this.visible = !config.has("visible") || config.get("visible").getAsBoolean();
         this.iconSize = config.has("icon_size") ? config.get("icon_size").getAsInt() : 40;
         this.borderSize = config.has("border_size") ? config.get("border_size").getAsInt() : 3;
         this.xOffset = config.has("x_offset") ? config.get("x_offset").getAsInt() : 0;
         this.yOffset = config.has("y_offset") ? config.get("y_offset").getAsInt() : 0;
         this.screenAnchor = config.has("screen_anchor") ? config.get("screen_anchor").getAsString() : "bottom_center";
         String bgHex = config.has("background_color") ? config.get("background_color").getAsString() : "#000000";
         this.backgroundColor = this.parseHex(bgHex, 0);
         int iconTrans = config.has("icon_box_opacity") ? config.get("icon_box_opacity").getAsInt() : 80;
         this.iconBoxAlpha = (float)(100 - iconTrans) / 100.0F;
         int textTrans = config.has("text_box_opacity") ? config.get("text_box_opacity").getAsInt() : 90;
         this.textBoxAlpha = (float)(100 - textTrans) / 100.0F;
         this.scaleWeapon = config.has("scale_weapon") ? config.get("scale_weapon").getAsFloat() : 1.0F;
         this.scaleVictim = config.has("scale_victim") ? config.get("scale_victim").getAsFloat() : 1.2F;
         this.scaleHealth = config.has("scale_health") ? config.get("scale_health").getAsFloat() : 1.5F;
         String vicHex = config.has("color_victim") ? config.get("color_victim").getAsString() : "#FF0000";
         this.colorVictim = this.parseHex(vicHex, 16711680);
         this.animationDuration = config.has("animation_duration") ? (long)(config.get("animation_duration").getAsFloat() * 1000.0F) : 200L;
         this.displayDuration = config.has("display_duration") ? (long)(config.get("display_duration").getAsFloat() * 1000.0F) : 300L;
         this.enableIconGlow = IconGlowRenderEffect.isEnabled(config);
         this.iconGlowColor = IconGlowRenderEffect.resolveColor(config);
         this.iconGlowIntensity = IconGlowRenderEffect.resolveIntensity(config);
         this.iconGlowSize = IconGlowRenderEffect.resolveSize(config);
      } catch (Exception var6) {
         ClientMessageLogger.chatWarn("battlefield_ops.client.config_error", "battlefield1");
         this.enableIconGlow = false;
         this.iconGlowColor = 16777215;
         this.iconGlowIntensity = 0.45F;
         this.iconGlowSize = 4.0F;
      }
   }

   private String getBattlefieldTextureKey() {
      return KillTypeRegistry.get(this.killType).bf1TextureKey();
   }

   private float resolveFrameRatio(String textureKey, String suffixKey) {
      if (this.currentConfig != null && textureKey != null) {
         String key = "anim_" + textureKey + "_" + suffixKey;
         if (!this.currentConfig.has(key)) {
            return 1.0F;
         } else {
            float value = this.currentConfig.get(key).getAsFloat();
            return value > 0.0F ? value : 1.0F;
         }
      } else {
         return 1.0F;
      }
   }

   private int parseHex(String hex, int def) {
      try {
         return Integer.parseInt(hex.replace("#", ""), 16);
      } catch (Exception var4) {
         return def;
      }
   }

   private float getAnimationProgress(long elapsed) {
      return elapsed < this.animationDuration ? (float)elapsed / (float)this.animationDuration : 1.0F;
   }

   private float getAlpha(long elapsed) {
      if (elapsed < this.animationDuration) {
         return (float)elapsed / (float)this.animationDuration;
      } else {
         long fadeOutStart = this.displayDuration;
         if (elapsed > fadeOutStart) {
            long fadeElapsed = elapsed - fadeOutStart;
            return fadeElapsed >= this.animationDuration ? 0.0F : 1.0F - (float)fadeElapsed / (float)this.animationDuration;
         } else {
            return 1.0F;
         }
      }
   }

   private float getGlobalScale(long elapsed) {
      if (elapsed < this.animationDuration) {
         float t = (float)elapsed / (float)this.animationDuration;
         float eased = 1.0F - (float)Math.pow((double)(1.0F - t), 3.0);
         return Mth.lerp(eased, 0.6F, 1.0F);
      } else {
         return 1.0F;
      }
   }
}
