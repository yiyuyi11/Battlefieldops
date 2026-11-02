package cn.bfops.killicon.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import cn.bfops.killicon.client.sounds.SoundTriggerManager;
import cn.bfops.killicon.network.IPacket;
import cn.bfops.killicon.network.PacketContext;

public class DamageSoundPacket implements IPacket {
   private final boolean headshotDamage;

   public DamageSoundPacket() {
      this(false);
   }

   public DamageSoundPacket(boolean headshotDamage) {
      this.headshotDamage = headshotDamage;
   }

   public DamageSoundPacket(FriendlyByteBuf buffer) {
      this.headshotDamage = buffer.readBoolean();
   }

   @Override
   public void encode(FriendlyByteBuf buffer) {
      buffer.writeBoolean(this.headshotDamage);
   }

   @Override
   public void handle(PacketContext context) {
      context.enqueueWork(() -> {
         if (this.headshotDamage) {
            SoundTriggerManager.playHeadshotDamageSound();
         } else {
            SoundTriggerManager.playHitSound();
         }
      });
      context.setPacketHandled(true);
   }
}
