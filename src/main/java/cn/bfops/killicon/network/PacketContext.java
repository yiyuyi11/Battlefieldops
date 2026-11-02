package cn.bfops.killicon.network;

import net.minecraft.server.level.ServerPlayer;

public interface PacketContext {
   void enqueueWork(Runnable var1);

   void setPacketHandled(boolean var1);

   ServerPlayer getSender();
}
