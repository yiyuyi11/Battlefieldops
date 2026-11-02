package cn.bfops.killicon.client.render.impl;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.Team;
import cn.bfops.killicon.client.config.ConfigManager;
import cn.bfops.killicon.client.config.ElementTextureDefinition;
import cn.bfops.killicon.client.gui.tabs.PreviewTextureFocusContext;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.render.PreviewRenderTimeContext;
import cn.bfops.killicon.client.render.ScreenAnchor;
import cn.bfops.killicon.client.render.effect.IconGlowRenderEffect;
import cn.bfops.killicon.client.textures.ExternalTextureManager;

public class CardRenderer implements IHudRenderer {
   @Override public void resetPreview(){activeCards.clear();}

   private float configScale = 1.0F;
   private int configXOffset = 0;
   private int configYOffset = 0;
   private String configScreenAnchor = "bottom_center";
   private float animationDuration = 0.75F;
   private String colorTextCt = "9cc1eb";
   private String colorTextT = "d9ac5b";
   private float textScale = 1.0F;
   private String team = "ct";
   private boolean dynamicCardStyle = false;
   private int maxStackCount = 5;
   private boolean enableIconGlow = false;
   private int iconGlowColor = 16777215;
   private float iconGlowIntensity = 0.45F;
   private float iconGlowSize = 4.0F;
   private JsonObject currentConfig;
   private static final int CARD_SIZE = 256;
   private static final float MOVE_DISTANCE_MULTIPLIER = 0.9F;
   private final List<CardRenderer.CardInstance> activeCards = new ArrayList<>();
   private CardRenderer.PendingTrigger pendingTrigger;

   @Override
   public void render(GuiGraphics guiGraphics, float partialTick) {
      JsonObject config = ConfigManager.getElementConfig("kill_icon", "card");
      if (config != null && config.has("visible") && config.get("visible").getAsBoolean()) {
         this.loadConfig(config);
         Minecraft mc = Minecraft.getInstance();
         int screenWidth = mc.getWindow().getGuiScaledWidth();
         int screenHeight = mc.getWindow().getGuiScaledHeight();
         float standardX = (float)ScreenAnchor.resolveCenterX(this.configScreenAnchor, this.configXOffset, screenWidth);
         float standardY = (float)ScreenAnchor.resolveCenterY(this.configScreenAnchor, this.configYOffset, screenHeight);
         this.renderInternal(guiGraphics, partialTick, standardX, standardY);
      } else {
         this.activeCards.clear();
         this.pendingTrigger = null;
      }
   }

   public void renderAt(GuiGraphics guiGraphics, float partialTick, float standardX, float standardY) {
      JsonObject config = ConfigManager.getElementConfig("kill_icon", "card");
      if (config != null && config.has("visible") && config.get("visible").getAsBoolean()) {
         this.loadConfig(config);
         this.renderInternal(guiGraphics, partialTick, standardX, standardY);
      } else {
         this.activeCards.clear();
         this.pendingTrigger = null;
      }
   }

   public void renderPreviewAt(GuiGraphics guiGraphics, float partialTick, float standardX, float standardY, JsonObject config) {
      if (config == null) {
         this.activeCards.clear();
         this.pendingTrigger = null;
      } else {
         this.loadConfig(config);
         this.renderInternal(guiGraphics, partialTick, standardX, standardY);
      }
   }

