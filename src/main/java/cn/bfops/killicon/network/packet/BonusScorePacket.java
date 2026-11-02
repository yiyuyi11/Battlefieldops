package cn.bfops.killicon.network.packet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.FriendlyByteBuf;
import cn.bfops.killicon.client.config.ClientConfigManager;
import cn.bfops.killicon.client.render.HudElementManager;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.render.impl.BonusListRenderer;
import cn.bfops.killicon.client.render.impl.ScoreSubtitleRenderer;
import cn.bfops.killicon.client.sounds.SoundTriggerManager;
import cn.bfops.killicon.client.stats.ClientStatsManager;
import cn.bfops.killicon.client.util.ClientMessageLogger;
import cn.bfops.killicon.common.bonus.BonusDefinition;
import cn.bfops.killicon.common.bonus.BonusRegistry;
import cn.bfops.killicon.network.IPacket;
import cn.bfops.killicon.network.PacketContext;

public class BonusScorePacket implements IPacket {
   private final int bonusType;
   private final float score;
   private final String extraData;
   private final int victimId;
   private final String victimName;
   private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("<(\\w+)>");

   public BonusScorePacket(int bonusType, float score) {
      this(bonusType, score, "", -1, null);
   }

   public BonusScorePacket(int bonusType, float score, String extraData) {
      this(bonusType, score, extraData, -1, null);
   }

   public BonusScorePacket(int bonusType, float score, String extraData, int victimId) {
      this(bonusType, score, extraData, victimId, null);
   }

   public BonusScorePacket(int bonusType, float score, String extraData, int victimId, String victimName) {
      this.bonusType = bonusType;
      this.score = score;
      this.extraData = extraData != null ? extraData : "";
      this.victimId = victimId;
      this.victimName = victimName;
   }

   public BonusScorePacket(FriendlyByteBuf buffer) {
      this.bonusType = buffer.readInt();
      this.score = buffer.readFloat();
      this.extraData = buffer.readUtf(32767);
      this.victimId = buffer.readInt();
      if (buffer.readBoolean()) {
         this.victimName = buffer.readUtf(32767);
      } else {
         this.victimName = null;
      }
   }

   @Override
   public void encode(FriendlyByteBuf buffer) {
      buffer.writeInt(this.bonusType);
      buffer.writeFloat(this.score);
      buffer.writeUtf(this.extraData != null ? this.extraData : "", 32767);
      buffer.writeInt(this.victimId);
      if (this.victimName != null) {
         buffer.writeBoolean(true);
         buffer.writeUtf(this.victimName, 32767);
      } else {
         buffer.writeBoolean(false);
      }
   }

   public int getBonusType() {
      return this.bonusType;
   }

   public float getScore() {
      return this.score;
   }

   public String getExtraData() {
      return this.extraData;
   }

   @Override
   public void handle(PacketContext context) {
      context.enqueueWork(() -> {
         if (ClientConfigManager.isShowBonusMessage()) {
            this.sendBonusChatMessage();
         }

         ScoreSubtitleRenderer.getInstance().addScore(this.score);
         this.triggerRushBombKillFeed();
         StringBuilder dataBuilder = new StringBuilder();
         dataBuilder.append(this.score);
         if (this.extraData != null && !this.extraData.isEmpty() || this.victimName != null && !this.victimName.isEmpty()) {
            dataBuilder.append("|").append(this.extraData != null ? this.extraData : "");
            if (this.victimName != null && !this.victimName.isEmpty()) {
               dataBuilder.append("|").append(this.victimName);
            }
         }

         String data = dataBuilder.toString();
         HudElementManager.trigger("subtitle", "bonus_list", IHudRenderer.TriggerContext.of(this.bonusType, this.victimId, 0, data));
         this.triggerScrollingBonusIcons();
         this.recordStatistics();
      });
      context.setPacketHandled(true);
   }

   private void triggerRushBombKillFeed() {
      BonusDefinition def = BonusRegistry.get(this.bonusType);
      if (def != null) {
         String payloadType = def.killFeedPayload();
         if (payloadType != null) {
            SoundTriggerManager.tryPlaySound("kill_icon", "scrolling", 6, 0, false, false);
            HudElementManager.trigger("kill_icon", "scrolling", IHudRenderer.TriggerContext.of(6, this.victimId));
            HudElementManager.trigger(
               "subtitle", "kill_feed", IHudRenderer.TriggerContext.of(6, this.victimId, 0, payloadType + "|" + this.formatScoreValue(this.score))
            );
         }
      }
   }

   private String formatScoreValue(float value) {
      return Math.abs(value) < 1.0F && Math.abs(value) > 0.001F ? String.format("%.1f", value) : String.valueOf(Math.round(value));
   }

   private void triggerScrollingBonusIcons() {
      BonusDefinition def = BonusRegistry.get(this.bonusType);
      if (def != null) {
         int iconKillType = def.iconKillType();
         if (iconKillType != -1) {
            SoundTriggerManager.tryPlaySound("kill_icon", "scrolling", iconKillType, 0, false, false);
            HudElementManager.trigger("kill_icon", "scrolling", IHudRenderer.TriggerContext.of(iconKillType, this.victimId, 0, this.victimName, 0.0F));
         }
      }
   }

   private void recordStatistics() {
      BonusDefinition def = BonusRegistry.get(this.bonusType);
      if (def != null) {
         switch (def.statCategory()) {
            case ASSIST:
               ClientStatsManager.recordAssist();
               break;
            case REVIVE:
               ClientStatsManager.recordRevive();
               break;
            case DAMAGE:
               ClientStatsManager.recordDamage((double)this.score);
         }
      }
   }

   private void sendBonusChatMessage() {
      String format = BonusListRenderer.getEffectiveFormat(this.bonusType, this.extraData);
      Matcher matcher = PLACEHOLDER_PATTERN.matcher(format);
      StringBuilder sb = new StringBuilder();

      while (matcher.find()) {
         String type = matcher.group(1);

         matcher.appendReplacement(sb, Matcher.quoteReplacement(switch (type) {
            case "score" -> {
               if (this.score < 1.0F && this.score > 0.0F) {
                  yield String.format("%.1f", this.score);
               } else {
                  yield String.valueOf(Math.round(this.score));
               }
            }
            case "combo" -> {
               int val = 0;

               try {
                  val = Integer.parseInt(this.extraData);
               } catch (Exception var11) {
               }

               yield String.valueOf(val);
            }
            case "multi_kill" -> {
               int val = 0;

               try {
                  val = Integer.parseInt(this.extraData);
               } catch (Exception var10) {
               }

               yield BonusListRenderer.getLocalizedNumber(val);
            }
            case "distance" -> this.extraData + "m";
            case "streak" -> this.extraData;
            case "extra" -> this.extraData;
            default -> "";
         }));
      }

      matcher.appendTail(sb);
      ClientMessageLogger.chatLiteral(sb.toString());
   }
}
