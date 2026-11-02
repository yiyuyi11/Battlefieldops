package cn.bfops.killicon.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import cn.bfops.killicon.client.stats.ClientStatsManager;
import cn.bfops.killicon.network.IPacket;
import cn.bfops.killicon.network.PacketContext;

public class KillDistancePacket implements IPacket {
   private final double distance;

   public KillDistancePacket(double distance) {
      this.distance = distance;
   }

   public KillDistancePacket(FriendlyByteBuf buffer) {
      this.distance = buffer.readDouble();
   }

   @Override
   public void encode(FriendlyByteBuf buffer) {
      buffer.writeDouble(this.distance);
   }

   @Override
   public void handle(PacketContext context) {
      context.enqueueWork(() -> ClientStatsManager.recordKillDistance(this.distance));
      context.setPacketHandled(true);
   }
}