   private void renderInternal(GuiGraphics guiGraphics, float partialTick, float standardX, float standardY) {
      long currentTime = PreviewRenderTimeContext.currentTimeMillis();
      long displayDuration = this.resolveDisplayDuration();
      long animDurMs = (long)(this.animationDuration * 1000.0F);
      Minecraft mc = Minecraft.getInstance();
      Iterator<CardRenderer.CardInstance> it = this.activeCards.iterator();

      while (it.hasNext()) {
         CardRenderer.CardInstance card = it.next();
         long age = currentTime - card.spawnTime;
         if (card.state == CardRenderer.CardState.ENTERING) {
            if (age >= animDurMs) {
               card.state = CardRenderer.CardState.DISPLAYING;
            }
         } else if (card.state == CardRenderer.CardState.EXITING) {
            long exitElapsed = currentTime - card.exitStartTime;
            if (exitElapsed > animDurMs) {
               it.remove();
               continue;
            }
         }

         if (card.isPendingRemoval) {
            long transitionElapsed = currentTime - card.lastLayoutUpdateTime;
            if (transitionElapsed >= animDurMs) {
               it.remove();
            }
         }
      }

      if (this.activeCards.isEmpty() && this.pendingTrigger != null) {
         CardRenderer.CardInstance newCard = new CardRenderer.CardInstance(this.pendingTrigger.killType, this.pendingTrigger.comboCount, currentTime);
         this.activeCards.add(newCard);
         this.pendingTrigger = null;
         this.updateLayout(currentTime);
      }

      if (!this.activeCards.isEmpty()) {
         CardRenderer.CardInstance newest = this.activeCards.get(this.activeCards.size() - 1);
         if (newest.state == CardRenderer.CardState.DISPLAYING) {
            long newestAge = currentTime - newest.spawnTime;
            if (newestAge > displayDuration) {
               long exitTime = currentTime;

               for (CardRenderer.CardInstance cardx : this.activeCards) {
                  if (cardx.state != CardRenderer.CardState.EXITING) {
                     cardx.startExit(exitTime);
                  }
               }
            }
         }
      }

      for (CardRenderer.CardInstance cardxx : this.activeCards) {
         this.renderCard(guiGraphics, cardxx, currentTime, standardX, standardY, animDurMs, mc);
      }
   }

