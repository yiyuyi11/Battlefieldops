package cn.bfops.killicon.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import cn.bfops.killicon.client.render.HudElementManager;
import cn.bfops.killicon.client.render.IHudRenderer;
import cn.bfops.killicon.client.stats.ClientStatsManager;
import cn.bfops.killicon.network.IPacket;
import cn.bfops.killicon.network.PacketContext;

public class HonorPacket implements IPacket {
   private final String honorId;
   private final String extraData;

   public HonorPacket(String honorId) {
      this(honorId, "");
   }

   public HonorPacket(String honorId, String extraData) {
      this.honorId = honorId != null ? honorId : "";
      this.extraData = extraData != null ? extraData : "";
   }

   public HonorPacket(FriendlyByteBuf buffer) {
      this.honorId = buffer.readUtf();
      this.extraData = buffer.readUtf();
   }

   @Override
   public void encode(FriendlyByteBuf buffer) {
      buffer.writeUtf(this.honorId);
      buffer.writeUtf(this.extraData);
   }

   @Override
   public void handle(PacketContext context) {
      context.enqueueWork(
         () -> {
            ClientStatsManager.recordHonor(this.honorId);
            HudElementManager.trigger(
               "kill_icon", "honor", IHudRenderer.TriggerContext.of(0, this.honorId + (this.extraData.isEmpty() ? "" : ":" + this.extraData))
            );
         }
      );
      context.setPacketHandled(true);
   }
}
