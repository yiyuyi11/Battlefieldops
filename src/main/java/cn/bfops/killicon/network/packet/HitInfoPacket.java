package cn.bfops.killicon.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import cn.bfops.killicon.client.render.impl.HitInfoRenderer;
import cn.bfops.killicon.network.IPacket;
import cn.bfops.killicon.network.PacketContext;

public class HitInfoPacket implements IPacket {
   private final float amount;
   private final boolean killed;
   private final int entityId;

   public HitInfoPacket(float amount, boolean killed) {
      this(amount, killed, -1);
   }

   public HitInfoPacket(float amount, boolean killed, int entityId) {
      this.amount = amount;
      this.killed = killed;
      this.entityId = entityId;
   }

   public HitInfoPacket(FriendlyByteBuf buffer) {
      this.amount = buffer.readFloat();
      this.killed = buffer.readBoolean();
      this.entityId = buffer.readInt();
   }

   @Override
   public void encode(FriendlyByteBuf buffer) {
      buffer.writeFloat(this.amount);
      buffer.writeBoolean(this.killed);
      buffer.writeInt(this.entityId);
   }

   public float getAmount() {
      return this.amount;
   }

   public boolean isKilled() {
      return this.killed;
   }

   public int getEntityId() {
      return this.entityId;
   }

   @Override
   public void handle(PacketContext context) {
      context.enqueueWork(() -> {
         if (this.killed) {
            HitInfoRenderer.getInstance().markKill(this.entityId);
         } else if (this.amount > 0.0F) {
            HitInfoRenderer.getInstance().addDamage(this.entityId, this.amount);
         }
      });
      context.setPacketHandled(true);
   }
}