   private void renderCard(
      GuiGraphics guiGraphics, CardRenderer.CardInstance card, long currentTime, float standardX, float standardY, long animDurMs, Minecraft mc
   ) {
      long elapsed = currentTime - card.spawnTime;
      float maxDist = 256.0F * this.configScale * 0.9F;
      float currentDist = 0.0F;
      if (card.state == CardRenderer.CardState.ENTERING) {
         float progress = (float)elapsed / (float)animDurMs;
         progress = Mth.clamp(progress, 0.0F, 1.0F);
         float moveProgress = this.calculateSegmentedEaseOut(progress);
         currentDist = moveProgress * maxDist;
      } else if (card.state == CardRenderer.CardState.EXITING) {
         long exitElapsed = currentTime - card.exitStartTime;
         float progress = (float)exitElapsed / (float)animDurMs;
         progress = Mth.clamp(progress, 0.0F, 1.0F);
         float moveProgress = this.calculateSegmentedEaseIn(progress);
         currentDist = Mth.lerp(moveProgress, maxDist, 0.0F);
      } else {
         currentDist = maxDist;
      }

      float currentAngle = card.targetAngle;
      if (currentTime - card.lastLayoutUpdateTime < animDurMs) {
         float layoutProgress = (float)(currentTime - card.lastLayoutUpdateTime) / (float)animDurMs;
         layoutProgress = Mth.clamp(layoutProgress, 0.0F, 1.0F);
         float angleEase = this.calculateSegmentedEaseOut(layoutProgress);
         currentAngle = Mth.lerp(angleEase, card.startAngle, card.targetAngle);
      }

      card.currentAngle = currentAngle;
      double rad = Math.toRadians((double)currentAngle);
      float offsetX = (float)Math.sin(rad) * currentDist;
      float offsetY = (float)(-Math.cos(rad)) * currentDist;
      float renderX = standardX + offsetX;
      float renderY = standardY + offsetY;
      float alpha = 1.0F;
      if (card.state == CardRenderer.CardState.ENTERING) {
         float fadeDur = (float)animDurMs / 3.0F;
         if ((float)elapsed < fadeDur) {
            alpha = (float)elapsed / fadeDur;
         }
      } else if (card.state == CardRenderer.CardState.EXITING) {
         long exitElapsed = currentTime - card.exitStartTime;
         float progress = (float)exitElapsed / (float)animDurMs;
         if (progress > 0.66F) {
            float fadeP = (progress - 0.66F) / 0.33F;
            alpha = 1.0F - fadeP;
         }
      }

      float lightAlpha = 0.0F;
      float lightScale = 1.0F;
      long lightTotalDur = animDurMs * 5L;
      if (elapsed < lightTotalDur) {
         float lightP = (float)elapsed / (float)lightTotalDur;
         lightAlpha = 1.0F - lightP * lightP;
         long scaleUpDur = animDurMs / 3L;
         if (elapsed < scaleUpDur) {
            float scaleP = (float)elapsed / (float)scaleUpDur;
            lightScale = Mth.lerp(scaleP, 0.5F, 1.0F);
         } else {
            float scaleP = (float)(elapsed - scaleUpDur) / (float)(lightTotalDur - scaleUpDur);
            lightScale = Mth.lerp(scaleP, 1.0F, 0.6F);
         }
      }

      String currentTeam = this.team;
      if (this.dynamicCardStyle && mc.player != null) {
         Team pt = mc.player.getTeam();
         if (pt != null) {
            ChatFormatting color = pt.getColor();
            if (color == ChatFormatting.BLUE || color == ChatFormatting.AQUA || color == ChatFormatting.DARK_AQUA || color == ChatFormatting.DARK_BLUE) {
               currentTeam = "ct";
            } else if (color == ChatFormatting.YELLOW || color == ChatFormatting.GOLD || color == ChatFormatting.RED || color == ChatFormatting.DARK_RED) {
               currentTeam = "t";
            }
         }
      }

      boolean isT = "t".equalsIgnoreCase(currentTeam);
      String cardTextureKey = this.getCardTextureKey(card.killType, isT);
      String lightTextureKey = isT ? "light_t" : "light_ct";
      float cardFocusMultiplier = PreviewTextureFocusContext.alphaMultiplier("kill_icon/card", cardTextureKey);
      float lightFocusMultiplier = PreviewTextureFocusContext.alphaMultiplier("kill_icon/card", lightTextureKey);
      String cardTextureName = ElementTextureDefinition.getSelectedTextureFileName(
         ConfigManager.getCurrentPresetId(), "kill_icon/card", cardTextureKey, this.currentConfig
      );
      String lightTextureName = ElementTextureDefinition.getSelectedTextureFileName(
         ConfigManager.getCurrentPresetId(), "kill_icon/card", lightTextureKey, this.currentConfig
      );
      ResourceLocation cardTexture = ExternalTextureManager.getTexture(cardTextureName);
      ResourceLocation lightTexture = ExternalTextureManager.getTexture(lightTextureName);
      if (cardTexture != null) {
         PoseStack poseStack = guiGraphics.pose();
         poseStack.pushPose();
         poseStack.translate(0.0F, 0.0F, (float)this.activeCards.indexOf(card));
         if (lightTexture != null && lightAlpha > 0.01F) {
            float lightWidthRatio = this.resolveFrameRatio(lightTextureKey, "texture_frame_width_ratio");
            float lightHeightRatio = this.resolveFrameRatio(lightTextureKey, "texture_frame_height_ratio");
            float lightW = 256.0F * this.configScale * lightWidthRatio;
            float lightH = 256.0F * this.configScale * lightHeightRatio;
            poseStack.pushPose();
            poseStack.translate(renderX, renderY, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(currentAngle));
            poseStack.translate(0.0F, 256.0F * this.configScale / 2.0F, 0.0F);
            poseStack.scale(lightScale, lightScale, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, lightAlpha * alpha * lightFocusMultiplier);
            guiGraphics.blit(
               lightTexture, (int)(-lightW / 2.0F), (int)(-lightH), (int)lightW, (int)lightH, 0.0F, 0.0F, (int)lightW, (int)lightH, (int)lightW, (int)lightH
            );
            poseStack.popPose();
         }

         poseStack.pushPose();
         poseStack.translate(renderX, renderY, 0.0F);
         poseStack.mulPose(Axis.ZP.rotationDegrees(currentAngle));
         poseStack.scale(this.configScale, this.configScale, 1.0F);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha * cardFocusMultiplier);
         float cardWidthRatio = this.resolveFrameRatio(cardTextureKey, "texture_frame_width_ratio");
         float cardHeightRatio = this.resolveFrameRatio(cardTextureKey, "texture_frame_height_ratio");
         int drawWidth = Math.round(256.0F * cardWidthRatio);
         int drawHeight = Math.round(256.0F * cardHeightRatio);
         if (this.enableIconGlow) {
            IconGlowRenderEffect.drawGlowFrame(
               guiGraphics,
               cardTexture,
               -drawWidth / 2,
               -drawHeight / 2,
               drawWidth,
               drawHeight,
               0,
               0,
               drawWidth,
               drawHeight,
               drawWidth,
               drawHeight,
               alpha * cardFocusMultiplier,
               this.iconGlowColor,
               this.iconGlowIntensity,
               this.iconGlowSize
            );
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha * cardFocusMultiplier);
         }

