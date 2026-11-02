package cn.bfops.killicon.network;

import net.minecraft.network.FriendlyByteBuf;

public interface IPacket {
   void encode(FriendlyByteBuf var1);

   void handle(PacketContext var1);
}