         guiGraphics.blit(cardTexture, -drawWidth / 2, -drawHeight / 2, drawWidth, drawHeight, 0.0F, 0.0F, drawWidth, drawHeight, drawWidth, drawHeight);
         float flashAlpha = 0.0F;
         long flashHold = animDurMs / 2L;
         long flashFade = animDurMs * 4L;
         if (elapsed < flashHold) {
            flashAlpha = 1.0F;
         } else {
            long flashElapsed = elapsed - flashHold;
            if (flashElapsed < flashFade) {
               flashAlpha = 1.0F - (float)flashElapsed / (float)flashFade;
            }
         }

         if (flashAlpha > 0.01F) {
            RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, flashAlpha * alpha);
            guiGraphics.blit(cardTexture, -drawWidth / 2, -drawHeight / 2, drawWidth, drawHeight, 0.0F, 0.0F, drawWidth, drawHeight, drawWidth, drawHeight);
            if (flashAlpha > 0.5F) {
               RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (flashAlpha - 0.5F) * 2.0F * alpha);
               guiGraphics.blit(
                  cardTexture, -drawWidth / 2, -drawHeight / 2, drawWidth, drawHeight, 0.0F, 0.0F, drawWidth, drawHeight, drawWidth, drawHeight
               );
            }

            RenderSystem.defaultBlendFunc();
         }

         if (card.comboCount > 0) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            String text = String.valueOf(card.comboCount);
            Font font = mc.font;
            String colorHex = isT ? this.colorTextT : this.colorTextCt;
            int color = this.parseColor(colorHex);
            int alphaInt = (int)(alpha * 255.0F);
            int finalColor = color & 16777215 | alphaInt << 24;
            poseStack.pushPose();
            poseStack.scale(this.textScale, this.textScale, 1.0F);
            int textWidth = font.width(text);
            guiGraphics.drawString(font, text, -textWidth / 2, -9 / 2, finalColor, true);
            poseStack.popPose();
         }

         poseStack.popPose();
         poseStack.popPose();
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      }
   }

   private float calculateSegmentedEaseOut(float t) {
      if (t < 0.5F) {
         return t * 1.8F;
      } else {
         float t2 = (t - 0.5F) * 2.0F;
         return 0.9F + (1.0F - (float)Math.pow((double)(1.0F - t2), 3.0)) * 0.1F;
      }
   }

   private float calculateSegmentedEaseIn(float t) {
      if (t < 0.5F) {
         float t2 = t * 2.0F;
         return t2 * t2 * 0.2F;
      } else {
         return 0.2F + (t - 0.5F) * 1.6F;
      }
   }

   private void loadConfig(JsonObject config) {
      this.currentConfig = config;
      this.configScale = config.has("scale") ? config.get("scale").getAsFloat() : 1.0F;
      this.configXOffset = config.has("x_offset") ? config.get("x_offset").getAsInt() : 0;
      this.configYOffset = config.has("y_offset") ? config.get("y_offset").getAsInt() : 0;
      this.configScreenAnchor = config.has("screen_anchor") ? config.get("screen_anchor").getAsString() : "bottom_center";
      this.team = config.has("team") ? config.get("team").getAsString() : "ct";
      this.animationDuration = config.has("animation_duration") ? config.get("animation_duration").getAsFloat() : 0.75F;
      this.colorTextCt = config.has("color_text_ct") ? config.get("color_text_ct").getAsString() : "9cc1eb";
      this.colorTextT = config.has("color_text_t") ? config.get("color_text_t").getAsString() : "d9ac5b";
      this.textScale = config.has("text_scale") ? config.get("text_scale").getAsFloat() : 1.0F;
      this.dynamicCardStyle = config.has("dynamic_card_style") && config.get("dynamic_card_style").getAsBoolean();
      this.maxStackCount = config.has("max_stack_count") ? config.get("max_stack_count").getAsInt() : 5;
      this.enableIconGlow = IconGlowRenderEffect.isEnabled(config);
      this.iconGlowColor = IconGlowRenderEffect.resolveColor(config);
      this.iconGlowIntensity = IconGlowRenderEffect.resolveIntensity(config);
      this.iconGlowSize = IconGlowRenderEffect.resolveSize(config);
   }

   private long resolveDisplayDuration() {
      long serverDuration = ComboIconRenderer.getServerComboWindowMs();
      return serverDuration > 0L ? serverDuration : 3000L;
   }

   private String getCardTextureKey(int killType, boolean isT) {
      String base = switch (killType) {
         case 1 -> "headshot";
         case 2 -> "explosion";
         case 3 -> "crit";
         default -> "default";
      };
      return isT ? base + "_t" : base + "_ct";
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

   private int parseColor(String hex) {
      try {
         return Integer.parseInt(hex.replace("#", ""), 16);
      } catch (NumberFormatException var3) {
         return 16777215;
      }
   }

   @Override
   public void trigger(IHudRenderer.TriggerContext context) {
      if (context.type() != 4 && context.type() != 5) {
         if (context.comboCount() == 1 && !this.activeCards.isEmpty()) {
            long now = PreviewRenderTimeContext.currentTimeMillis();

            for (CardRenderer.CardInstance card : this.activeCards) {
               if (card.state != CardRenderer.CardState.EXITING) {
                  card.startExit(now);
               }
            }

            this.pendingTrigger = new CardRenderer.PendingTrigger(context.type(), context.comboCount());
         } else {
            if (!this.activeCards.isEmpty()) {
               CardRenderer.CardInstance newest = this.activeCards.get(this.activeCards.size() - 1);
               if (newest.state == CardRenderer.CardState.EXITING) {
                  this.activeCards.clear();
                  this.pendingTrigger = null;
               }
            }

            long now = PreviewRenderTimeContext.currentTimeMillis();
            CardRenderer.CardInstance newCard = new CardRenderer.CardInstance(context.type(), context.comboCount(), now);
            this.activeCards.add(newCard);
            this.updateLayout(now);
         }
      }
   }

   private void updateLayout(long now) {
      int count = this.activeCards.size();
      if (count != 0) {
         CardRenderer.CardInstance newest = this.activeCards.get(count - 1);
         boolean isStackingMode = newest.comboCount >= this.maxStackCount;
         if (isStackingMode) {
            for (int i = 0; i < count; i++) {
               CardRenderer.CardInstance card = this.activeCards.get(i);
               if (card.targetAngle != 0.0F) {
                  card.startAngle = card.currentAngle;
                  card.targetAngle = 0.0F;
                  card.lastLayoutUpdateTime = now;
               }

               if (i < count - 1 && !card.isPendingRemoval) {
                  card.isPendingRemoval = true;
                  card.lastLayoutUpdateTime = now;
               }
            }
         } else {
            float centerIndex = (float)(count - 1) / 2.0F;

            for (int i = 0; i < count; i++) {
               CardRenderer.CardInstance cardx = this.activeCards.get(i);
               float newTarget = ((float)i - centerIndex) * 10.0F;
               if (Math.abs(cardx.targetAngle - newTarget) > 0.01F || i == count - 1 && cardx.state == CardRenderer.CardState.ENTERING) {
                  if (i == count - 1) {
                     cardx.targetAngle = newTarget;
                     cardx.currentAngle = newTarget;
                     cardx.startAngle = newTarget;
                  } else {
                     cardx.startAngle = cardx.currentAngle;
                     cardx.targetAngle = newTarget;
                     cardx.lastLayoutUpdateTime = now;
                  }
               }
            }
         }
      }
   }

   public static void updateServerComboWindowSeconds(double seconds) {
      ComboIconRenderer.updateServerComboWindowSeconds(seconds);
   }

   private static class CardInstance {
      final int killType;
      final int comboCount;
      final long spawnTime;
      CardRenderer.CardState state = CardRenderer.CardState.ENTERING;
      long exitStartTime = -1L;
      float currentAngle = 0.0F;
      float startAngle = 0.0F;
      float targetAngle = 0.0F;
      long lastLayoutUpdateTime = 0L;
      boolean isPendingRemoval = false;

      CardInstance(int killType, int comboCount, long spawnTime) {
         this.killType = killType;
         this.comboCount = comboCount;
         this.spawnTime = spawnTime;
      }

      void startExit(long currentTime) {
         if (this.state != CardRenderer.CardState.EXITING) {
            this.state = CardRenderer.CardState.EXITING;
            this.exitStartTime = currentTime;
         }
      }
   }

   private static enum CardState {
      ENTERING,
      DISPLAYING,
      EXITING;
   }

   private static class PendingTrigger {
      final int killType;
      final int comboCount;

      PendingTrigger(int killType, int comboCount) {
         this.killType = killType;
         this.comboCount = comboCount;
      }
   }
}
